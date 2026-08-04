package com.example.proyectofinalrestaurante.data.sync;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.dao.MesaDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.MesaMapper;
import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.remote.dto.MesaDto;
import com.example.proyectofinalrestaurante.data.repository.MesaRemoto;
import com.example.proyectofinalrestaurante.data.repository.ResultadoRed;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;

import java.util.List;

/**
 * El sincronizador del módulo Mesas (Fase 2c).
 *
 * <p>Dos trabajos, en orden: primero <b>drena el outbox</b> (sube las escrituras locales en
 * orden FIFO) y después <b>baja el delta</b> con la marca de agua. Si el drenado se corta
 * por un error transitorio, no se baja el delta: el reintento lo va a repetir todo.</p>
 */
public final class SincronizadorMesa implements Sincronizador {

    static final int MAX_INTENTOS = 3;
    static final int LOTE = 50;
    static final String TABLA_MESAS = "mesas";

    static final String CAMBIO_LOCAL_PERDIDO =
            "Un cambio local se perdió: el servidor tenía una versión más reciente.";

    private final MesaRemoto remoto;
    private final Outbox outbox;
    private final MesaDao mesaDao;
    private final SincronizacionDao sincronizacionDao;

    public SincronizadorMesa(MesaRemoto remoto, Outbox outbox,
                             MesaDao mesaDao, SincronizacionDao sincronizacionDao) {
        this.remoto = remoto;
        this.outbox = outbox;
        this.mesaDao = mesaDao;
        this.sincronizacionDao = sincronizacionDao;
    }

    @Override
    public String modulo() {
        return TipoOperacion.Modulo.MESAS;
    }

    @Override
    public ResultadoSync sincronizar() {
        ResultadoSync drenado = drenarOutbox();
        if (drenado.esTransitorio()) {
            return drenado;
        }
        return bajarDelta(drenado.getMensaje());
    }

    // ------------------------------------------------------------------ drenado

    private ResultadoSync drenarOutbox() {
        String ultimoErrorPermanente = null;
        while (true) {
            List<OperacionPendienteEntity> lote = outbox.primeras(LOTE);
            if (lote.isEmpty()) {
                break;
            }
            for (OperacionPendienteEntity operacion : lote) {
                ResultadoSync resultado = procesar(operacion);
                if (resultado.esTransitorio()) {
                    return resultado;
                }
                if (resultado.esPermanente() && resultado.getMensaje() != null) {
                    ultimoErrorPermanente = resultado.getMensaje();
                }
            }
            if (lote.size() < LOTE) {
                break;
            }
        }
        return ultimoErrorPermanente == null
                ? ResultadoSync.ok()
                : ResultadoSync.permanente(ultimoErrorPermanente);
    }

    private ResultadoSync procesar(OperacionPendienteEntity operacion) {
        switch (operacion.getTipo()) {
            case TipoOperacion.CREAR_MESA:
                return crearMesa(operacion);
            case TipoOperacion.ACTUALIZAR_MESA:
                return actualizarMesa(operacion);
            case TipoOperacion.CAMBIAR_ESTADO_MESA:
                return cambiarEstadoMesa(operacion);
            case TipoOperacion.CAMBIAR_BAJA_MESA:
                return cambiarBajaMesa(operacion);
            default:
                outbox.descartar(operacion.getId());
                return ResultadoSync.permanente("Operación desconocida en la cola.");
        }
    }

    // ------------------------------------------------------------------ operaciones

    private ResultadoSync crearMesa(OperacionPendienteEntity operacion) {
        MesaEntity fila = mesaDao.porIdLocal(operacion.getIdLocal());
        if (fila == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<MesaDto> resultado = remoto.crearMesa(
                fila.getNumeroMesa(), fila.getCapacidad(), fila.getUbicacion());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        MesaDto creado = resultado.getValor();
        fila.setIdServidor(creado.getIdMesa());
        fila.setActualizadoEn(creado.getActualizadoEn());
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        mesaDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync actualizarMesa(OperacionPendienteEntity operacion) {
        MesaEntity fila = mesaDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado = remoto.actualizarMesa(
                fila.getIdServidor(), fila.getNumeroMesa(), fila.getCapacidad(),
                fila.getUbicacion());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        mesaDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync cambiarEstadoMesa(OperacionPendienteEntity operacion) {
        MesaEntity fila = mesaDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        EstadoMesa estado = EstadoMesa.porId(
                Integer.parseInt(operacion.getPayloadJson()));
        if (estado == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado = remoto.cambiarEstadoMesa(
                fila.getIdServidor(), estado.getIdServidor());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        fila.setEstadoMesa(estado.name());
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        mesaDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync cambiarBajaMesa(OperacionPendienteEntity operacion) {
        MesaEntity fila = mesaDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        boolean activo = "1".equals(operacion.getPayloadJson());
        ResultadoRed<Void> resultado = remoto.cambiarBajaMesa(
                fila.getIdServidor(), activo);
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        fila.setActivo(activo);
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        mesaDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    // ------------------------------------------------------------------ fallos

    private ResultadoSync manejarFallo(OperacionPendienteEntity operacion,
                                       ResultadoRed<?> resultado,
                                       @Nullable MesaEntity mesa) {
        String mensaje = resultado.getMensaje() == null
                ? "No se pudo sincronizar un cambio." : resultado.getMensaje();
        if (ClasificadorDeError.esTransitorio(resultado.getCodigoHttp())) {
            boolean quedanIntentos =
                    outbox.registrarErrorTransitorio(operacion.getId(), mensaje, MAX_INTENTOS);
            if (quedanIntentos) {
                return ResultadoSync.transitorio(mensaje);
            }
        }
        outbox.descartar(operacion.getId());
        if (mesa != null) {
            mesa.setEstadoSync(EstadoSync.ERROR.name());
            mesaDao.actualizar(mesa);
        }
        return ResultadoSync.permanente(mensaje);
    }

    // ------------------------------------------------------------------ delta

    private ResultadoSync bajarDelta(@Nullable String errorPermanenteDelDrenado) {
        String marca = leerMarca(TABLA_MESAS);
        boolean huboConflictoLocal = false;
        while (true) {
            ResultadoRed<List<MesaDto>> resultado = remoto.listarMesasDesde(marca);
            if (!resultado.isExitoso()) {
                return convertirFalloDelta(resultado);
            }
            List<MesaDto> pagina = resultado.getValor();
            for (MesaDto dto : pagina) {
                huboConflictoLocal |= aplicarMesa(dto);
                if (dto.getActualizadoEn() != null) {
                    marca = mayor(marca, dto.getActualizadoEn());
                }
            }
            guardarMarca(TABLA_MESAS, marca);
            if (pagina.size() < MesaRemoto.LIMITE_DELTA) {
                break;
            }
        }
        if (errorPermanenteDelDrenado != null) {
            return ResultadoSync.permanente(errorPermanenteDelDrenado);
        }
        return huboConflictoLocal
                ? ResultadoSync.permanente(CAMBIO_LOCAL_PERDIDO)
                : ResultadoSync.ok();
    }

    private boolean aplicarMesa(MesaDto dto) {
        MesaEntity existente = mesaDao.porIdServidor(dto.getIdMesa());
        if (existente == null) {
            mesaDao.insertar(MesaMapper.desdeServidor(dto));
            return false;
        }
        if (ganaElServidor(existente.getEstadoSync(), existente.getActualizadoEn(),
                dto.getActualizadoEn())) {
            descartarOperacionesDe(existente.getIdLocal());
            MesaEntity entidad = MesaMapper.desdeServidor(dto);
            entidad.setIdLocal(existente.getIdLocal());
            mesaDao.actualizar(entidad);
            return true;
        }
        return false;
    }

    private boolean ganaElServidor(@Nullable String estadoLocal,
                                   @Nullable String marcaLocal,
                                   @Nullable String marcaServidor) {
        if (EstadoSync.SINCRONIZADO.name().equals(estadoLocal)) {
            return true;
        }
        if (marcaServidor == null) {
            return false;
        }
        if (marcaLocal == null) {
            return true;
        }
        return marcaServidor.compareTo(marcaLocal) > 0;
    }

    private void descartarOperacionesDe(long idLocal) {
        for (OperacionPendienteEntity pendiente : outbox.deFila(idLocal)) {
            outbox.descartar(pendiente.getId());
        }
    }

    private ResultadoSync convertirFalloDelta(ResultadoRed<?> resultado) {
        String mensaje = resultado.getMensaje() == null
                ? "No se pudieron sincronizar las mesas." : resultado.getMensaje();
        if (ClasificadorDeError.esTransitorio(resultado.getCodigoHttp())) {
            return ResultadoSync.transitorio(mensaje);
        }
        return ResultadoSync.permanente(mensaje);
    }

    // ------------------------------------------------------------------ marca de agua

    @Nullable
    private String leerMarca(String tabla) {
        SincronizacionEntity marca = sincronizacionDao.porTabla(tabla);
        return marca == null ? null : marca.getMarcaAgua();
    }

    private void guardarMarca(String tabla, @Nullable String marca) {
        SincronizacionEntity entidad = new SincronizacionEntity();
        entidad.setTabla(tabla);
        entidad.setMarcaAgua(marca);
        entidad.setUltimoIntento(System.currentTimeMillis());
        entidad.setUltimoError(null);
        sincronizacionDao.guardar(entidad);
    }

    private static String mayor(@Nullable String a, @Nullable String b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }
}

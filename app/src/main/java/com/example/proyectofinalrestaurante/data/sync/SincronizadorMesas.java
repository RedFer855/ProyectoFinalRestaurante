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
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;

import java.util.List;

/**
 * El sincronizador del módulo Mesas (Plan Fase 2c, E4). Mismo contrato que
 * {@link SincronizadorMenu}: primero drena el outbox (FIFO) y después baja el delta con la
 * marca de agua. Es más parecido al del Menú que al de Empleados porque Mesas <b>sí</b> tiene
 * alta offline: el {@code id_local} no es el {@code id_servidor} hasta que el {@code CREAR}
 * sube.
 */
public final class SincronizadorMesas implements Sincronizador {

    static final int MAX_INTENTOS = 3;
    static final int LOTE = 50;
    static final String TABLA = "mesas";

    static final String CAMBIO_LOCAL_PERDIDO =
            "Un cambio de mesa se perdió: el servidor tenía una versión más reciente.";

    private final MesaRemoto remoto;
    private final Outbox outbox;
    private final MesaDao mesaDao;
    private final SincronizacionDao sincronizacionDao;

    public SincronizadorMesas(MesaRemoto remoto, Outbox outbox, MesaDao mesaDao,
                              SincronizacionDao sincronizacionDao) {
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
                // Un tipo que esta versión no conoce no puede bloquear la cola; el outbox
                // está particionado por módulo, así que acá solo llegan operaciones de MESAS.
                outbox.descartar(operacion.getId());
                return ResultadoSync.permanente("Operación desconocida en la cola.");
        }
    }

    private ResultadoSync crearMesa(OperacionPendienteEntity operacion) {
        MesaEntity fila = mesaDao.porIdLocal(operacion.getIdLocal());
        if (fila == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<MesaDto> resultado =
                remoto.crear(fila.getNumeroMesa(), fila.getCapacidad(), fila.getUbicacion());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }

        MesaDto creada = resultado.getValor();
        fila.setIdServidor(creada.getIdMesa());
        fila.setActualizadoEn(creada.getActualizadoEn());
        fila.setIdEstado(creada.getIdEstado());

        // El INSERT siempre crea la mesa en Libre (id_estado_mesa = 1): CrearMesaDto no
        // lleva estado (Plan Fase 2c, §2.2). Si el usuario ya la había cambiado de estado
        // sin red -antes de que este CREAR subiera-, ese cambio se aplica ahora con un
        // segundo viaje en vez de perderse en silencio.
        if (fila.getIdEstadoMesa() != 0 && fila.getIdEstadoMesa() != creada.getIdEstadoMesa()) {
            remoto.cambiarEstado(creada.getIdMesa(), fila.getIdEstadoMesa());
            // Si este segundo viaje falla, la mesa ya quedó creada (Libre) y el próximo
            // cambio de estado que el mesero intente la corrige; no se descarta la fila.
        } else {
            fila.setIdEstadoMesa(creada.getIdEstadoMesa());
            fila.setEstadoMesa(creada.getEstadoMesa());
        }
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        mesaDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync actualizarMesa(OperacionPendienteEntity operacion) {
        MesaEntity fila = mesaDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            // Sin fila, o sin id_servidor: la edición ya viajó en el CREAR pendiente, que lee
            // la fila fresca al drenar (mismo pliegue que el Menú, §5.5).
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado = remoto.actualizarDatos(
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
        ResultadoRed<Void> resultado =
                remoto.cambiarEstado(fila.getIdServidor(), fila.getIdEstadoMesa());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
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
        ResultadoRed<Void> resultado =
                remoto.cambiarBaja(fila.getIdServidor(), fila.isActivo());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        mesaDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync manejarFallo(OperacionPendienteEntity operacion,
                                       ResultadoRed<?> resultado, MesaEntity fila) {
        String mensaje = resultado.getMensaje() == null
                ? "No se pudo sincronizar un cambio de mesa." : resultado.getMensaje();
        if (ClasificadorDeError.esTransitorio(resultado.getCodigoHttp())) {
            boolean quedanIntentos =
                    outbox.registrarErrorTransitorio(operacion.getId(), mensaje, MAX_INTENTOS);
            if (quedanIntentos) {
                return ResultadoSync.transitorio(mensaje);
            }
        }
        outbox.descartar(operacion.getId());
        fila.setEstadoSync(EstadoSync.ERROR.name());
        mesaDao.actualizar(fila);
        return ResultadoSync.permanente(mensaje);
    }

    // ------------------------------------------------------------------ delta

    private ResultadoSync bajarDelta(@Nullable String errorPermanenteDelDrenado) {
        String marca = leerMarca();
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
            guardarMarca(marca);
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

    /** @return {@code true} si el servidor pisó un cambio local que no se había subido. */
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
            return !EstadoSync.SINCRONIZADO.name().equals(existente.getEstadoSync());
        }
        return false;
    }

    /**
     * Last-write-wins (mismo criterio que el Menú y Empleados): el servidor gana si la fila
     * local está sincronizada, o si está pendiente/error con una marca más vieja que la del
     * servidor.
     */
    private boolean ganaElServidor(@Nullable String estadoLocal, @Nullable String marcaLocal,
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
                ? "No se pudo sincronizar el salón." : resultado.getMensaje();
        if (ClasificadorDeError.esTransitorio(resultado.getCodigoHttp())) {
            return ResultadoSync.transitorio(mensaje);
        }
        return ResultadoSync.permanente(mensaje);
    }

    // ------------------------------------------------------------------ marca de agua

    @Nullable
    private String leerMarca() {
        SincronizacionEntity marca = sincronizacionDao.porTabla(TABLA);
        return marca == null ? null : marca.getMarcaAgua();
    }

    private void guardarMarca(@Nullable String marca) {
        SincronizacionEntity entidad = new SincronizacionEntity();
        entidad.setTabla(TABLA);
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

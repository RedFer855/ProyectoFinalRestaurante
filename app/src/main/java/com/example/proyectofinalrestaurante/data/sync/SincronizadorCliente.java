package com.example.proyectofinalrestaurante.data.sync;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.dao.ClienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.ClienteMapper;
import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.remote.dto.ClienteDto;
import com.example.proyectofinalrestaurante.data.repository.ClienteRemoto;
import com.example.proyectofinalrestaurante.data.repository.ResultadoRed;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;

import java.util.List;

/**
 * El sincronizador del módulo Clientes (Fase 2d).
 *
 * <p>Dos trabajos, en orden: primero <b>drena el outbox</b> (sube las escrituras locales en
 * orden FIFO) y después <b>baja el delta</b> con la marca de agua. Si el drenado se corta
 * por un error transitorio, no se baja el delta: el reintento lo va a repetir todo.</p>
 */
public final class SincronizadorCliente implements Sincronizador {

    static final int MAX_INTENTOS = 3;
    static final int LOTE = 50;
    static final String TABLA_CLIENTES = "clientes";

    static final String CAMBIO_LOCAL_PERDIDO =
            "Un cambio local se perdió: el servidor tenía una versión más reciente.";

    private final ClienteRemoto remoto;
    private final Outbox outbox;
    private final ClienteDao clienteDao;
    private final SincronizacionDao sincronizacionDao;

    public SincronizadorCliente(ClienteRemoto remoto, Outbox outbox,
                                ClienteDao clienteDao, SincronizacionDao sincronizacionDao) {
        this.remoto = remoto;
        this.outbox = outbox;
        this.clienteDao = clienteDao;
        this.sincronizacionDao = sincronizacionDao;
    }

    @Override
    public String modulo() {
        return TipoOperacion.Modulo.CLIENTES;
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
            case TipoOperacion.CREAR_CLIENTE:
                return crearCliente(operacion);
            case TipoOperacion.ACTUALIZAR_CLIENTE:
                return actualizarCliente(operacion);
            case TipoOperacion.CAMBIAR_ESTADO_CLIENTE:
                return cambiarEstadoCliente(operacion);
            case TipoOperacion.BORRAR_CLIENTE:
                return borrarCliente(operacion);
            default:
                outbox.descartar(operacion.getId());
                return ResultadoSync.permanente("Operación desconocida en la cola.");
        }
    }

    // ------------------------------------------------------------------ operaciones

    private ResultadoSync crearCliente(OperacionPendienteEntity operacion) {
        ClienteEntity fila = clienteDao.porIdLocal(operacion.getIdLocal());
        if (fila == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<ClienteDto> resultado = remoto.crearCliente(
                fila.getNombre(), fila.getApellido(),
                fila.getIdentidad(), fila.getTelefono());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        ClienteDto creado = resultado.getValor();
        fila.setIdServidor(creado.getIdCliente());
        fila.setActualizadoEn(creado.getActualizadoEn());
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        clienteDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync actualizarCliente(OperacionPendienteEntity operacion) {
        ClienteEntity fila = clienteDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado = remoto.actualizarCliente(
                fila.getIdServidor(), fila.getNombre(), fila.getApellido(),
                fila.getIdentidad(), fila.getTelefono());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        clienteDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync cambiarEstadoCliente(OperacionPendienteEntity operacion) {
        ClienteEntity fila = clienteDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        boolean activo = "1".equals(operacion.getPayloadJson());
        ResultadoRed<Void> resultado = remoto.cambiarEstadoCliente(
                fila.getIdServidor(), activo);
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        fila.setActivo(activo);
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        clienteDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync borrarCliente(OperacionPendienteEntity operacion) {
        ClienteEntity fila = clienteDao.porIdLocal(operacion.getIdLocal());
        if (fila == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        if (fila.getIdServidor() == null) {
            clienteDao.borrar(fila);
            outbox.marcarExito(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado = remoto.borrarCliente(fila.getIdServidor());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        clienteDao.borrar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    // ------------------------------------------------------------------ fallos

    private ResultadoSync manejarFallo(OperacionPendienteEntity operacion,
                                       ResultadoRed<?> resultado,
                                       @Nullable ClienteEntity cliente) {
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
        if (cliente != null) {
            cliente.setEstadoSync(EstadoSync.ERROR.name());
            clienteDao.actualizar(cliente);
        }
        return ResultadoSync.permanente(mensaje);
    }

    // ------------------------------------------------------------------ delta

    private ResultadoSync bajarDelta(@Nullable String errorPermanenteDelDrenado) {
        String marca = leerMarca(TABLA_CLIENTES);
        boolean huboConflictoLocal = false;
        while (true) {
            ResultadoRed<List<ClienteDto>> resultado = remoto.listarClientesDesde(marca);
            if (!resultado.isExitoso()) {
                return convertirFalloDelta(resultado);
            }
            List<ClienteDto> pagina = resultado.getValor();
            for (ClienteDto dto : pagina) {
                huboConflictoLocal |= aplicarCliente(dto);
                if (dto.getActualizadoEn() != null) {
                    marca = mayor(marca, dto.getActualizadoEn());
                }
            }
            guardarMarca(TABLA_CLIENTES, marca);
            if (pagina.size() < ClienteRemoto.LIMITE_DELTA) {
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

    private boolean aplicarCliente(ClienteDto dto) {
        ClienteEntity existente = clienteDao.porIdServidor(dto.getIdCliente());
        if (existente == null) {
            clienteDao.insertar(ClienteMapper.desdeServidor(dto));
            return false;
        }
        if (ganaElServidor(existente.getEstadoSync(), existente.getActualizadoEn(),
                dto.getActualizadoEn())) {
            descartarOperacionesDe(existente.getIdLocal());
            ClienteEntity entidad = ClienteMapper.desdeServidor(dto);
            entidad.setIdLocal(existente.getIdLocal());
            clienteDao.actualizar(entidad);
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
                ? "No se pudieron sincronizar los clientes." : resultado.getMensaje();
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

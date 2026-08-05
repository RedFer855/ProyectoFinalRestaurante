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
 * El sincronizador del módulo Clientes (Plan Fase 2d, E4). Mismo contrato que
 * {@link SincronizadorMesas}: drena el outbox (FIFO) y después baja el delta. El borrado real
 * ({@code BORRAR_CLIENTE}) sigue el patrón de {@code SincronizadorMenu.borrarCategoria}: la
 * fila local ya se borró al encolar, así que el {@code id_servidor} viaja en el payload.
 *
 * <p>El delta pagina por {@code offset} con la marca fija durante toda la pasada (P-029,
 * 2026-08-04) — mismo bucle que {@link SincronizadorMenu#bajarPlatillos()}.</p>
 */
public final class SincronizadorClientes implements Sincronizador {

    static final int MAX_INTENTOS = 3;
    static final int LOTE = 50;
    static final String TABLA = "clientes";

    /** Tope de páginas por pasada del delta; ver el Javadoc de {@link SincronizadorMenu}. */
    static final int MAX_PAGINAS = 200;

    static final String CAMBIO_LOCAL_PERDIDO =
            "Un cambio de cliente se perdió: el servidor tenía una versión más reciente.";

    private final ClienteRemoto remoto;
    private final Outbox outbox;
    private final ClienteDao clienteDao;
    private final SincronizacionDao sincronizacionDao;
    private final EjecutorDeTransaccion transacciones;

    public SincronizadorClientes(ClienteRemoto remoto, Outbox outbox, ClienteDao clienteDao,
                                 SincronizacionDao sincronizacionDao,
                                 EjecutorDeTransaccion transacciones) {
        this.remoto = remoto;
        this.outbox = outbox;
        this.clienteDao = clienteDao;
        this.sincronizacionDao = sincronizacionDao;
        this.transacciones = transacciones;
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
                return cambiarBajaCliente(operacion);
            case TipoOperacion.BORRAR_CLIENTE:
                return borrarCliente(operacion);
            default:
                outbox.descartar(operacion.getId());
                return ResultadoSync.permanente("Operación desconocida en la cola.");
        }
    }

    private ResultadoSync crearCliente(OperacionPendienteEntity operacion) {
        ClienteEntity fila = clienteDao.porIdLocal(operacion.getIdLocal());
        if (fila == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<ClienteDto> resultado = remoto.crear(
                fila.getNombre(), fila.getApellido(), fila.getIdentidad(), fila.getTelefono());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }

        ClienteDto creado = resultado.getValor();
        fila.setIdServidor(creado.getIdCliente());
        fila.setActualizadoEn(creado.getActualizadoEn());

        // El INSERT siempre crea el cliente Activo: CrearClienteDto no lleva id_estado (Plan
        // Fase 2d, §2.1). Si el usuario ya lo había dado de baja sin red antes de que este
        // CREAR subiera, ese cambio se aplica ahora con un segundo viaje (mismo caso que
        // SincronizadorMesas.crearMesa con el estado operativo).
        if (!fila.isActivo()) {
            remoto.cambiarBaja(creado.getIdCliente(), false);
        }
        fila.setIdEstado(creado.getIdEstado());
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        clienteDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync actualizarCliente(OperacionPendienteEntity operacion) {
        ClienteEntity fila = clienteDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            // La edición ya viajó en el CREAR pendiente, que lee la fila fresca al drenar.
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado = remoto.actualizarDatos(fila.getIdServidor(),
                fila.getNombre(), fila.getApellido(), fila.getIdentidad(), fila.getTelefono());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        clienteDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync cambiarBajaCliente(OperacionPendienteEntity operacion) {
        ClienteEntity fila = clienteDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado = remoto.cambiarBaja(fila.getIdServidor(), fila.isActivo());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        clienteDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    /**
     * La fila local ya se borró al encolar (mismo patrón que
     * {@code SincronizadorMenu.borrarCategoria}), así que el {@code id_servidor} viaja en el
     * payload: se reusa {@code PayloadOperacion.borrarCategoria}/{@code idServidorDe}, que ya
     * es genérico (solo envuelve un entero) más allá de su nombre.
     */
    private ResultadoSync borrarCliente(OperacionPendienteEntity operacion) {
        Integer idServidor = PayloadOperacion.idServidorDe(operacion.getPayloadJson());
        if (idServidor == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado = remoto.borrar(idServidor);
        if (!resultado.isExitoso()) {
            // La fila local ya no existe; si el servidor rechaza el borrado (le llegaron
            // pedidos justo antes), el delta de la próxima pasada la vuelve a traer.
            return manejarFallo(operacion, resultado, null);
        }
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync manejarFallo(OperacionPendienteEntity operacion,
                                       ResultadoRed<?> resultado, @Nullable ClienteEntity fila) {
        String mensaje = resultado.getMensaje() == null
                ? "No se pudo sincronizar un cambio de cliente." : resultado.getMensaje();
        if (ClasificadorDeError.esTransitorio(resultado.getCodigoHttp())) {
            boolean quedanIntentos =
                    outbox.registrarErrorTransitorio(operacion.getId(), mensaje, MAX_INTENTOS);
            if (quedanIntentos) {
                return ResultadoSync.transitorio(mensaje);
            }
        }
        outbox.descartar(operacion.getId());
        if (fila != null) {
            fila.setEstadoSync(EstadoSync.ERROR.name());
            clienteDao.actualizar(fila);
        }
        return ResultadoSync.permanente(mensaje);
    }

    // ------------------------------------------------------------------ delta

    private ResultadoSync bajarDelta(@Nullable String errorPermanenteDelDrenado) {
        // La marca queda FIJA durante toda la pasada y las páginas avanzan por offset (ver
        // el Javadoc de la clase y de SincronizadorMenu.bajarPlatillos()).
        String marcaInicial = leerMarca();
        String marcaMaxima = marcaInicial;
        boolean huboConflictoLocal = false;
        int desplazamiento = 0;

        for (int pagina = 0; pagina < MAX_PAGINAS; pagina++) {
            ResultadoRed<List<ClienteDto>> resultado =
                    remoto.listarClientesDesde(marcaInicial, desplazamiento);
            if (!resultado.isExitoso()) {
                return convertirFalloDelta(resultado);
            }
            List<ClienteDto> filas = resultado.getValor();
            for (ClienteDto dto : filas) {
                if (dto.getActualizadoEn() != null) {
                    marcaMaxima = mayor(marcaMaxima, dto.getActualizadoEn());
                }
            }
            huboConflictoLocal |= aplicarPagina(filas);
            desplazamiento += filas.size();
            if (filas.size() < ClienteRemoto.LIMITE_DELTA) {
                break;
            }
        }

        guardarMarca(marcaMaxima);
        if (errorPermanenteDelDrenado != null) {
            return ResultadoSync.permanente(errorPermanenteDelDrenado);
        }
        return huboConflictoLocal
                ? ResultadoSync.permanente(CAMBIO_LOCAL_PERDIDO)
                : ResultadoSync.ok();
    }

    /**
     * Aplica una página entera en <b>una sola transacción</b> — ver el Javadoc de
     * {@link EjecutorDeTransaccion} y de {@code SincronizadorMenu.aplicarPagina}.
     *
     * @return {@code true} si alguna fila perdió un cambio local por conflicto LWW
     */
    private boolean aplicarPagina(List<ClienteDto> pagina) {
        boolean[] huboConflicto = {false};
        transacciones.enTransaccion(() -> {
            for (ClienteDto dto : pagina) {
                huboConflicto[0] |= aplicarCliente(dto);
            }
        });
        return huboConflicto[0];
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
            return !EstadoSync.SINCRONIZADO.name().equals(existente.getEstadoSync());
        }
        return false;
    }

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
                ? "No se pudo sincronizar la lista de clientes." : resultado.getMensaje();
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

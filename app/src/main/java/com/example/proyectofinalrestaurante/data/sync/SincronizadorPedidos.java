package com.example.proyectofinalrestaurante.data.sync;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.dao.ClienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.MesaDao;
import com.example.proyectofinalrestaurante.data.local.dao.NotificacionDao;
import com.example.proyectofinalrestaurante.data.local.dao.PedidoDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.PedidoEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.NotificacionMapper;
import com.example.proyectofinalrestaurante.data.local.mapper.PedidoMapper;
import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.sync.payload.PayloadCrearPedido;
import com.example.proyectofinalrestaurante.data.sync.payload.PayloadOperacion;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearPedidoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.PedidoDto;
import com.example.proyectofinalrestaurante.data.repository.PedidoRemoto;
import com.example.proyectofinalrestaurante.data.repository.ResultadoRed;
import com.example.proyectofinalrestaurante.domain.Permisos;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.TipoNotificacion;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import java.time.OffsetDateTime;

/**
 * El sincronizador del módulo Pedidos (Plan Fase 3, E4).
 *
 * <p>Dos trabajos, en orden: primero <b>drena el outbox</b> (sube los
 * {@code AVANZAR_ESTADO_PEDIDO} en orden FIFO) y después <b>baja el delta</b> con la marca
 * de agua. Si el drenado se corta por un error transitorio, no se baja el delta: el
 * reintento lo va a repetir todo. Mismo molde que {@link SincronizadorMenu}.</p>
 *
 * <p><b>El delta se copia del Menú, nunca de Mesas/Clientes (Plan Fase 3, §4.4).</b> La
 * marca queda <b>fija</b> durante toda la pasada y las páginas avanzan por {@code offset};
 * avanzarla por página perdía las filas excedentes cuando ≥50 comparten
 * {@code actualizado_en} —el caso normal de una tanda de pedidos del mediodía—, el bug
 * P-029 que este módulo no reintroduce.</p>
 *
 * <p>Al aplicar el delta se <b>derivan las notificaciones</b> del buzón local (§4.6): una
 * fila nueva avisa a cocina y una fila que pasa a "Listo" avisa al mesero que tomó el
 * pedido. Se derivan dentro del mismo recorrido que aplica las filas, con
 * {@code clave_unica} de idempotencia para que una pasada repetida no duplique avisos.</p>
 */
public final class SincronizadorPedidos implements Sincronizador {

    static final int MAX_INTENTOS = 3;
    static final int LOTE = 50;

    /**
     * Tope de páginas por pasada del delta. Es un cinturón, no una regla de negocio: con
     * 50 filas por página son 10 000 filas, mucho más de lo que la ventana de 48 h va a
     * juntar. Existe para que un servidor que devolviera páginas llenas indefinidamente no
     * dejara al worker girando hasta que WorkManager lo mate por tiempo.
     */
    static final int MAX_PAGINAS = 200;

    /** Clave de la marca de agua y nombre de la tabla; el mismo para ambos (Plan Fase 3, §4.4). */
    static final String TABLA_PEDIDOS = "pedidos";

    /** Mensaje con que el conflicto LWW (§4.6) avisa que un cambio local se perdió. */
    static final String CAMBIO_LOCAL_PERDIDO =
            "Un cambio local se perdió: el servidor tenía una versión más reciente.";

    private final PedidoRemoto remoto;
    private final Outbox outbox;
    private final PedidoDao pedidoDao;
    private final NotificacionDao notificacionDao;
    private final SincronizacionDao sincronizacionDao;
    private final EjecutorDeTransaccion transacciones;
    /** Para resolver {@code mesa_id_local} al drenar un {@code CREAR_PEDIDO} (§4.2). */
    private final MesaDao mesaDao;
    /** Para resolver {@code cliente_id_local} al drenar un {@code CREAR_PEDIDO} (§4.2). */
    private final ClienteDao clienteDao;
    /** El outbox de Módulo CLIENTES: saber si el CREAR_CLIENTE sigue pendiente o se descartó. */
    private final Outbox outboxDeClientes;

    /** Piso de arranque en frío: el ISO de {@code ahora − 48 h}, cuando no hay marca. */
    private final Supplier<String> pisoDeArranque;
    /** Reloj para las notificaciones; inyectado para poder testear sin dormir. */
    private final Supplier<Long> reloj;

    public SincronizadorPedidos(PedidoRemoto remoto, Outbox outbox,
                                PedidoDao pedidoDao, NotificacionDao notificacionDao,
                                SincronizacionDao sincronizacionDao,
                                EjecutorDeTransaccion transacciones,
                                MesaDao mesaDao, ClienteDao clienteDao, Outbox outboxDeClientes,
                                Supplier<String> pisoDeArranque, Supplier<Long> reloj) {
        this.remoto = remoto;
        this.outbox = outbox;
        this.pedidoDao = pedidoDao;
        this.notificacionDao = notificacionDao;
        this.sincronizacionDao = sincronizacionDao;
        this.transacciones = transacciones;
        this.mesaDao = mesaDao;
        this.clienteDao = clienteDao;
        this.outboxDeClientes = outboxDeClientes;
        this.pisoDeArranque = pisoDeArranque;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------ orquestación

    @Override
    public String modulo() {
        return TipoOperacion.Modulo.PEDIDOS;
    }

    /** Una pasada completa: drenar y bajar. Devuelve el resultado para el worker. */
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
            case TipoOperacion.CREAR_PEDIDO:
                return crearPedido(operacion);
            case TipoOperacion.AVANZAR_ESTADO_PEDIDO:
                return avanzarEstado(operacion);
            default:
                // Operación de un tipo que esta versión no conoce: no puede bloquear la cola.
                outbox.descartar(operacion.getId());
                return ResultadoSync.permanente("Operación desconocida en la cola.");
        }
    }

    private ResultadoSync avanzarEstado(OperacionPendienteEntity operacion) {
        PedidoEntity fila = pedidoDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            // Sin fila o sin id_servidor no hay nada que empujar: se descarta.
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        Integer idEstado = PayloadOperacion.idEstadoPedidoDe(operacion.getPayloadJson());
        if (idEstado == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado = remoto.avanzarEstado(fila.getIdServidor(), idEstado);
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        // Optimista: el usuario ya veía este estado; se confirma y se sincroniza la fila.
        fila.setIdEstadoPedido(idEstado);
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        pedidoDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    /**
     * Sube un pedido tomado offline contra el RPC {@code crear_pedido}(Plan Fase 3b, §5.3
     * y §4.2). El payload guarda {@code mesa_id_local}/{@code cliente_id_local}, así que acá
     * se resuelven al {@code id_servidor} antes de armar el {@code JSONB}: el servidor espera
     * {@code id_mesa}/{@code id_cliente} reales.
     *
     * <p>Política de degradación (§4.2):</p>
     * <ul>
     *   <li>un platillo sin {@code idServidor} (el payload lo codifica como {@code 0}) hace
     *       el pedido {@code ERROR} permanente — no hay pedido sin líneas válidas;</li>
     *   <li>una mesa sin {@code idServidor} sube con {@code id_mesa = NULL} + notificación;</li>
     *   <li>un cliente sin {@code idServidor} cuya {@code CREAR_CLIENTE} sigue pendiente en
     *       el outbox de Clientes es <b>transitorio</b> —sin consumir intento— porque la
     *       próxima pasada puede resolverlo; si el {@code CREAR_CLIENTE} ya se descartó
     *       (error permanente), el pedido sube con {@code id_cliente = NULL} + notificación.</li>
     * </ul>
     *
     * <p>La regla de fondo: un pedido que no sube es peor que un pedido sin el dato
     * accesorio. Se degrada el accesorio, nunca la transacción.</p>
     */
    private ResultadoSync crearPedido(OperacionPendienteEntity operacion) {
        PayloadCrearPedido.Cuerpo payload =
                PayloadCrearPedido.parsear(operacion.getPayloadJson());
        if (payload == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }

        // Un platillo sin idServidor (payload lo trae como 0) invalida el pedido: no hay
        // líneas reales que subir. Cinturón de seguridad; la UI lo impide antes.
        for (PayloadCrearPedido.Linea linea : payload.getLineas()) {
            if (linea.getIdPlatillo() <= 0) {
                outbox.descartar(operacion.getId());
                PedidoEntity fila = pedidoDao.porIdLocal(operacion.getIdLocal());
                if (fila != null) {
                    fila.setEstadoSync(EstadoSync.ERROR.name());
                    pedidoDao.actualizar(fila);
                }
                notificarError("El pedido se descartó porque una línea no pudo subir.");
                return ResultadoSync.permanente("Un platillo sin id_servidor invalidó el pedido.");
            }
        }

        // Resolver ids locales → id_servidor. La mesa que no se pueda resolver se degrada;
        // el cliente pendiente corta la pasada como transitorio, el descartado degrada.
        Integer idMesa = resolverMesa(payload);
        ResolucionCliente clientes = resolverCliente(payload);
        if (clientes.esPendiente()) {
            return ResultadoSync.transitorio(
                    "El cliente del pedido todavía no se sincronizó; reintentando más tarde.");
        }

        String cuerpoJson = construirJsonRpc(payload, idMesa, clientes.idServidor());
        ResultadoRed<CrearPedidoDto> resultado = remoto.crearPedido(cuerpoJson);
        if (!resultado.isExitoso()) {
            PedidoEntity fila = pedidoDao.porIdLocal(operacion.getIdLocal());
            return manejarFallo(operacion, resultado, fila);
        }

        // El RPC devuelve el id_pedido — y el mismo si la clave de idempotencia ya se usó
        // (B2): re-aplicarlo es idempotente (upsert + LWW).
        PedidoEntity cabecera = pedidoDao.porIdLocal(operacion.getIdLocal());
        if (cabecera != null) {
            cabecera.setIdServidor(resultado.getValor().getIdPedido());
            cabecera.setEstadoSync(EstadoSync.SINCRONIZADO.name());
            pedidoDao.actualizar(cabecera);
        }
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    /** Resuelve {@code mesa_id_local} → {@code id_servidor}; no la encuentra → {@code null}. */
    private Integer resolverMesa(PayloadCrearPedido.Cuerpo payload) {
        Integer mesaIdLocal = payload.getMesaIdLocal();
        if (mesaIdLocal == null) {
            return null;
        }
        MesaEntity mesa = mesaDao.porIdLocal(mesaIdLocal);
        if (mesa == null || mesa.getIdServidor() == null) {
            notificarDatoDegradado(TipoNotificacion.PEDIDO_SIN_MESA, String.valueOf(mesaIdLocal));
            return null;
        }
        return mesa.getIdServidor();
    }

    /**
     * Resuelve {@code cliente_id_local} → {@code id_servidor}. Si la {@code CREAR_CLIENTE}
     * sigue en el outbox (no drenó aún), devuelve {@link ResolucionCliente#esPendiente()}
     * {@code true} y el {@code crearPedido} corta como transitorio <b>sin consumir intento</b>.
     * Si el {@code CREAR_CLIENTE} ya se descartó, degrada a {@code null} y notifica (B4).
     */
    private ResolucionCliente resolverCliente(PayloadCrearPedido.Cuerpo payload) {
        Integer idClienteLocal = payload.getClienteIdLocal();
        if (idClienteLocal == null) {
            return ResolucionCliente.resuelto(null);
        }
        ClienteEntity cliente = clienteDao.porIdLocal(idClienteLocal);
        if (cliente != null && cliente.getIdServidor() != null) {
            return ResolucionCliente.resuelto(cliente.getIdServidor());
        }
        // Sin id_servidor: ¿el CREAR_CLIENTE sigue en cola o se descartó?
        for (OperacionPendienteEntity op : outboxDeClientes.deFila(idClienteLocal)) {
            if (TipoOperacion.CREAR_CLIENTE.equals(op.getTipo())) {
                return ResolucionCliente.pendiente(); // transitorio, sin consumir intento
            }
        }
        // El CREAR_CLIENTE se descartó por error permanente: degrada al cliente (B4).
        notificarDatoDegradado(TipoNotificacion.PEDIDO_SIN_CLIENTE, String.valueOf(idClienteLocal));
        return ResolucionCliente.resuelto(null);
    }

    /** Arma el JSON final del RPC con los ids ya resueltos del servidor. */
    private String construirJsonRpc(PayloadCrearPedido.Cuerpo payload, Integer idMesa,
                                    Integer idCliente) {
        return PayloadCrearPedido.serializarRpc(payload.getClaveIdempotencia(),
                payload.getFecha(), payload.getIdTipoPedido(), idMesa, idCliente, payload.getLineas());
    }

    /** Notifica que un id accesorio de un pedido se degradó al subir sin él (Plan 3b §4.2). */
    private void notificarDatoDegradado(TipoNotificacion tipo, String idLocal) {
        notificacionDao.insertar(NotificacionMapper.aEntidadNueva(
                tipo, null, null, idLocal, reloj.get()));
    }

    /** Resultado de la resolución del cliente al drenar un {@code CREAR_PEDIDO}. */
    private static final class ResolucionCliente {

        private final Integer idServidor;

        private final boolean esPendiente;

        private ResolucionCliente(Integer idServidor, boolean esPendiente) {
            this.idServidor = idServidor;
            this.esPendiente = esPendiente;
        }

        static ResolucionCliente resuelto(@Nullable Integer idServidor) {
            return new ResolucionCliente(idServidor, false);
        }

        static ResolucionCliente pendiente() {
            return new ResolucionCliente(null, true);
        }

        /** {@code true} si la {@code CREAR_CLIENTE} todavía no drenó: reintentar, sin consumir intento. */
        boolean esPendiente() {
            return esPendiente;
        }

        @Nullable
        Integer idServidor() {
            return idServidor;
        }
    }

    /**
     * Clasifica el fallo de una operación. Transitorio → se registra el error y, si quedan
     * intentos, corta la pasada (el worker reintenta); si se agotaron, se descarta como si
     * fuera permanente. Permanente → se descarta para no bloquear la cola, se marca la fila
     * como {@code ERROR} y se deriva una notificación {@code ERROR_SYNC} (§4.6).
     */
    private ResultadoSync manejarFallo(OperacionPendienteEntity operacion,
                                       ResultadoRed<?> resultado, PedidoEntity fila) {
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
        if (fila != null) {
            fila.setEstadoSync(EstadoSync.ERROR.name());
            pedidoDao.actualizar(fila);
        }
        notificarError(mensaje);
        return ResultadoSync.permanente(mensaje);
    }

    // ------------------------------------------------------------------ delta

    /**
     * Baja el delta (Plan Fase 3, §4.4). El mensaje que trae es el de un error permanente
     * del drenado, que debe conservarse si el delta termina bien: la pasada "terminó" pero
     * el usuario tiene que enterarse de que algo no se subió.
     */
    private ResultadoSync bajarDelta(@Nullable String errorPermanenteDelDrenado) {
        // La marca queda FIJA durante toda la pasada y las páginas avanzan por offset.
        // Copiado de SincronizadorMenu: avanzar la marca por página hacía que, con ≥50 filas
        // del mismo instante, las que seguían no se bajaran nunca (P-029).
        String marcaInicial = leerMarca();
        // Arranque en frío: sin marca se pide desde ahora − 48 h en vez de la vista entera
        // (R6 aplicado al lado servidor; los pedidos viejos no sirven en el tablero).
        // Solapamiento de la marca (Plan Fase 3b, §3.3): pedir `> marca − 2s` en vez de
        // `> marca` para cerrar la ventana del cursor reloj. Re-bajar unas pocas filas es
        // seguro: aplicar filas ya es idempotente (upsert por id_servidor + LWW).
        String desde = marcaInicial != null ? restarDosSegundos(marcaInicial) : pisoDeArranque.get();
        String marcaMaxima = marcaInicial != null ? marcaInicial : desde;
        boolean huboConflictoLocal = false;
        int desplazamiento = 0;

        for (int pagina = 0; pagina < MAX_PAGINAS; pagina++) {
            ResultadoRed<List<PedidoDto>> resultado =
                    remoto.listarPedidosDesde(desde, desplazamiento);
            if (!resultado.isExitoso()) {
                return convertirFalloDelta(resultado);
            }
            List<PedidoDto> filas = resultado.getValor();
            for (PedidoDto dto : filas) {
                if (dto.getActualizadoEn() != null) {
                    marcaMaxima = mayor(marcaMaxima, dto.getActualizadoEn());
                }
            }
            huboConflictoLocal |= aplicarPagina(filas, this::aplicarPedido);
            desplazamiento += filas.size();
            if (filas.size() < PedidoRemoto.LIMITE_DELTA) {
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
     * Aplica una página entera en <b>una sola transacción</b>. Las notificaciones derivadas
     * entran en esa misma transacción: si la pasada se corta a mitad, la reaplicación no
     * duplica avisos gracias al índice único de {@code clave_unica} (B5).
     *
     * @return {@code true} si alguna fila perdió un cambio local por conflicto LWW
     */
    private boolean aplicarPagina(List<PedidoDto> pagina, Predicate<PedidoDto> aplicar) {
        boolean[] huboConflicto = {false};
        transacciones.enTransaccion(() -> {
            for (PedidoDto dto : pagina) {
                huboConflicto[0] |= aplicar.test(dto);
            }
        });
        return huboConflicto[0];
    }

    private boolean aplicarPedido(PedidoDto dto) {
        PedidoEntity existente = pedidoDao.porIdServidor(dto.getIdPedido());
        if (existente == null) {
            pedidoDao.insertar(PedidoMapper.desdeServidor(dto));
            notificarPedidoNuevo(dto);
            return false;
        }
        if (ganaElServidor(existente.getEstadoSync(), existente.getActualizadoEn(),
                dto.getActualizadoEn())) {
            PedidoEntity entidad = PedidoMapper.desdeServidor(dto);
            entidad.setIdLocal(existente.getIdLocal());
            pedidoDao.actualizar(entidad);
            if (pasoAListo(existente, dto)) {
                notificarPedidoListo(dto);
            }
            // Solo es un conflicto si la fila local tenía un cambio sin subir (pendiente/
            // error). Pisar una fila SINCRONIZADO es el refresco normal del delta — y con el
            // solapamiento de la marca (Plan 3b, §3.3) el delta re-baja las filas de la
            // frontera a propósito; contarlas como "cambio local perdido" sería falso.
            return !EstadoSync.SINCRONIZADO.name().equals(existente.getEstadoSync());
        }
        // Gana el local (fila pendiente/error con marca más nueva): no se pisa.
        return false;
    }

    // ------------------------------------------------------------------ buzón

    /** Fila nueva: aviso para todos los de cocina (B6). */
    private void notificarPedidoNuevo(PedidoDto dto) {
        notificacionDao.insertar(NotificacionMapper.aEntidadNueva(
                TipoNotificacion.PEDIDO_NUEVO, Permisos.ROL_COCINA, null,
                String.valueOf(dto.getIdPedido()), reloj.get()));
    }

    /** Fila que pasa a Listo: aviso para el mesero que tomó el pedido (B7). */
    private void notificarPedidoListo(PedidoDto dto) {
        notificacionDao.insertar(NotificacionMapper.aEntidadNueva(
                TipoNotificacion.PEDIDO_LISTO, null, dto.getIdAuthUsuario(),
                String.valueOf(dto.getIdPedido()), reloj.get()));
    }

    /** Una operación se descartó por error permanente (Plan Fase 3, §4.6). */
    private void notificarError(String mensaje) {
        notificacionDao.insertar(NotificacionMapper.aEntidadNueva(
                TipoNotificacion.ERROR_SYNC, null, null, mensaje, reloj.get()));
    }

    private boolean pasoAListo(PedidoEntity existente, PedidoDto dto) {
        return existente.getIdEstadoPedido() != 3 && dto.getIdEstadoPedido() == 3;
    }

    /**
     * Last-write-wins (§4.6): el servidor gana si la fila local está sincronizada (es la
     * misma versión, pisarla es inofensivo) o, estando pendiente/error, trae una marca más
     * nueva que la local. El {@code trg_pedido_actualizado_en} del servidor toca
     * {@code actualizado_en} en cada {@code UPDATE}, así que el cambio de estado del RPC
     * llega con marca nueva y gana como corresponde.
     */
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

    private ResultadoSync convertirFalloDelta(ResultadoRed<?> resultado) {
        String mensaje = resultado.getMensaje() == null
                ? "No se pudieron sincronizar los pedidos." : resultado.getMensaje();
        if (ClasificadorDeError.esTransitorio(resultado.getCodigoHttp())) {
            return ResultadoSync.transitorio(mensaje);
        }
        return ResultadoSync.permanente(mensaje);
    }

    // ------------------------------------------------------------------ marca de agua

    @Nullable
    private String leerMarca() {
        SincronizacionEntity marca = sincronizacionDao.porTabla(TABLA_PEDIDOS);
        return marca == null ? null : marca.getMarcaAgua();
    }

    private void guardarMarca(@Nullable String marca) {
        SincronizacionEntity entidad = new SincronizacionEntity();
        entidad.setTabla(TABLA_PEDIDOS);
        entidad.setMarcaAgua(marca);
        entidad.setUltimoIntento(reloj.get());
        entidad.setUltimoError(null);
        sincronizacionDao.guardar(entidad);
    }

    /** Comparación lexicográfica: los timestamps vienen del servidor en el mismo formato. */
    private static String mayor(@Nullable String a, @Nullable String b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }

    /**
     * Solapamiento de la marca de agua (Plan Fase 3b, §3.3): el cursor del delta es un
     * reloj, y pedir {@code > marca − 2s} cierra la ventana en la que una fila commiteada
     * en el mismo instante que la marca quedaría sin bajar. Si la marca no es un ISO
     * parseable (nunca debería pasar), se devuelve la marca sin tocar.
     */
    private static String restarDosSegundos(String marca) {
        try {
            return OffsetDateTime.parse(marca).minusSeconds(2).toString();
        } catch (java.time.format.DateTimeParseException ignorada) {
            // Marca ilegible: peor caso, se re-bajan las filas de la última marca.
            return marca;
        }
    }
}
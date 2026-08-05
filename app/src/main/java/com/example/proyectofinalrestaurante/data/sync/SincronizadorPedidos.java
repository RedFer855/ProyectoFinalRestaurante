package com.example.proyectofinalrestaurante.data.sync;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.dao.NotificacionDao;
import com.example.proyectofinalrestaurante.data.local.dao.PedidoDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.PedidoEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.NotificacionMapper;
import com.example.proyectofinalrestaurante.data.local.mapper.PedidoMapper;
import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.remote.dto.PedidoDto;
import com.example.proyectofinalrestaurante.data.repository.PedidoRemoto;
import com.example.proyectofinalrestaurante.data.repository.ResultadoRed;
import com.example.proyectofinalrestaurante.domain.Permisos;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.TipoNotificacion;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

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

    /** Piso de arranque en frío: el ISO de {@code ahora − 48 h}, cuando no hay marca. */
    private final Supplier<String> pisoDeArranque;
    /** Reloj para las notificaciones; inyectado para poder testear sin dormir. */
    private final Supplier<Long> reloj;

    public SincronizadorPedidos(PedidoRemoto remoto, Outbox outbox,
                                PedidoDao pedidoDao, NotificacionDao notificacionDao,
                                SincronizacionDao sincronizacionDao,
                                EjecutorDeTransaccion transacciones,
                                Supplier<String> pisoDeArranque, Supplier<Long> reloj) {
        this.remoto = remoto;
        this.outbox = outbox;
        this.pedidoDao = pedidoDao;
        this.notificacionDao = notificacionDao;
        this.sincronizacionDao = sincronizacionDao;
        this.transacciones = transacciones;
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
        String desde = marcaInicial != null ? marcaInicial : pisoDeArranque.get();
        String marcaMaxima = desde;
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
            return true;
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
}
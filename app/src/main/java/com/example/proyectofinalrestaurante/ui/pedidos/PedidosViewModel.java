package com.example.proyectofinalrestaurante.ui.pedidos;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.ReglasPedido;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.Pedido;
import com.example.proyectofinalrestaurante.domain.repository.PedidoRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel del tablero de Pedidos (Plan Fase 3, E7). Mismo patrón que {@code ClientesViewModel}:
 * {@link ExecutorService} inyectado (P-005), fuente Room vía {@link LiveData} con
 * <b>ventana creciente</b> (§4.5): arranca en 20 y cada {@link #cargarMas()} suma 20 más
 * cuando el scroll llega al final y {@code hayMas} es cierto.
 *
 * <p>La validación de transiciones vive acá, no en el repositorio: {@code ReglasPedido}
 * con el rol de la sesión decide si se puede avanzar/cancelar, y solo entonces se escribe
 * (optimista, outbox).</p>
 */
public class PedidosViewModel extends ViewModel {

    private static final int VENTANA_INICIAL = 20;
    private static final int PASO_VENTANA = 20;
    private static final String PEDIDO_AVANZADO = "Pedido avanzado.";
    private static final String PEDIDO_CANCELADO = "Pedido cancelado.";
    private static final String SIN_PERMISO_PARA_AVANZAR =
            "Tu rol no puede cambiar ese pedido a ese estado.";
    private static final String SIN_PERMISO_PARA_CANCELAR =
            "Solo el administrador puede cancelar pedidos.";

    private final PedidoRepository repositorio;
    private final ExecutorService executor;
    @Nullable private final String rol;

    private final MediatorLiveData<EstadoPedidos> estado = new MediatorLiveData<>();

    private LiveData<List<Pedido>> fuenteVentana;
    private List<Pedido> pedidosActuales = Collections.emptyList();
    private int total = 0;
    private int ventana = VENTANA_INICIAL;
    @Nullable private EstadoPedido filtro = null;
    private List<EstadoPedido> estados = Collections.emptyList();
    private boolean sincronizando = false;
    @Nullable private String ultimoErrorSync = null;
    @Nullable private String errorDeOperacion = null;
    @Nullable private String mensajeExito = null;

    public PedidosViewModel(@NonNull PedidoRepository repositorio,
                            @NonNull ExecutorService executor,
                            @Nullable String rol) {
        this.repositorio = repositorio;
        this.executor = executor;
        this.rol = rol;
        fuenteVentana = repositorio.observarVentana(ventana);
        estado.addSource(fuenteVentana, this::cuandoLleganPedidos);
        estado.addSource(repositorio.contarPedidos(), this::cuandoCambiaElTotal);
        estado.addSource(repositorio.observarEstadosPedido(), this::cuandoLleganEstados);
        estado.addSource(repositorio.getEstadoSincronizacion(), this::cuandoCambiaSincronizacion);
        estado.setValue(EstadoPedidos.cargando());
        // Sync-on-launch: no depender del pull-to-refresh para la primera bajada de datos.
        sincronizar();
    }

    public LiveData<EstadoPedidos> getEstado() {
        return estado;
    }

    /** Los estados del catálogo (para los chips del filtro), en el orden del servidor. */
    public List<EstadoPedido> getEstados() {
        return new ArrayList<>(estados);
    }

    public void sincronizar() {
        repositorio.sincronizar();
    }

    // ------------------------------------------------------------------ paginación

    /**
     * Suma {@link #PASO_VENTANA} a la ventana y re-observa (Plan Fase 3, §4.5). Una sola
     * {@code LiveData} cuyo {@code LIMIT} crece: Room re-emite y la lista se actualiza sola.
     */
    public void cargarMas() {
        ventana += PASO_VENTANA;
        estado.removeSource(fuenteVentana);
        fuenteVentana = repositorio.observarVentana(ventana);
        estado.addSource(fuenteVentana, this::cuandoLleganPedidos);
    }

    // ------------------------------------------------------------------ filtros

    public void filtrarPorEstado(@Nullable EstadoPedido estadoFiltro) {
        filtro = estadoFiltro;
        recalcular();
    }

    // ------------------------------------------------------------------ operaciones

    /** Avanza el pedido al estado siguiente natural del flujo (Pendiente → … → Entregado). */
    public void avanzarEstado(Pedido pedido) {
        EstadoPedido nuevo = ReglasPedido.siguienteDe(pedido.getEstado());
        if (nuevo == null || !ReglasPedido.puedeCambiarA(rol, pedido, nuevo)) {
            publicarError(SIN_PERMISO_PARA_AVANZAR);
            return;
        }
        executor.execute(() -> ejecutar(
                repositorio.avanzarEstado(pedido.getIdLocal(), nuevo), PEDIDO_AVANZADO));
    }

    /** Cancela el pedido ({@code CANCELADO}). Solo admin (matriz de {@code Permisos}). */
    public void cancelar(Pedido pedido) {
        if (!ReglasPedido.puedeCancelar(rol)) {
            publicarError(SIN_PERMISO_PARA_CANCELAR);
            return;
        }
        executor.execute(() -> ejecutar(
                repositorio.avanzarEstado(pedido.getIdLocal(), EstadoPedido.CANCELADO),
                PEDIDO_CANCELADO));
    }

    // ------------------------------------------------------------------ fuentes de datos

    private void cuandoLleganPedidos(List<Pedido> pedidos) {
        pedidosActuales = pedidos;
        recalcular();
    }

    private void cuandoCambiaElTotal(Integer nuevoTotal) {
        total = nuevoTotal == null ? 0 : nuevoTotal;
        recalcular();
    }

    private void cuandoLleganEstados(List<EstadoPedido> estadosNuevos) {
        estados = estadosNuevos == null ? Collections.emptyList() : estadosNuevos;
        recalcular();
    }

    private void cuandoCambiaSincronizacion(EstadoSincronizacion estadoSync) {
        sincronizando = estadoSync.isSincronizando();
        ultimoErrorSync = estadoSync.getUltimoError();
        recalcular();
    }

    // ------------------------------------------------------------------ operaciones auxiliares

    public void onMensajeConsumido() {
        mensajeExito = null;
        EstadoPedidos actual = estado.getValue();
        if (actual != null && actual.getMensajeExito() != null) {
            estado.setValue(actual.sinMensaje());
        }
    }

    public void onErrorConsumido() {
        errorDeOperacion = null;
        recalcular();
    }

    private void ejecutar(Result<Void> resultado, String mensajeExitoso) {
        if (resultado.isSuccess()) {
            publicarExito(mensajeExitoso);
        } else {
            publicarError(resultado.getError());
        }
    }

    private void publicarExito(String mensaje) {
        mensajeExito = mensaje;
        errorDeOperacion = null;
        estado.postValue(construirEstado());
    }

    private void publicarError(String mensaje) {
        errorDeOperacion = mensaje;
        estado.postValue(construirEstado());
    }

    // ------------------------------------------------------------------ estado

    private void recalcular() {
        estado.setValue(construirEstado());
    }

    private EstadoPedidos construirEstado() {
        EstadoPedidos nuevo = EstadoPedidos.conDatos(filtrados(), filtro, sincronizando,
                ultimoErrorSync, total, ventana);
        if (errorDeOperacion != null) {
            nuevo = nuevo.conError(errorDeOperacion);
        }
        if (mensajeExito != null) {
            nuevo = nuevo.conMensaje(mensajeExito);
        }
        return nuevo;
    }

    private List<Pedido> filtrados() {
        if (filtro == null) {
            return pedidosActuales;
        }
        List<Pedido> resultado = new ArrayList<>();
        for (Pedido pedido : pedidosActuales) {
            if (filtro.equals(pedido.getEstado())) {
                resultado.add(pedido);
            }
        }
        return resultado;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}

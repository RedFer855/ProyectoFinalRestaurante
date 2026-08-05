package com.example.proyectofinalrestaurante.ui.detallepedido;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.model.LineaPedido;
import com.example.proyectofinalrestaurante.domain.repository.PedidoRepository;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel del detalle de un pedido (Plan Fase 3b, E9). La carga es <b>bajo demanda</b>: el
 * tablero no baja las líneas hasta que el {@link DetallePedidoHoja} se abre y llama a
 * {@link #cargarDetalle(long)}. Una sola fuente, {@link PedidoRepository#observarDetalle(long)}.
 *
 * <p>{@link ExecutorService} inyectado (P-005): la lectura viaja por Room en su propio hilo,
 * pero el contrato MVVM mantiene el ejecutor fuera del ViewModel para testear y para el cierre
 * ordenado. Al reabrir con otro pedido se quita la fuente anterior antes de observar la nueva.</p>
 */
public class DetallePedidoViewModel extends ViewModel {

    private final PedidoRepository repositorio;
    private final ExecutorService executor;

    private final MediatorLiveData<EstadoDetallePedido> estado = new MediatorLiveData<>();

    private LiveData<List<LineaPedido>> fuenteDetalle;

    public DetallePedidoViewModel(@NonNull PedidoRepository repositorio,
                                  @NonNull ExecutorService executor) {
        this.repositorio = repositorio;
        this.executor = executor;
        estado.setValue(new EstadoDetallePedido(Collections.emptyList()));
    }

    public LiveData<EstadoDetallePedido> getEstado() {
        return estado;
    }

    /**
     * Observa las líneas del pedido indicado. Si ya había otra fuente conectada (se reabrió la
     * hoja con otro pedido), se desconecta antes para no acumular observadores muertos.
     */
    public void cargarDetalle(long idPedidoLocal) {
        if (fuenteDetalle != null) {
            estado.removeSource(fuenteDetalle);
        }
        fuenteDetalle = repositorio.observarDetalle(idPedidoLocal);
        estado.addSource(fuenteDetalle, this::cuandoLleganLineas);
    }

    private void cuandoLleganLineas(List<LineaPedido> lineas) {
        estado.setValue(new EstadoDetallePedido(lineas));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
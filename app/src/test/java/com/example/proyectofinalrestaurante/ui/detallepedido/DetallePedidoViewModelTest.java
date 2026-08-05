package com.example.proyectofinalrestaurante.ui.detallepedido;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.LineaPedido;
import com.example.proyectofinalrestaurante.domain.model.NuevoPedido;
import com.example.proyectofinalrestaurante.domain.model.Pedido;
import com.example.proyectofinalrestaurante.domain.repository.PedidoRepository;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests de {@link DetallePedidoViewModel} (Plan Fase 3b, E9). Mismo patrón que el resto de los
 * ViewModel: repositorio fake sin Room ni Retrofit, ejecutor síncrono. Verifica la carga bajo
 * demanda (el id que se le pasa al contrato) y los derivados (vacío y total).
 */
public class DetallePedidoViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static LineaPedido linea(long idLocal, String nombre, int cantidad, double precio) {
        return new LineaPedido(idLocal, null, nombre, cantidad, precio);
    }

    private static DetallePedidoViewModel viewModelCon(FakePedidoRepository repositorio) {
        DetallePedidoViewModel viewModel =
                new DetallePedidoViewModel(repositorio, new ExecutorServiceSincrono());
        viewModel.getEstado().observeForever(ignorado -> { });
        return viewModel;
    }

    // ------------------------------------------------------------------ estado inicial

    @Test
    public void alConstruir_muestraListaVaciaYTotalCero() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        DetallePedidoViewModel viewModel = viewModelCon(repositorio);

        EstadoDetallePedido estado = viewModel.getEstado().getValue();
        assertTrue(estado.isVacio());
        assertEquals(0, estado.getLineas().size());
        assertEquals(0.0, estado.getTotal(), 0.0);
    }

    // ------------------------------------------------------------------ carga bajo demanda

    @Test
    public void cargarDetalle_observaElIdIndicado() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        DetallePedidoViewModel viewModel = viewModelCon(repositorio);

        viewModel.cargarDetalle(42);

        assertEquals(42L, repositorio.ultimoIdPedidoObservado);
    }

    @Test
    public void cargarDetalle_conNuevoId_observaElNuevoId() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        DetallePedidoViewModel viewModel = viewModelCon(repositorio);

        viewModel.cargarDetalle(42);
        viewModel.cargarDetalle(77);

        assertEquals(77L, repositorio.ultimoIdPedidoObservado);
    }

    @Test
    public void alRecibirLineas_emiteLaLista() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        DetallePedidoViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargarDetalle(1);

        repositorio.detalle.setValue(Arrays.asList(
                linea(1, "Platillo 1", 2, 3.0), linea(2, "Platillo 2", 1, 4.0)));

        EstadoDetallePedido estado = viewModel.getEstado().getValue();
        assertFalse(estado.isVacio());
        assertEquals(2, estado.getLineas().size());
    }

    @Test
    public void conLineas_isVacioEsFalso() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        DetallePedidoViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargarDetalle(1);

        repositorio.detalle.setValue(new ArrayList<>(
                Collections.singletonList(linea(1, "Solo", 1, 1.0))));

        assertFalse(viewModel.getEstado().getValue().isVacio());
    }

    @Test
    public void sinLineas_quedaVacio() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        DetallePedidoViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargarDetalle(1);

        repositorio.detalle.setValue(new ArrayList<>());

        assertTrue(viewModel.getEstado().getValue().isVacio());
        assertEquals(0.0, viewModel.getEstado().getValue().getTotal(), 0.0);
    }

    // ------------------------------------------------------------------ derivado total

    @Test
    public void elTotalEsLaSumaDeLosSubtotales() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        DetallePedidoViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargarDetalle(1);
        repositorio.detalle.setValue(Arrays.asList(
                linea(1, "A", 2, 3.0), linea(2, "B", 1, 4.0)));

        // 2 × 3 + 1 × 4 = 10.
        assertEquals(10.0, viewModel.getEstado().getValue().getTotal(), 0.0);
    }

    @Test
    public void siElRepositorioVuelveVacio_elTotalSeCaeACero() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        DetallePedidoViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargarDetalle(1);
        repositorio.detalle.setValue(Arrays.asList(linea(1, "Temporal", 2, 3.0)));

        repositorio.detalle.setValue(Collections.emptyList());

        assertTrue(viewModel.getEstado().getValue().isVacio());
        assertEquals(0.0, viewModel.getEstado().getValue().getTotal(), 0.0);
    }

    // ------------------------------------------------------------------ fake

    /** Fake de {@link PedidoRepository}: solo el detalle importa y queda en memoria; el resto tira si se toca. */
    private static final class FakePedidoRepository implements PedidoRepository {

        final MutableLiveData<List<LineaPedido>> detalle = new MutableLiveData<>(new ArrayList<>());
        long ultimoIdPedidoObservado;

        @Override
        public LiveData<List<Pedido>> observarVentana(int ventana) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LiveData<Integer> contarPedidos() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LiveData<List<EstadoPedido>> observarEstadosPedido() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LiveData<EstadoSincronizacion> getEstadoSincronizacion() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sincronizar() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Void> avanzarEstado(long idLocal, EstadoPedido nuevo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Long> crear(NuevoPedido nuevo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LiveData<List<LineaPedido>> observarDetalle(long idLocal) {
            ultimoIdPedidoObservado = idLocal;
            return detalle;
        }
    }

    /** Ejecuta cada tarea en el hilo que la envía (ver ClientesViewModelTest). */
    private static final class ExecutorServiceSincrono extends AbstractExecutorService {

        private volatile boolean cerrado = false;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            cerrado = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            cerrado = true;
            return new ArrayList<>();
        }

        @Override
        public boolean isShutdown() {
            return cerrado;
        }

        @Override
        public boolean isTerminated() {
            return cerrado;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
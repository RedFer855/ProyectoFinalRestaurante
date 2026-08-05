package com.example.proyectofinalrestaurante.ui.pedidos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.LineaPedido;
import com.example.proyectofinalrestaurante.domain.model.NuevoPedido;
import com.example.proyectofinalrestaurante.domain.model.Pedido;
import com.example.proyectofinalrestaurante.domain.repository.PedidoRepository;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests de {@link PedidosViewModel} (Plan Fase 3, E7). Mismo patrón que
 * {@code ClientesViewModelTest}: el repositorio es un fake sin Room ni Retrofit que corta la
 * ventana sobre una lista en memoria — así se prueban B9 y B10 (ventana creciente y
 * {@code hayMas} derivado del total, no de una bandera).
 */
public class PedidosViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final int TOTAL = 50;

    private static Pedido pedido(long idLocal, EstadoPedido estado) {
        return new Pedido(idLocal, (int) idLocal, "2026-08-01T10:00:00Z", estado, 3, "Ana",
                150.0, 2, "auth-1", "2026-08-01T10:00:00Z", EstadoSync.SINCRONIZADO);
    }

    private static List<Pedido> cincuentaPedidos() {
        List<Pedido> lista = new ArrayList<>();
        for (int i = 1; i <= TOTAL; i++) {
            EstadoPedido estado =
                    i % 2 == 0 ? EstadoPedido.EN_PREPARACION : EstadoPedido.PENDIENTE;
            lista.add(pedido(i, estado));
        }
        return lista;
    }

    private PedidosViewModel viewModelCon(FakePedidoRepository repositorio) {
        return viewModelCon(repositorio, "admin");
    }

    private PedidosViewModel viewModelCon(FakePedidoRepository repositorio,
                                          @Nullable String rol) {
        PedidosViewModel viewModel =
                new PedidosViewModel(repositorio, new ExecutorServiceSincrono(), rol);
        viewModel.getEstado().observeForever(ignorado -> { });
        return viewModel;
    }

    // ------------------------------------------------------------------ carga inicial

    @Test
    public void alActivarLasFuentes_muestraSoloLaVentanaInicial() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);

        EstadoPedidos estado = viewModel.getEstado().getValue();
        assertFalse(estado.isCargando());
        assertNull(estado.getError());
        assertEquals(20, estado.getPedidos().size());
        assertEquals(20, repositorio.ultimaVentana);
        assertTrue(estado.isHayMas());
    }

    @Test
    public void sinPedidos_quedaVacio() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        repositorio.todos.clear();
        repositorio.total.setValue(0);
        PedidosViewModel viewModel = viewModelCon(repositorio);

        EstadoPedidos estado = viewModel.getEstado().getValue();
        assertTrue(estado.isVacio());
        assertFalse(estado.isHayMas());
    }

    @Test
    public void construir_sincronizaUnaVezSinEsperarElPullToRefresh() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        viewModelCon(repositorio);

        assertEquals(1, repositorio.vecesSincronizo);
    }

    @Test
    public void sincronizar_pideAlRepositorio() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);
        repositorio.vecesSincronizo = 0; // descarta el sync-on-launch del constructor

        viewModel.sincronizar();

        assertEquals(1, repositorio.vecesSincronizo);
    }

    // ------------------------------------------------------------------ paginación (B9, B10)

    @Test
    public void cargarMas_sumaVeinteALaVentana() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);

        viewModel.cargarMas();

        assertEquals(40, viewModel.getEstado().getValue().getPedidos().size());
        assertEquals(40, repositorio.ultimaVentana);
        assertTrue(viewModel.getEstado().getValue().isHayMas());
    }

    @Test
    public void cargarMas_hastaSuperarElTotal_apagaHayMas() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);

        viewModel.cargarMas();
        viewModel.cargarMas();

        assertEquals(50, viewModel.getEstado().getValue().getPedidos().size());
        assertEquals(60, viewModel.getEstado().getValue().getVentana());
        assertFalse(viewModel.getEstado().getValue().isHayMas());
    }

    @Test
    public void hayMas_seDerivaDelTotalNoDeLaListaVisible() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);

        // El total crece a 25 pero la ventana sigue en 20: hayMas sigue cierto.
        repositorio.total.setValue(25);

        assertTrue(viewModel.getEstado().getValue().isHayMas());
    }

    @Test
    public void siElTotalBajaDeLaVentana_hayMasSeApagaSolo() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);

        repositorio.total.setValue(5);

        assertFalse(viewModel.getEstado().getValue().isHayMas());
    }

    // ------------------------------------------------------------------ filtros

    @Test
    public void filtrarPorEstado_dejaSoloElFiltrado() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);

        viewModel.filtrarPorEstado(EstadoPedido.PENDIENTE);

        for (Pedido pedido : viewModel.getEstado().getValue().getPedidos()) {
            assertEquals(EstadoPedido.PENDIENTE, pedido.getEstado());
        }
        assertEquals(EstadoPedido.PENDIENTE, viewModel.getEstado().getValue().getFiltro());
    }

    @Test
    public void filtrarPorEstado_null_muestraTodos() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorEstado(EstadoPedido.PENDIENTE);

        viewModel.filtrarPorEstado(null);

        assertEquals(20, viewModel.getEstado().getValue().getPedidos().size());
        assertNull(viewModel.getEstado().getValue().getFiltro());
    }

    @Test
    public void elFiltroNoCambiaHayMas_seSigueDerivandoDelTotal() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);

        viewModel.filtrarPorEstado(EstadoPedido.EN_PREPARACION);

        assertTrue(viewModel.getEstado().getValue().isHayMas());
        assertTrue(viewModel.getEstado().getValue().getPedidos().size() < 20);
    }

    @Test
    public void losEstadosDelCatalogo_alimentanLosChips() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        repositorio.estados.setValue(Arrays.asList(EstadoPedido.PENDIENTE,
                EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO));
        PedidosViewModel viewModel = viewModelCon(repositorio);

        assertEquals(3, viewModel.getEstados().size());
        assertEquals(EstadoPedido.PENDIENTE, viewModel.getEstados().get(0));
    }

    // ------------------------------------------------------------------ avanzar estado

    @Test
    public void avanzarEstado_pideAlRepositorioYPublicaExito() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);

        viewModel.avanzarEstado(pedido(1, EstadoPedido.PENDIENTE));

        assertEquals(1L, repositorio.ultimoIdLocal);
        assertEquals(EstadoPedido.EN_PREPARACION, repositorio.ultimoEstado);
        assertEquals("Pedido avanzado.", viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void avanzarEstado_sinPermisoDelRol_publicaError() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio, "cocina");

        // Cocina no marca Entregado: LISTO → ENTREGADO es solo de mesero.
        viewModel.avanzarEstado(pedido(1, EstadoPedido.LISTO));

        assertEquals(0L, repositorio.ultimoIdLocal);
        assertEquals("Tu rol no puede cambiar ese pedido a ese estado.",
                viewModel.getEstado().getValue().getError());
    }

    @Test
    public void avanzarEstado_pedidoCerrado_noAvanza() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);

        viewModel.avanzarEstado(pedido(1, EstadoPedido.ENTREGADO));

        assertEquals(0L, repositorio.ultimoIdLocal);
        assertNull(viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void avanzarEstado_conErrorDelRepositorio_publicaError() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        repositorio.resultadoOperacion = Result.fail("No se encontró el pedido.");
        PedidosViewModel viewModel = viewModelCon(repositorio);

        viewModel.avanzarEstado(pedido(1, EstadoPedido.PENDIENTE));

        assertEquals("No se encontró el pedido.", viewModel.getEstado().getValue().getError());
    }

    @Test
    public void avanzarEstado_mesero_listoAEntregado_siPuede() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio, "mesero");

        viewModel.avanzarEstado(pedido(1, EstadoPedido.LISTO));

        assertEquals(EstadoPedido.ENTREGADO, repositorio.ultimoEstado);
        assertEquals("Pedido avanzado.", viewModel.getEstado().getValue().getMensajeExito());
    }

    // ------------------------------------------------------------------ cancelar

    @Test
    public void cancelar_comoAdmin_publicaExito() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio, "admin");

        viewModel.cancelar(pedido(1, EstadoPedido.PENDIENTE));

        assertEquals(EstadoPedido.CANCELADO, repositorio.ultimoEstado);
        assertEquals("Pedido cancelado.", viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void cancelar_comoMesero_publicaError() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio, "mesero");

        viewModel.cancelar(pedido(1, EstadoPedido.PENDIENTE));

        assertEquals(0L, repositorio.ultimoIdLocal);
        assertEquals("Solo el administrador puede cancelar pedidos.",
                viewModel.getEstado().getValue().getError());
    }

    // ------------------------------------------------------------------ sincronización

    @Test
    public void alIniciarSincronizacion_muestraSincronizando() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);

        repositorio.sincronizacion.setValue(new EstadoSincronizacion(true, null));

        assertTrue(viewModel.getEstado().getValue().isSincronizando());
    }

    @Test
    public void alTerminarConError_publicaElUltimoError() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);

        repositorio.sincronizacion.setValue(
                new EstadoSincronizacion(false, "Fallo de red."));

        assertFalse(viewModel.getEstado().getValue().isSincronizando());
        assertEquals("Fallo de red.", viewModel.getEstado().getValue().getUltimoErrorSync());
    }

    // ------------------------------------------------------------------ mensajes

    @Test
    public void onMensajeConsumido_limpiaElMensaje() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        PedidosViewModel viewModel = viewModelCon(repositorio);
        viewModel.avanzarEstado(pedido(1, EstadoPedido.PENDIENTE));

        viewModel.onMensajeConsumido();

        assertNull(viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void onErrorConsumido_limpiaElError() {
        FakePedidoRepository repositorio = new FakePedidoRepository();
        repositorio.resultadoOperacion = Result.fail("No se encontró el pedido.");
        PedidosViewModel viewModel = viewModelCon(repositorio);
        viewModel.avanzarEstado(pedido(1, EstadoPedido.PENDIENTE));

        viewModel.onErrorConsumido();

        assertNull(viewModel.getEstado().getValue().getError());
    }

    // ------------------------------------------------------------------ fake

    /** Fake de {@link PedidoRepository}: ventana cortada sobre una lista en memoria. */
    private static final class FakePedidoRepository implements PedidoRepository {

        final List<Pedido> todos = new ArrayList<>(cincuentaPedidos());
        final MutableLiveData<List<Pedido>> lista = new MutableLiveData<>();
        final MutableLiveData<Integer> total = new MutableLiveData<>(TOTAL);
        final MutableLiveData<List<EstadoPedido>> estados = new MutableLiveData<>(
                Arrays.asList(EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION,
                        EstadoPedido.LISTO, EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO));
        final MutableLiveData<EstadoSincronizacion> sincronizacion =
                new MutableLiveData<>(new EstadoSincronizacion(false, null));

        Result<Void> resultadoOperacion = Result.ok(null);

        int vecesSincronizo = 0;
        int ultimaVentana;
        long ultimoIdLocal;
        EstadoPedido ultimoEstado;

        @Override
        public LiveData<List<Pedido>> observarVentana(int ventana) {
            ultimaVentana = ventana;
            List<Pedido> cortada = todos.size() <= ventana
                    ? todos : new ArrayList<>(todos.subList(0, ventana));
            lista.setValue(cortada);
            return lista;
        }

        @Override
        public LiveData<Integer> contarPedidos() {
            return total;
        }

        @Override
        public LiveData<List<EstadoPedido>> observarEstadosPedido() {
            return estados;
        }

        @Override
        public LiveData<EstadoSincronizacion> getEstadoSincronizacion() {
            return sincronizacion;
        }

        @Override
        public void sincronizar() {
            vecesSincronizo++;
        }

        @Override
        public Result<Void> avanzarEstado(long idLocal, EstadoPedido nuevo) {
            ultimoIdLocal = idLocal;
            ultimoEstado = nuevo;
            return resultadoOperacion;
        }

        Result<Long> resultadoCrear = Result.ok(1L);
        NuevoPedido ultimoNuevo;

        @Override
        public Result<Long> crear(NuevoPedido nuevo) {
            ultimoNuevo = nuevo;
            return resultadoCrear;
        }

        @Override
        public LiveData<List<LineaPedido>> observarDetalle(long idLocal) {
            return new MutableLiveData<>();
        }
    }

    /**
     * Ejecuta cada tarea en el hilo que la envía. Junto con {@link InstantTaskExecutorRule},
     * el resultado queda disponible apenas retorna la llamada al ViewModel.
     */
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

package com.example.proyectofinalrestaurante.ui.nuevopedido;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.ValidadorPedido;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.LineaPedido;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;
import com.example.proyectofinalrestaurante.domain.model.NuevoPedido;
import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Pedido;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.domain.model.TipoPedido;
import com.example.proyectofinalrestaurante.domain.repository.ClienteRepository;
import com.example.proyectofinalrestaurante.domain.repository.MenuRepository;
import com.example.proyectofinalrestaurante.domain.repository.MesaRepository;
import com.example.proyectofinalrestaurante.domain.repository.PedidoRepository;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests de {@link NuevoPedidoViewModel} (Plan Fase 3b, E7). Mismo patrón que
 * {@code PedidosViewModelTest}: los cuatro repositorios son fakes sin Room ni Retrofit que
 * alimentan {@code MutableLiveData} a mano, y el {@link ExecutorService} inyectado corre las
 * tareas de forma síncrona para que el resultado quede disponible apenas retorna la llamada.
 */
public class NuevoPedidoViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    // ------------------------------------------------------------------ helpers

    private static Platillo platillo(int idLocal, @Nullable Integer idServidor, String nombre) {
        return new Platillo(idLocal, idServidor, nombre, null, 100.0, 1, null, null,
                true, EstadoSync.SINCRONIZADO);
    }

    private static Mesa mesa(int idLocal, int numero) {
        return new Mesa(idLocal, idLocal, numero, 4, null, EstadoMesa.LIBRE, true,
                null, EstadoSync.SINCRONIZADO);
    }

    private static Cliente cliente(int idLocal, String nombre) {
        return new Cliente(idLocal, idLocal, nombre, "Apellido", null, null, true, 0,
                null, EstadoSync.SINCRONIZADO);
    }

    private static Fakes fakes() {
        Fakes f = new Fakes();
        f.menu.lista.add(platillo(1, 100, "Platillo A"));
        f.menu.lista.add(platillo(2, 101, "Platillo B"));
        f.mesas.lista.add(mesa(1, 1));
        f.mesas.lista.add(mesa(2, 2));
        f.clientes.lista.add(cliente(1, "Ana"));
        return f;
    }

    private static NuevoPedidoViewModel verCon(Fakes fakes, @Nullable String rol) {
        NuevoPedidoViewModel viewModel = new NuevoPedidoViewModel(
                fakes.pedidos, fakes.menu, fakes.mesas, fakes.clientes,
                new ExecutorServiceSincrono(), rol);
        viewModel.getEstado().observeForever(ignorado -> { });
        return viewModel;
    }

    // ------------------------------------------------------------------ estado inicial

    @Test
    public void estadoInicial_carritoVacioSinConfirmarTipoEnMesaYCargando() {
        // Sin observador: las fuentes todavía no emiten, así que se ve el estado inicial.
        NuevoPedidoViewModel viewModel = new NuevoPedidoViewModel(
                new FakePedidoRepository(), new FakeMenuRepository(), new FakeMesaRepository(),
                new FakeClienteRepository(), new ExecutorServiceSincrono(), "mesero");

        EstadoNuevoPedido estado = viewModel.getEstado().getValue();
        assertTrue(estado.isCargando());
        assertTrue(estado.isCarritoVacio());
        assertFalse(estado.isPuedeConfirmar());
        assertEquals(TipoPedido.EN_MESA, estado.getTipoPedido());
        assertNull(estado.getIdLocalMesa());
        assertNull(estado.getIdLocalCliente());
    }

    // ------------------------------------------------------------------ carrito

    @Test
    public void agregarPlatillo_dosVecesElMismo_unaSolaLineaConCantidadDos() {
        Fakes fakes = fakes();
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");

        viewModel.agregarPlatillo(fakes.menu.lista.get(0));
        viewModel.agregarPlatillo(fakes.menu.lista.get(0));

        EstadoNuevoPedido estado = viewModel.getEstado().getValue();
        assertEquals(1, estado.getCarrito().getLineas().size());
        assertEquals(2, estado.getCarrito().getLineas().get(0).getCantidad());
        assertTrue(estado.isPuedeConfirmar());
    }

    @Test
    public void agregarPlatillo_distintos_creaDosLineas() {
        Fakes fakes = fakes();
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");

        viewModel.agregarPlatillo(fakes.menu.lista.get(0));
        viewModel.agregarPlatillo(fakes.menu.lista.get(1));

        assertEquals(2, viewModel.getEstado().getValue().getCarrito().getLineas().size());
    }

    @Test
    public void cambiarCantidad_aCero_eliminaLaLinea() {
        Fakes fakes = fakes();
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");
        viewModel.agregarPlatillo(fakes.menu.lista.get(0));
        viewModel.agregarPlatillo(fakes.menu.lista.get(0));

        viewModel.cambiarCantidad(fakes.menu.lista.get(0).getIdLocal(), 0);

        assertTrue(viewModel.getEstado().getValue().isCarritoVacio());
        assertFalse(viewModel.getEstado().getValue().isPuedeConfirmar());
    }

    @Test
    public void quitarPlatillo_quitaSoloEsaLinea() {
        Fakes fakes = fakes();
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");
        viewModel.agregarPlatillo(fakes.menu.lista.get(0));
        viewModel.agregarPlatillo(fakes.menu.lista.get(1));

        viewModel.quitarPlatillo(fakes.menu.lista.get(0).getIdLocal());

        List<com.example.proyectofinalrestaurante.domain.model.LineaCarrito> lineas =
                viewModel.getEstado().getValue().getCarrito().getLineas();
        assertEquals(1, lineas.size());
        assertEquals("Platillo B", lineas.get(0).getNombre());
    }

    // ------------------------------------------------------------------ confirmar

    @Test
    public void confirmar_carritoValido_creaConElCarritoYTipoYPublicaExito() {
        Fakes fakes = fakes();
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");
        viewModel.agregarPlatillo(fakes.menu.lista.get(0));

        viewModel.confirmar("Pedido #%1$d creado.");

        assertNotNull(fakes.pedidos.ultimoNuevo);
        assertEquals(TipoPedido.EN_MESA, fakes.pedidos.ultimoNuevo.getTipoPedido());
        assertEquals(1, fakes.pedidos.ultimoNuevo.getCarrito().getLineas().size());
        assertNotNull(fakes.pedidos.ultimoNuevo.getClaveIdempotencia());
        assertFalse(fakes.pedidos.ultimoNuevo.getClaveIdempotencia().isEmpty());
        assertEquals("Pedido #1 creado.", viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void confirmar_carritoVacio_errorDeValidacionYNoLlamaACrear() {
        Fakes fakes = fakes();
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");

        viewModel.confirmar("Pedido #%1$d creado.");

        assertEquals(ValidadorPedido.ErrorPedido.CARRITO_VACIO,
                viewModel.getEstado().getValue().getErrorDeValidacion());
        assertNull(fakes.pedidos.ultimoNuevo);
    }

    @Test
    public void confirmar_cincuentaYUnaLineas_errorDeDemasiadasLineas() {
        Fakes fakes = fakes();
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");
        for (int i = 0; i < 51; i++) {
            viewModel.agregarPlatillo(platillo(1000 + i, 2000 + i, "P" + i));
        }

        viewModel.confirmar("Pedido #%1$d creado.");

        assertEquals(ValidadorPedido.ErrorPedido.DEMASIADAS_LINEAS,
                viewModel.getEstado().getValue().getErrorDeValidacion());
        assertNull(fakes.pedidos.ultimoNuevo);
        assertFalse(viewModel.getEstado().getValue().isPuedeConfirmar());
    }

    @Test
    public void confirmar_conErrorDelRepositorio_publicaError() {
        Fakes fakes = fakes();
        fakes.pedidos.resultadoCrear = Result.fail("No se pudo crear el pedido.");
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");
        viewModel.agregarPlatillo(fakes.menu.lista.get(0));

        viewModel.confirmar("Pedido #%1$d creado.");

        assertEquals("No se pudo crear el pedido.",
                viewModel.getEstado().getValue().getError());
        assertNull(viewModel.getEstado().getValue().getMensajeExito());
    }

    // ------------------------------------------------------------------ selectores

    @Test
    public void seleccionarTipoParaLlevar_loReflejaElEstadoYElPedidoConfirmado() {
        Fakes fakes = fakes();
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");
        viewModel.agregarPlatillo(fakes.menu.lista.get(0));

        viewModel.seleccionarTipo(TipoPedido.PARA_LLEVAR);
        viewModel.confirmar("Pedido #%1$d creado.");

        assertEquals(TipoPedido.PARA_LLEVAR, viewModel.getEstado().getValue().getTipoPedido());
        assertEquals(TipoPedido.PARA_LLEVAR, fakes.pedidos.ultimoNuevo.getTipoPedido());
    }

    @Test
    public void seleccionarMesaYCliente_loReflejanElEstadoYElPedidoConfirmado() {
        Fakes fakes = fakes();
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");
        viewModel.agregarPlatillo(fakes.menu.lista.get(0));

        viewModel.seleccionarMesa(2);
        viewModel.seleccionarCliente(1);
        viewModel.confirmar("Pedido #%1$d creado.");

        assertEquals(Integer.valueOf(2), viewModel.getEstado().getValue().getIdLocalMesa());
        assertEquals(Integer.valueOf(1), viewModel.getEstado().getValue().getIdLocalCliente());
        assertEquals(Integer.valueOf(2), fakes.pedidos.ultimoNuevo.getIdLocalMesa());
        assertEquals(Integer.valueOf(1), fakes.pedidos.ultimoNuevo.getIdLocalCliente());
    }

    // ------------------------------------------------------------------ permisos

    @Test
    public void sinPermisoDeRol_puedeTomarPedidoEsFalso() {
        assertEquals(false, verCon(fakes(), null).getEstado().getValue().isPuedeTomarPedido());
        assertEquals(false, verCon(fakes(), "cocina").getEstado().getValue().isPuedeTomarPedido());
    }

    @Test
    public void conPermisoDeRol_puedeTomarPedidoEsVerdadero() {
        assertEquals(true, verCon(fakes(), "mesero").getEstado().getValue().isPuedeTomarPedido());
        assertEquals(true, verCon(fakes(), "admin").getEstado().getValue().isPuedeTomarPedido());
    }

    // ------------------------------------------------------------------ filtro del selector

    @Test
    public void laListaDePlatillosQuedaFiltradaPorPuedePedirse() {
        Fakes fakes = new Fakes();
        fakes.menu.lista.add(platillo(1, 100, "Permitido"));
        fakes.menu.lista.add(platillo(2, null, "Sin servidor"));
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");

        assertEquals(1, viewModel.getEstado().getValue().getPlatillos().size());
        assertEquals("Permitido", viewModel.getEstado().getValue().getPlatillos().get(0).getNombre());
    }

    // ------------------------------------------------------------------ mensajes

    @Test
    public void onMensajeConsumido_limpiaElMensaje() {
        Fakes fakes = fakes();
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");
        viewModel.agregarPlatillo(fakes.menu.lista.get(0));
        viewModel.confirmar("Pedido #%1$d creado.");

        viewModel.onMensajeConsumido();

        assertNull(viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void onErrorConsumido_limpiaElError() {
        Fakes fakes = fakes();
        fakes.pedidos.resultadoCrear = Result.fail("No se pudo crear el pedido.");
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");
        viewModel.agregarPlatillo(fakes.menu.lista.get(0));
        viewModel.confirmar("Pedido #%1$d creado.");

        viewModel.onErrorConsumido();

        assertNull(viewModel.getEstado().getValue().getError());
    }

    @Test
    public void onErrorDeValidacionConsumido_limpiaElErrorDeValidacion() {
        Fakes fakes = fakes();
        NuevoPedidoViewModel viewModel = verCon(fakes, "mesero");
        viewModel.confirmar("Pedido #%1$d creado.");

        viewModel.onErrorDeValidacionConsumido();

        assertNull(viewModel.getEstado().getValue().getErrorDeValidacion());
    }

    // ------------------------------------------------------------------ executor inyectado

    @Test
    public void confirmar_ejecutaEnElExecutorInyectado_noEnElHiloDeLlamada() {
        Fakes fakes = fakes();
        Thread hiloDeLaPrueba = Thread.currentThread();
        NuevoPedidoViewModel viewModel = new NuevoPedidoViewModel(
                fakes.pedidos, fakes.menu, fakes.mesas, fakes.clientes,
                new ExecutorServiceEnOtroHilo(), "mesero");
        viewModel.getEstado().observeForever(ignorado -> { });
        viewModel.agregarPlatillo(fakes.menu.lista.get(0));

        viewModel.confirmar("Pedido #%1$d creado.");

        assertNotNull(fakes.pedidos.ultimoNuevo);
        assertNotNull(fakes.pedidos.hiloDeCrear);
        assertNotEquals(hiloDeLaPrueba, fakes.pedidos.hiloDeCrear);
    }

    // ------------------------------------------------------------------ fakes

    private static final class Fakes {
        final FakePedidoRepository pedidos = new FakePedidoRepository();
        final FakeMenuRepository menu = new FakeMenuRepository();
        final FakeMesaRepository mesas = new FakeMesaRepository();
        final FakeClienteRepository clientes = new FakeClienteRepository();
    }

    /** Fake de {@link PedidoRepository}: solo {@code crear} tiene comportamiento. */
    private static final class FakePedidoRepository implements PedidoRepository {

        final MutableLiveData<List<Pedido>> lista = new MutableLiveData<>();
        final MutableLiveData<Integer> total = new MutableLiveData<>(0);
        final MutableLiveData<List<EstadoPedido>> estados = new MutableLiveData<>();
        final MutableLiveData<EstadoSincronizacion> sincronizacion =
                new MutableLiveData<>(new EstadoSincronizacion(false, null));

        Result<Long> resultadoCrear = Result.ok(1L);
        NuevoPedido ultimoNuevo;
        Thread hiloDeCrear;

        @Override
        public LiveData<List<Pedido>> observarVentana(int ventana) {
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
        }

        @Override
        public Result<Void> avanzarEstado(long idLocal, EstadoPedido nuevo) {
            return Result.ok(null);
        }

        @Override
        public Result<Long> crear(NuevoPedido nuevo) {
            hiloDeCrear = Thread.currentThread();
            ultimoNuevo = nuevo;
            return resultadoCrear;
        }

        @Override
        public LiveData<List<LineaPedido>> observarDetalle(long idLocal) {
            return new MutableLiveData<>();
        }
    }

    /** Fake de {@link MenuRepository}: la lista de platillos se siembra por test. */
    private static final class FakeMenuRepository implements MenuRepository {

        final List<Platillo> lista = new ArrayList<>();
        final MutableLiveData<List<Platillo>> platillos = new MutableLiveData<>();

        @Override
        public LiveData<List<Platillo>> observarPlatillos() {
            platillos.setValue(new ArrayList<>(lista));
            return platillos;
        }

        @Override
        public LiveData<List<Categoria>> observarCategorias() {
            return new MutableLiveData<>();
        }

        @Override
        public LiveData<EstadoSincronizacion> getEstadoSincronizacion() {
            return new MutableLiveData<>(new EstadoSincronizacion(false, null));
        }

        @Override
        public void sincronizar() {
        }

        @Override
        public Result<Long> crearPlatillo(NuevoPlatillo nuevo, @Nullable ImagenPlatillo imagen) {
            return Result.ok(1L);
        }

        @Override
        public Result<Void> actualizarPlatillo(int idLocal, NuevoPlatillo datos,
                                               @Nullable ImagenPlatillo imagenNueva) {
            return Result.ok(null);
        }

        @Override
        public Result<Void> quitarImagen(int idLocal) {
            return Result.ok(null);
        }

        @Override
        public Result<Void> cambiarEstadoPlatillo(int idLocal, boolean activo) {
            return Result.ok(null);
        }

        @Override
        public Result<Long> crearCategoria(String descripcion) {
            return Result.ok(1L);
        }

        @Override
        public Result<Void> renombrarCategoria(int idLocal, String descripcion) {
            return Result.ok(null);
        }

        @Override
        public Result<Void> cambiarEstadoCategoria(int idLocal, boolean activo) {
            return Result.ok(null);
        }

        @Override
        public Result<Void> borrarCategoria(int idLocal) {
            return Result.ok(null);
        }
    }

    /** Fake de {@link MesaRepository}: la lista de mesas se siembra por test. */
    private static final class FakeMesaRepository implements MesaRepository {

        final List<Mesa> lista = new ArrayList<>();
        final MutableLiveData<List<Mesa>> mesas = new MutableLiveData<>();

        @Override
        public LiveData<List<Mesa>> observarMesas() {
            mesas.setValue(new ArrayList<>(lista));
            return mesas;
        }

        @Override
        public LiveData<List<EstadoMesa>> observarEstadosMesa() {
            return new MutableLiveData<>();
        }

        @Override
        public LiveData<EstadoSincronizacion> getEstadoSincronizacion() {
            return new MutableLiveData<>(new EstadoSincronizacion(false, null));
        }

        @Override
        public void sincronizar() {
        }

        @Override
        public Result<Long> crearMesa(NuevaMesa nueva) {
            return Result.ok(1L);
        }

        @Override
        public Result<Void> actualizarMesa(int idLocal, NuevaMesa datos) {
            return Result.ok(null);
        }

        @Override
        public Result<Void> cambiarEstadoMesa(int idLocal, EstadoMesa nuevoEstado) {
            return Result.ok(null);
        }

        @Override
        public Result<Void> cambiarBajaMesa(int idLocal, boolean activo) {
            return Result.ok(null);
        }
    }

    /** Fake de {@link ClienteRepository}: la lista de clientes se siembra por test. */
    private static final class FakeClienteRepository implements ClienteRepository {

        final List<Cliente> lista = new ArrayList<>();
        final MutableLiveData<List<Cliente>> clientes = new MutableLiveData<>();

        @Override
        public LiveData<List<Cliente>> observarClientes() {
            clientes.setValue(new ArrayList<>(lista));
            return clientes;
        }

        @Override
        public LiveData<EstadoSincronizacion> getEstadoSincronizacion() {
            return new MutableLiveData<>(new EstadoSincronizacion(false, null));
        }

        @Override
        public void sincronizar() {
        }

        @Override
        public Result<Long> crearCliente(NuevoCliente nuevo) {
            return Result.ok(1L);
        }

        @Override
        public Result<Void> actualizarCliente(int idLocal, NuevoCliente datos) {
            return Result.ok(null);
        }

        @Override
        public Result<Void> cambiarBajaCliente(int idLocal, boolean activo) {
            return Result.ok(null);
        }

        @Override
        public Result<Void> borrarCliente(int idLocal) {
            return Result.ok(null);
        }

        @Override
        public Result<Integer> buscarOCrearCliente(String nombre, String apellido,
                                                   @Nullable String identidad,
                                                   @Nullable String telefono) {
            return Result.ok(1);
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

    /**
     * Corre cada tarea en un {@link Thread} nuevo y espera a que termine: prueba que el ViewModel
     * delega la confirmación al {@link ExecutorService} inyectado y no la corre inline.
     */
    private static final class ExecutorServiceEnOtroHilo extends AbstractExecutorService {

        private volatile boolean cerrado = false;

        @Override
        public void execute(Runnable command) {
            Thread hilo = new Thread(command);
            hilo.start();
            try {
                hilo.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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

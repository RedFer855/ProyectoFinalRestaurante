package com.example.proyectofinalrestaurante.ui.clientes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;
import com.example.proyectofinalrestaurante.domain.repository.ClienteRepository;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests de {@link ClientesViewModel} (Plan Fase 2d, E7). Mismo patrón que
 * {@code MesasViewModelTest}: el repositorio es un fake sin Room ni Retrofit.
 */
public class ClientesViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static Cliente cliente(int idLocal, String nombre, String apellido, boolean activo) {
        return new Cliente(idLocal, idLocal, nombre, apellido, null, null, activo, 0,
                "2026-08-01", EstadoSync.SINCRONIZADO);
    }

    private static Cliente clienteConEstadoSync(int idLocal, String nombre, EstadoSync estadoSync) {
        return new Cliente(idLocal, idLocal, nombre, "Apellido", null, null, true, 0,
                "2026-08-01", estadoSync);
    }

    private static List<Cliente> clientes() {
        return Arrays.asList(
                cliente(1, "Ana", "Cruz", true),
                cliente(2, "Luis", "Medina", true),
                cliente(3, "Sofía", "Ramos", false));
    }

    private ClientesViewModel viewModelCon(FakeClienteRepository repositorio) {
        ClientesViewModel viewModel = new ClientesViewModel(repositorio, new ExecutorServiceSincrono());
        viewModel.getEstado().observeForever(ignorado -> { });
        return viewModel;
    }

    // ------------------------------------------------------------------ datos iniciales

    @Test
    public void alActivarLasFuentes_muestraLosClientes() {
        FakeClienteRepository repositorio = new FakeClienteRepository();

        ClientesViewModel viewModel = viewModelCon(repositorio);

        EstadoClientes estado = viewModel.getEstado().getValue();
        assertFalse(estado.isCargando());
        assertNull(estado.getError());
        assertEquals(3, estado.getClientes().size());
        assertFalse(estado.isVacio());
    }

    @Test
    public void sinClientes_quedaVacioPeroNoPorFiltro() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        repositorio.clientes.setValue(new ArrayList<>());

        ClientesViewModel viewModel = viewModelCon(repositorio);

        EstadoClientes estado = viewModel.getEstado().getValue();
        assertTrue(estado.isVacio());
        assertFalse(estado.isVacioPorFiltro());
    }

    @Test
    public void cambiosSinSubir_cuentaFilasQueNoEstanSincronizadas() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        repositorio.clientes.setValue(Arrays.asList(
                clienteConEstadoSync(1, "Ana", EstadoSync.SINCRONIZADO),
                clienteConEstadoSync(2, "Luis", EstadoSync.PENDIENTE),
                clienteConEstadoSync(3, "Sofía", EstadoSync.ERROR)));

        ClientesViewModel viewModel = viewModelCon(repositorio);

        assertEquals(2, viewModel.getEstado().getValue().getCambiosSinSubir());
    }

    @Test
    public void todosLosClientes_esCopiaCompletaSinFiltrar() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        ClientesViewModel viewModel = viewModelCon(repositorio);
        viewModel.buscar("ana");

        assertEquals(3, viewModel.getTodosLosClientes().size());
        assertEquals(1, viewModel.getEstado().getValue().getClientes().size());
    }

    // ------------------------------------------------------------------ filtros y búsqueda

    @Test
    public void filtrarPorActivo_dejaSoloLosActivos() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        ClientesViewModel viewModel = viewModelCon(repositorio);

        viewModel.filtrarPorActivo(true);

        assertEquals(2, viewModel.getEstado().getValue().getClientes().size());
        assertEquals(Boolean.TRUE, viewModel.getEstado().getValue().getFiltroActivo());
    }

    @Test
    public void filtrarPorInactivos_dejaSoloLosDeBaja() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        ClientesViewModel viewModel = viewModelCon(repositorio);

        viewModel.filtrarPorActivo(false);

        EstadoClientes estado = viewModel.getEstado().getValue();
        assertEquals(1, estado.getClientes().size());
        assertEquals("Sofía", estado.getClientes().get(0).getNombre());
    }

    @Test
    public void buscar_porNombreOApellido() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        ClientesViewModel viewModel = viewModelCon(repositorio);

        viewModel.buscar("medina");

        assertEquals(1, viewModel.getEstado().getValue().getClientes().size());
    }

    @Test
    public void buscar_sinCoincidencias_quedaVacioPorFiltro() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        ClientesViewModel viewModel = viewModelCon(repositorio);

        viewModel.buscar("nadie");

        EstadoClientes estado = viewModel.getEstado().getValue();
        assertTrue(estado.isVacio());
        assertTrue(estado.isVacioPorFiltro());
    }

    // ------------------------------------------------------------------ baja/reactivación

    @Test
    public void cambiarBaja_siElFiltroLoEsconderia_loSuelta() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        ClientesViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorActivo(true);

        // Ana estaba activa (visible con el filtro) y se da de baja.
        viewModel.cambiarBaja(cliente(1, "Ana", "Cruz", true), false);

        // Con el filtro "Activos" puesto, Ana desaparecería de la pantalla y se leería como
        // que la baja no se guardó (Plan Fase 2d, §5.5).
        assertNull(viewModel.getEstado().getValue().getFiltroActivo());
    }

    @Test
    public void cambiarBaja_siSigueCoincidiendoConElFiltro_loConserva() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        ClientesViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorActivo(true);

        // Luis sigue activo después de "reactivarlo" (ya lo estaba): el filtro no estorba.
        viewModel.cambiarBaja(cliente(2, "Luis", "Medina", true), true);

        assertEquals(Boolean.TRUE, viewModel.getEstado().getValue().getFiltroActivo());
    }

    // ------------------------------------------------------------------ crear/editar/borrar

    @Test
    public void crear_exitoso_anunciaYMantieneElMensajeDeUnSoloDisparo() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        ClientesViewModel viewModel = viewModelCon(repositorio);

        viewModel.crear(new NuevoCliente("Gabriela", "Paz", null, null));

        assertEquals("Cliente registrado.", viewModel.getEstado().getValue().getMensajeExito());
        assertEquals("Gabriela", repositorio.ultimoNuevo.getNombre());

        viewModel.onMensajeConsumido();

        assertNull(viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void crear_rechazado_publicaElError() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        repositorio.resultadoCrear = Result.fail("La identidad no es válida");
        ClientesViewModel viewModel = viewModelCon(repositorio);

        viewModel.crear(new NuevoCliente("Ana", "Cruz", "123", null));

        assertEquals("La identidad no es válida", viewModel.getEstado().getValue().getError());
    }

    @Test
    public void actualizar_pasaLosDatosAlRepositorio() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        ClientesViewModel viewModel = viewModelCon(repositorio);

        viewModel.actualizar(1, new NuevoCliente("Ana", "Cruz Reyes", null, "9999-0000"));

        assertEquals(1, repositorio.ultimoIdLocal);
        assertEquals("Cruz Reyes", repositorio.ultimoNuevo.getApellido());
        assertEquals("Cliente actualizado.", viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void borrar_exitoso_anunciaElBorrado() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        ClientesViewModel viewModel = viewModelCon(repositorio);

        viewModel.borrar(cliente(1, "Ana", "Cruz", true));

        assertEquals(1, repositorio.ultimoIdLocal);
        assertEquals("Cliente borrado.", viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void borrar_conPedidos_publicaElError() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        repositorio.resultadoOperacion = Result.fail("Ese cliente tiene pedidos.");
        ClientesViewModel viewModel = viewModelCon(repositorio);

        viewModel.borrar(cliente(1, "Ana", "Cruz", true));

        assertEquals("Ese cliente tiene pedidos.", viewModel.getEstado().getValue().getError());
    }

    @Test
    public void sincronizar_pideAlRepositorio() {
        FakeClienteRepository repositorio = new FakeClienteRepository();
        ClientesViewModel viewModel = viewModelCon(repositorio);

        viewModel.sincronizar();

        assertEquals(1, repositorio.vecesSincronizo);
    }

    /** Fake de {@link ClienteRepository}: LiveData en memoria, escrituras que solo anotan. */
    private static final class FakeClienteRepository implements ClienteRepository {

        final MutableLiveData<List<Cliente>> clientes = new MutableLiveData<>(clientes());
        final MutableLiveData<EstadoSincronizacion> sincronizacion =
                new MutableLiveData<>(new EstadoSincronizacion(false, null));

        Result<Long> resultadoCrear = Result.ok(9L);
        Result<Void> resultadoOperacion = Result.ok(null);
        Result<Integer> resultadoBuscarOCrear = Result.ok(1);

        int vecesSincronizo = 0;
        int ultimoIdLocal;
        NuevoCliente ultimoNuevo;

        @Override
        public LiveData<List<Cliente>> observarClientes() {
            return clientes;
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
        public Result<Long> crearCliente(NuevoCliente nuevo) {
            ultimoNuevo = nuevo;
            return resultadoCrear;
        }

        @Override
        public Result<Void> actualizarCliente(int idLocal, NuevoCliente datos) {
            ultimoIdLocal = idLocal;
            ultimoNuevo = datos;
            return resultadoOperacion;
        }

        @Override
        public Result<Void> cambiarBajaCliente(int idLocal, boolean activo) {
            ultimoIdLocal = idLocal;
            return resultadoOperacion;
        }

        @Override
        public Result<Void> borrarCliente(int idLocal) {
            ultimoIdLocal = idLocal;
            return resultadoOperacion;
        }

        @Override
        public Result<Integer> buscarOCrearCliente(String nombre, String apellido,
                                                    @Nullable String identidad,
                                                    @Nullable String telefono) {
            return resultadoBuscarOCrear;
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

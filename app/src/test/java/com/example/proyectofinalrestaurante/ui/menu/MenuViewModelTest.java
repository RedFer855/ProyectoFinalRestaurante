package com.example.proyectofinalrestaurante.ui.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.domain.repository.MenuRepository;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/** Tests de {@link MenuViewModel} (Plan Fase 2a, E7). */
public class MenuViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final int ID_ENTRADAS = 1;
    private static final int ID_BEBIDAS = 3;

    private static Platillo platillo(int id, String nombre, int idCategoria) {
        return new Platillo(id, nombre, "Descripción de " + nombre, 35.0, idCategoria,
                "Categoría " + idCategoria, null, true);
    }

    private static List<Platillo> catalogo() {
        return Arrays.asList(
                platillo(1, "Baleada sencilla", ID_ENTRADAS),
                platillo(2, "Sopa de caracol", ID_ENTRADAS),
                platillo(3, "Refresco de tamarindo", ID_BEBIDAS));
    }

    private static List<Categoria> categorias() {
        return Arrays.asList(
                new Categoria(ID_ENTRADAS, "Entradas", true, 2, 2),
                new Categoria(ID_BEBIDAS, "Bebidas", true, 1, 1));
    }

    private MenuViewModel viewModelCon(FakeMenuRepository repositorio) {
        return new MenuViewModel(repositorio, new ExecutorServiceSincrono());
    }

    // ------------------------------------------------------------------ carga

    @Test
    public void cargar_exitoso_publicaPlatillosYCategorias() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.cargar();

        EstadoMenu estado = viewModel.getEstado().getValue();
        assertFalse(estado.isCargando());
        assertNull(estado.getError());
        assertEquals(3, estado.getPlatillos().size());
        assertEquals(2, estado.getCategorias().size());
        assertFalse(estado.isVacio());
    }

    @Test
    public void cargar_sinPlatillos_quedaVacioPeroNoPorFiltro() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        repositorio.platillos = Result.ok(new ArrayList<>());
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.cargar();

        EstadoMenu estado = viewModel.getEstado().getValue();
        assertTrue(estado.isVacio());
        // Distinguirlo importa: el mensaje "no hay coincidencias" sería mentira acá.
        assertFalse(estado.isVacioPorFiltro());
    }

    @Test
    public void cargar_errorDeRed_publicaElError() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        repositorio.platillos = Result.fail("Sin conexión al servidor. Intentá de nuevo.");
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.cargar();

        EstadoMenu estado = viewModel.getEstado().getValue();
        assertEquals("Sin conexión al servidor. Intentá de nuevo.", estado.getError());
        assertTrue(estado.getPlatillos().isEmpty());
    }

    @Test
    public void cargar_siFallanLasCategorias_tambienEsError() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        repositorio.categorias = Result.fail("No se pudieron cargar las categorías.");
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.cargar();

        // La pantalla necesita las dos listas: sin categorías no hay chips ni formulario.
        assertEquals("No se pudieron cargar las categorías.",
                viewModel.getEstado().getValue().getError());
    }

    // ------------------------------------------------------------------ filtros

    @Test
    public void filtrarPorCategoria_dejaSoloLosDeEsaCategoria() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargar();

        viewModel.filtrarPorCategoria(ID_BEBIDAS);

        EstadoMenu estado = viewModel.getEstado().getValue();
        assertEquals(1, estado.getPlatillos().size());
        assertEquals("Refresco de tamarindo", estado.getPlatillos().get(0).getNombre());
        assertEquals(ID_BEBIDAS, estado.getFiltroCategoria());
    }

    @Test
    public void filtrarPorTodos_vuelveATraerElCatalogoCompleto() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargar();
        viewModel.filtrarPorCategoria(ID_BEBIDAS);

        viewModel.filtrarPorCategoria(EstadoMenu.SIN_FILTRO);

        assertEquals(3, viewModel.getEstado().getValue().getPlatillos().size());
    }

    @Test
    public void buscar_ignoraMayusculasYBuscaTambienEnLaDescripcion() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargar();

        viewModel.buscar("  BALEADA ");

        assertEquals(1, viewModel.getEstado().getValue().getPlatillos().size());
    }

    @Test
    public void buscar_sinCoincidencias_quedaVacioPorFiltro() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargar();

        viewModel.buscar("pizza");

        EstadoMenu estado = viewModel.getEstado().getValue();
        assertTrue(estado.isVacio());
        assertTrue(estado.isVacioPorFiltro());
    }

    @Test
    public void filtroYBusquedaSeCombinan() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargar();

        viewModel.filtrarPorCategoria(ID_ENTRADAS);
        viewModel.buscar("sopa");

        assertEquals(1, viewModel.getEstado().getValue().getPlatillos().size());
        assertEquals("Sopa de caracol",
                viewModel.getEstado().getValue().getPlatillos().get(0).getNombre());
    }

    @Test
    public void elFiltroSobreviveAUnaRecarga() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargar();
        viewModel.filtrarPorCategoria(ID_BEBIDAS);
        viewModel.buscar("refresco");

        // Una operación exitosa relee del servidor; el filtro no puede perderse en el medio.
        viewModel.cambiarEstadoPlatillo(platillo(1, "Baleada sencilla", ID_ENTRADAS), false);

        EstadoMenu estado = viewModel.getEstado().getValue();
        assertEquals(ID_BEBIDAS, estado.getFiltroCategoria());
        assertEquals("refresco", estado.getTextoBusqueda());
        assertEquals(1, estado.getPlatillos().size());
    }

    // ------------------------------------------------------------------ operaciones

    @Test
    public void crearPlatillo_exitoso_dejaMensajeDeUnSoloDisparo() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.crearPlatillo(new NuevoPlatillo("Pinchos", null, 165.0, ID_ENTRADAS), null);

        assertEquals("Platillo agregado al menú.",
                viewModel.getEstado().getValue().getMensajeExito());

        viewModel.onMensajeConsumido();

        // Sin consumirlo, el Snackbar volvería a salir en cada rotación (P-013).
        assertNull(viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void crearPlatillo_rechazado_publicaElErrorDelServidor() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        repositorio.resultadoCrearPlatillo = Result.fail("Ya existe un platillo con ese nombre");
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.crearPlatillo(new NuevoPlatillo("Baleada sencilla", null, 35.0, ID_ENTRADAS), null);

        assertEquals("Ya existe un platillo con ese nombre",
                viewModel.getEstado().getValue().getError());
    }

    @Test
    public void borrarCategoria_siEraLaDelFiltro_vuelveATodos() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargar();
        viewModel.filtrarPorCategoria(ID_BEBIDAS);

        viewModel.borrarCategoria(ID_BEBIDAS);

        // Dejar el filtro apuntando a una categoría borrada mostraría una lista vacía
        // sin ninguna forma obvia de salir de ahí.
        assertEquals(EstadoMenu.SIN_FILTRO, viewModel.getEstado().getValue().getFiltroCategoria());
        assertEquals(3, viewModel.getEstado().getValue().getPlatillos().size());
    }

    @Test
    public void cambiarEstadoPlatillo_relee_noRetocaLaListaEnMemoria() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.cargar();
        int lecturasIniciales = repositorio.vecesQueSeLeyo;

        viewModel.cambiarEstadoPlatillo(platillo(1, "Baleada sencilla", ID_ENTRADAS), false);

        // Lo que se ve tiene que ser lo que la base aceptó, con triggers incluidos.
        assertEquals(lecturasIniciales + 1, repositorio.vecesQueSeLeyo);
    }

    /** Fake de {@link MenuRepository}: sin red, sin Retrofit — solo lo que el test necesita. */
    private static final class FakeMenuRepository implements MenuRepository {

        Result<List<Platillo>> platillos = Result.ok(catalogo());
        Result<List<Categoria>> categorias = Result.ok(categorias());
        Result<Platillo> resultadoCrearPlatillo = Result.ok(platillo(9, "Nuevo", ID_ENTRADAS));
        Result<Void> resultadoOperacion = Result.ok(null);
        int vecesQueSeLeyo = 0;

        @Override
        public Result<List<Platillo>> listarPlatillos() {
            vecesQueSeLeyo++;
            return platillos;
        }

        @Override
        public Result<List<Categoria>> listarCategorias() {
            return categorias;
        }

        @Override
        public Result<Platillo> crearPlatillo(NuevoPlatillo nuevo, ImagenPlatillo imagen) {
            return resultadoCrearPlatillo;
        }

        @Override
        public Result<Void> actualizarPlatillo(Platillo platillo, ImagenPlatillo imagenNueva) {
            return resultadoOperacion;
        }

        @Override
        public Result<Void> quitarImagen(Platillo platillo) {
            return resultadoOperacion;
        }

        @Override
        public Result<Void> cambiarEstadoPlatillo(int idPlatillo, boolean activo) {
            return resultadoOperacion;
        }

        @Override
        public Result<Categoria> crearCategoria(String descripcion) {
            return Result.ok(new Categoria(9, descripcion, true, 0, 0));
        }

        @Override
        public Result<Void> renombrarCategoria(int idCategoria, String descripcion) {
            return resultadoOperacion;
        }

        @Override
        public Result<Void> cambiarEstadoCategoria(int idCategoria, boolean activo) {
            return resultadoOperacion;
        }

        @Override
        public Result<Void> borrarCategoria(int idCategoria) {
            return resultadoOperacion;
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
            return List.of();
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

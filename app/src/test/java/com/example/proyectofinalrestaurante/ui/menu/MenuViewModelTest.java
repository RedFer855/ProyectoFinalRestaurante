package com.example.proyectofinalrestaurante.ui.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
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

/**
 * Tests de {@link MenuViewModel} (Plan Fase 2b, E6/E8): la UI ya no lee de la red sino de
 * Room, así que el test alimenta las tres {@link LiveData} del contrato (platillos,
 * categorías y estado de sincronización) y verifica el estado fusionado.
 *
 * <p>El repositorio es un fake sin Room ni Retrofit. Como {@code MediatorLiveData} solo
 * emite cuando tiene un observador activo, cada test activa la cadena observando el estado
 * una vez — ese es el equivalente del viejo {@code cargar()}, que ya no existe.</p>
 */
public class MenuViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final int ID_ENTRADAS = 1;
    private static final int ID_BEBIDAS = 3;

    private static Platillo platillo(int idLocal, String nombre, int idCategoria) {
        return new Platillo(idLocal, idLocal, nombre, "Descripción de " + nombre, 35.0,
                idCategoria, "Categoría " + idCategoria, null, true, EstadoSync.SINCRONIZADO);
    }

    private static Categoria categoria(int idLocal, String descripcion) {
        return new Categoria(idLocal, idLocal, descripcion, true, 2, 2,
                EstadoSync.SINCRONIZADO);
    }

    private static List<Platillo> catalogo() {
        return Arrays.asList(
                platillo(1, "Baleada sencilla", ID_ENTRADAS),
                platillo(2, "Sopa de caracol", ID_ENTRADAS),
                platillo(3, "Refresco de tamarindo", ID_BEBIDAS));
    }

    private static List<Categoria> categorias() {
        return Arrays.asList(
                categoria(ID_ENTRADAS, "Entradas"),
                categoria(ID_BEBIDAS, "Bebidas"));
    }

    /** Activa la cadena LiveData observando el estado; sin esto, las fuentes no emiten. */
    private MenuViewModel viewModelCon(FakeMenuRepository repositorio) {
        MenuViewModel viewModel = new MenuViewModel(repositorio, new ExecutorServiceSincrono());
        viewModel.getEstado().observeForever(ignorado -> { });
        return viewModel;
    }

    // ------------------------------------------------------------------ datos iniciales

    @Test
    public void alActivarLasFuentes_muestraPlatillosYCategorias() {
        FakeMenuRepository repositorio = new FakeMenuRepository();

        MenuViewModel viewModel = viewModelCon(repositorio);

        EstadoMenu estado = viewModel.getEstado().getValue();
        assertFalse(estado.isCargando());
        assertNull(estado.getError());
        assertEquals(3, estado.getPlatillos().size());
        assertEquals(2, estado.getCategorias().size());
        assertFalse(estado.isVacio());
    }

    @Test
    public void sinPlatillos_quedaVacioPeroNoPorFiltro() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        repositorio.platillos.setValue(new ArrayList<>());

        MenuViewModel viewModel = viewModelCon(repositorio);

        EstadoMenu estado = viewModel.getEstado().getValue();
        assertTrue(estado.isVacio());
        // Distinguirlo importa: el mensaje "no hay coincidencias" sería mentira acá.
        assertFalse(estado.isVacioPorFiltro());
    }

    @Test
    public void sinPlatillosYSinHaberSincronizado_esEsperaYNoMenuVacio() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        repositorio.platillos.setValue(new ArrayList<>());

        MenuViewModel viewModel = viewModelCon(repositorio);

        // Regresión de la Sesión 2026-08-04: en instalación desde cero Room emite la lista
        // vacía enseguida, mucho antes de que conteste el servidor. Anunciar ahí "Todavía
        // no hay platillos" era un estado terminal falso.
        assertTrue(viewModel.getEstado().getValue().isEsperandoPrimeraCarga());
    }

    @Test
    public void trasLaPrimeraPasada_conListaVacia_reciensSeAnunciaComoVacio() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        repositorio.platillos.setValue(new ArrayList<>());
        MenuViewModel viewModel = viewModelCon(repositorio);

        // Un ciclo completo de sincronización: arranca y termina.
        repositorio.sincronizacion.setValue(new EstadoSincronizacion(true, null));
        repositorio.sincronizacion.setValue(new EstadoSincronizacion(false, null));

        EstadoMenu estado = viewModel.getEstado().getValue();
        assertFalse(estado.isEsperandoPrimeraCarga());
        // Ahora sí: el servidor contestó y de verdad no hay platillos.
        assertTrue(estado.isVacio());
    }

    @Test
    public void conPlatillos_nuncaSeReportaComoEsperandoLaPrimeraCarga() {
        FakeMenuRepository repositorio = new FakeMenuRepository();

        MenuViewModel viewModel = viewModelCon(repositorio);

        // isEsperandoPrimeraCarga() deriva de isVacio(): con datos en pantalla no aplica,
        // aunque todavía no haya terminado ninguna pasada.
        assertFalse(viewModel.getEstado().getValue().isEsperandoPrimeraCarga());
    }

    @Test
    public void errorDeSincronizacion_terminaEnElEstado() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        repositorio.sincronizacion.setValue(
                new EstadoSincronizacion(false, "2 cambios no se pudieron subir"));

        MenuViewModel viewModel = viewModelCon(repositorio);

        assertEquals("2 cambios no se pudieron subir",
                viewModel.getEstado().getValue().getUltimoErrorSync());
    }

    @Test
    public void mientrasSincroniza_elIndicadorEstaEncendido() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        repositorio.sincronizacion.setValue(new EstadoSincronizacion(true, null));

        MenuViewModel viewModel = viewModelCon(repositorio);

        assertTrue(viewModel.getEstado().getValue().isSincronizando());
    }

    @Test
    public void cambiosSinSubir_cuentaFilasQueNoEstanSincronizadas() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        List<Platillo> conPendientes = Arrays.asList(
                platillo(1, "Baleada sencilla", ID_ENTRADAS),
                platilloConEstado(2, "Sopa de caracol", ID_ENTRADAS, EstadoSync.PENDIENTE),
                platilloConEstado(3, "Refresco de tamarindo", ID_BEBIDAS, EstadoSync.ERROR));
        List<Categoria> conCategoriaPendiente = Arrays.asList(
                categoria(ID_ENTRADAS, "Entradas"),
                categoriaConEstado(ID_BEBIDAS, "Bebidas", EstadoSync.PENDIENTE));
        repositorio.platillos.setValue(conPendientes);
        repositorio.categorias.setValue(conCategoriaPendiente);

        MenuViewModel viewModel = viewModelCon(repositorio);

        // 2 platillos + 1 categoría sin subir; el conteo se deriva, no se guarda.
        assertEquals(3, viewModel.getEstado().getValue().getCambiosSinSubir());
    }

    private static Platillo platilloConEstado(int idLocal, String nombre, int idCategoria,
                                              EstadoSync estadoSync) {
        return new Platillo(idLocal, idLocal, nombre, "Descripción de " + nombre, 35.0,
                idCategoria, "Categoría " + idCategoria, null, true, estadoSync);
    }

    private static Categoria categoriaConEstado(int idLocal, String descripcion,
                                                EstadoSync estadoSync) {
        return new Categoria(idLocal, idLocal, descripcion, true, 2, 2, estadoSync);
    }

    @Test
    public void todosLosPlatillos_esCopiaCompletaSinFiltrar() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorCategoria(ID_BEBIDAS);

        // El formulario y el chequeo de duplicados necesitan el catálogo entero.
        assertEquals(3, viewModel.getTodosLosPlatillos().size());
        assertEquals(1, viewModel.getEstado().getValue().getPlatillos().size());
    }

    // ------------------------------------------------------------------ filtros

    @Test
    public void filtrarPorCategoria_dejaSoloLosDeEsaCategoria() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);

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
        viewModel.filtrarPorCategoria(ID_BEBIDAS);

        viewModel.filtrarPorCategoria(EstadoMenu.SIN_FILTRO);

        assertEquals(3, viewModel.getEstado().getValue().getPlatillos().size());
    }

    @Test
    public void buscar_ignoraMayusculasYBuscaTambienEnLaDescripcion() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.buscar("  BALEADA ");

        assertEquals(1, viewModel.getEstado().getValue().getPlatillos().size());
    }

    @Test
    public void buscar_sinCoincidencias_quedaVacioPorFiltro() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.buscar("pizza");

        EstadoMenu estado = viewModel.getEstado().getValue();
        assertTrue(estado.isVacio());
        assertTrue(estado.isVacioPorFiltro());
    }

    @Test
    public void filtroYBusquedaSeCombinan() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.filtrarPorCategoria(ID_ENTRADAS);
        viewModel.buscar("sopa");

        assertEquals(1, viewModel.getEstado().getValue().getPlatillos().size());
        assertEquals("Sopa de caracol",
                viewModel.getEstado().getValue().getPlatillos().get(0).getNombre());
    }

    @Test
    public void elFiltroSobreviveAUnaOperacion() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorCategoria(ID_BEBIDAS);
        viewModel.buscar("refresco");

        // Una operación exitosa no puede resetear el filtro con el que se está mirando.
        viewModel.cambiarEstadoPlatillo(3, false);

        EstadoMenu estado = viewModel.getEstado().getValue();
        assertEquals(ID_BEBIDAS, estado.getFiltroCategoria());
        assertEquals("refresco", estado.getTextoBusqueda());
        assertEquals(1, estado.getPlatillos().size());
    }

    // ------------------------------------------------------------------ operaciones

    @Test
    public void crearPlatillo_exitoso_anunciaYMantieneElMensajeDeUnSoloDisparo() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.crearPlatillo(new NuevoPlatillo("Pinchos", null, 165.0, ID_ENTRADAS), null);

        assertEquals("Platillo agregado al menú.",
                viewModel.getEstado().getValue().getMensajeExito());
        assertEquals("Pinchos", repositorio.ultimoNuevo.getNombre());

        viewModel.onMensajeConsumido();

        // Sin consumirlo, el Snackbar volvería a salir en cada rotación (P-013).
        assertNull(viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void crearPlatillo_rechazado_publicaElError() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        repositorio.resultadoCrearPlatillo = Result.fail("Ya existe un platillo con ese nombre");
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.crearPlatillo(new NuevoPlatillo("Baleada sencilla", null, 35.0, ID_ENTRADAS),
                null);

        assertEquals("Ya existe un platillo con ese nombre",
                viewModel.getEstado().getValue().getError());
    }

    @Test
    public void actualizarPlatillo_siLoMueveAOtraCategoria_sueltaElFiltroQueLoEsconderia() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorCategoria(ID_ENTRADAS);

        // La baleada estaba en Entradas y el usuario la manda a Bebidas.
        viewModel.actualizarPlatillo(1,
                new NuevoPlatillo("Baleada sencilla", null, 35.0, ID_BEBIDAS), null);

        // Con el filtro en Entradas, el platillo recién guardado desaparecía de la pantalla
        // y se leía como que el cambio no se había aplicado.
        EstadoMenu estado = viewModel.getEstado().getValue();
        assertEquals(EstadoMenu.SIN_FILTRO, estado.getFiltroCategoria());
        assertEquals("Platillo actualizado.", estado.getMensajeExito());
    }

    @Test
    public void actualizarPlatillo_siSigueEnLaCategoriaDelFiltro_loConserva() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorCategoria(ID_BEBIDAS);

        // Solo cambia el nombre: el platillo sigue visible, así que el filtro no estorba.
        viewModel.actualizarPlatillo(3,
                new NuevoPlatillo("Refresco de tamarindo XL", null, 35.0, ID_BEBIDAS), null);

        assertEquals(ID_BEBIDAS, viewModel.getEstado().getValue().getFiltroCategoria());
    }

    @Test
    public void crearPlatillo_enOtraCategoria_sueltaElFiltroQueLoEsconderia() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorCategoria(ID_BEBIDAS);

        viewModel.crearPlatillo(new NuevoPlatillo("Pinchos", null, 165.0, ID_ENTRADAS), null);

        // Mismo problema que al editar: crear algo que no se ve parece que falló.
        assertEquals(EstadoMenu.SIN_FILTRO,
                viewModel.getEstado().getValue().getFiltroCategoria());
    }

    @Test
    public void borrarCategoria_siEraLaDelFiltro_vuelveATodos() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorCategoria(ID_BEBIDAS);

        viewModel.borrarCategoria(ID_BEBIDAS);

        // Dejar el filtro apuntando a una categoría borrada mostraría una lista vacía
        // sin ninguna forma obvia de salir de ahí.
        assertEquals(EstadoMenu.SIN_FILTRO, viewModel.getEstado().getValue().getFiltroCategoria());
        assertEquals(3, viewModel.getEstado().getValue().getPlatillos().size());
    }

    @Test
    public void cambiarEstadoPlatillo_pasaElIdLocalYElEstadoAlRepositorio() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.cambiarEstadoPlatillo(3, false);

        assertEquals(3, repositorio.ultimoIdPlatillo);
        assertFalse(repositorio.ultimoActivo);
        assertEquals("Platillo desactivado.",
                viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void quitarImagen_pasaElIdLocalYAnuncia() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);

        viewModel.quitarImagen(3);

        assertEquals(3, repositorio.ultimoIdSinImagen);
        assertEquals("Foto quitada.", viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void construir_sincronizaUnaVezSinEsperarElPullToRefresh() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        viewModelCon(repositorio);

        assertEquals(1, repositorio.vecesSincronizo);
    }

    @Test
    public void sincronizar_pideAlRepositorio() {
        FakeMenuRepository repositorio = new FakeMenuRepository();
        MenuViewModel viewModel = viewModelCon(repositorio);
        repositorio.vecesSincronizo = 0; // descarta el sync-on-launch del constructor

        viewModel.sincronizar();

        assertEquals(1, repositorio.vecesSincronizo);
    }

    /** Fake de {@link MenuRepository}: LiveData en memoria, escrituras que solo anotan. */
    private static final class FakeMenuRepository implements MenuRepository {

        final MutableLiveData<List<Platillo>> platillos = new MutableLiveData<>(catalogo());
        final MutableLiveData<List<Categoria>> categorias = new MutableLiveData<>(categorias());
        final MutableLiveData<EstadoSincronizacion> sincronizacion =
                new MutableLiveData<>(new EstadoSincronizacion(false, null));

        Result<Long> resultadoCrearPlatillo = Result.ok(9L);
        Result<Void> resultadoOperacion = Result.ok(null);

        int vecesSincronizo = 0;
        int ultimoIdPlatillo;
        int ultimoIdSinImagen;
        boolean ultimoActivo;
        NuevoPlatillo ultimoNuevo;

        @Override
        public LiveData<List<Platillo>> observarPlatillos() {
            return platillos;
        }

        @Override
        public LiveData<List<Categoria>> observarCategorias() {
            return categorias;
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
        public Result<Long> crearPlatillo(NuevoPlatillo nuevo, ImagenPlatillo imagen) {
            ultimoNuevo = nuevo;
            return resultadoCrearPlatillo;
        }

        @Override
        public Result<Void> actualizarPlatillo(int idLocal, NuevoPlatillo datos,
                                               ImagenPlatillo imagenNueva) {
            ultimoIdPlatillo = idLocal;
            ultimoNuevo = datos;
            return resultadoOperacion;
        }

        @Override
        public Result<Void> quitarImagen(int idLocal) {
            ultimoIdSinImagen = idLocal;
            return resultadoOperacion;
        }

        @Override
        public Result<Void> cambiarEstadoPlatillo(int idLocal, boolean activo) {
            ultimoIdPlatillo = idLocal;
            ultimoActivo = activo;
            return resultadoOperacion;
        }

        @Override
        public Result<Long> crearCategoria(String descripcion) {
            return Result.ok(9L);
        }

        @Override
        public Result<Void> renombrarCategoria(int idLocal, String descripcion) {
            return resultadoOperacion;
        }

        @Override
        public Result<Void> cambiarEstadoCategoria(int idLocal, boolean activo) {
            return resultadoOperacion;
        }

        @Override
        public Result<Void> borrarCategoria(int idLocal) {
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

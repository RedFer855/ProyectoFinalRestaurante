package com.example.proyectofinalrestaurante.ui.mesas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;
import com.example.proyectofinalrestaurante.domain.repository.MesaRepository;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests de {@link MesasViewModel} (Plan Fase 2c, E7). Mismo patrón que {@code MenuViewModelTest}:
 * el repositorio es un fake sin Room ni Retrofit, con {@link MutableLiveData} en memoria.
 */
public class MesasViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static Mesa mesa(int idLocal, int numero, EstadoMesa estado, boolean activo) {
        return new Mesa(idLocal, idLocal, numero, 4, "Salón", estado, activo, "2026-08-01",
                EstadoSync.SINCRONIZADO);
    }

    private static Mesa mesaConEstadoSync(int idLocal, int numero, EstadoSync estadoSync) {
        return new Mesa(idLocal, idLocal, numero, 4, "Salón", EstadoMesa.LIBRE, true,
                "2026-08-01", estadoSync);
    }

    private static List<Mesa> salon() {
        return Arrays.asList(
                mesa(1, 4, EstadoMesa.LIBRE, true),
                mesa(2, 7, EstadoMesa.OCUPADA, true),
                mesa(3, 2, EstadoMesa.RESERVADA, true));
    }

    /** Activa la cadena LiveData observando el estado; sin esto, las fuentes no emiten. */
    private MesasViewModel viewModelCon(FakeMesaRepository repositorio) {
        MesasViewModel viewModel = new MesasViewModel(repositorio, new ExecutorServiceSincrono());
        viewModel.getEstado().observeForever(ignorado -> { });
        return viewModel;
    }

    // ------------------------------------------------------------------ datos iniciales

    @Test
    public void alActivarLasFuentes_muestraElSalon() {
        FakeMesaRepository repositorio = new FakeMesaRepository();

        MesasViewModel viewModel = viewModelCon(repositorio);

        EstadoMesas estado = viewModel.getEstado().getValue();
        assertFalse(estado.isCargando());
        assertNull(estado.getError());
        assertEquals(3, estado.getMesas().size());
        assertFalse(estado.isVacio());
    }

    @Test
    public void sinMesas_quedaVacioPeroNoPorFiltro() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        repositorio.mesas.setValue(new ArrayList<>());

        MesasViewModel viewModel = viewModelCon(repositorio);

        EstadoMesas estado = viewModel.getEstado().getValue();
        assertTrue(estado.isVacio());
        assertFalse(estado.isVacioPorFiltro());
    }

    @Test
    public void errorDeSincronizacion_terminaEnElEstado() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        repositorio.sincronizacion.setValue(
                new EstadoSincronizacion(false, "2 cambios no se pudieron subir"));

        MesasViewModel viewModel = viewModelCon(repositorio);

        assertEquals("2 cambios no se pudieron subir",
                viewModel.getEstado().getValue().getUltimoErrorSync());
    }

    @Test
    public void cambiosSinSubir_cuentaFilasQueNoEstanSincronizadas() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        repositorio.mesas.setValue(Arrays.asList(
                mesaConEstadoSync(1, 4, EstadoSync.SINCRONIZADO),
                mesaConEstadoSync(2, 7, EstadoSync.PENDIENTE),
                mesaConEstadoSync(3, 2, EstadoSync.ERROR)));

        MesasViewModel viewModel = viewModelCon(repositorio);

        assertEquals(2, viewModel.getEstado().getValue().getCambiosSinSubir());
    }

    @Test
    public void todasLasMesas_esCopiaCompletaSinFiltrar() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorEstado(EstadoMesa.LIBRE);

        assertEquals(3, viewModel.getTodasLasMesas().size());
        assertEquals(1, viewModel.getEstado().getValue().getMesas().size());
    }

    // ------------------------------------------------------------------ filtros

    @Test
    public void filtrarPorEstado_dejaSoloLasDeEseEstado() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);

        viewModel.filtrarPorEstado(EstadoMesa.OCUPADA);

        EstadoMesas estado = viewModel.getEstado().getValue();
        assertEquals(1, estado.getMesas().size());
        assertEquals(7, estado.getMesas().get(0).getNumeroMesa());
        assertEquals(EstadoMesa.OCUPADA, estado.getFiltroEstado());
    }

    @Test
    public void filtrarPorNull_vuelveATraerTodas() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorEstado(EstadoMesa.OCUPADA);

        viewModel.filtrarPorEstado(null);

        assertEquals(3, viewModel.getEstado().getValue().getMesas().size());
    }

    @Test
    public void buscar_porNumero() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);

        viewModel.buscar("7");

        assertEquals(1, viewModel.getEstado().getValue().getMesas().size());
    }

    @Test
    public void buscar_sinCoincidencias_quedaVacioPorFiltro() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);

        viewModel.buscar("99");

        EstadoMesas estado = viewModel.getEstado().getValue();
        assertTrue(estado.isVacio());
        assertTrue(estado.isVacioPorFiltro());
    }

    // ------------------------------------------------------------------ cambiar estado

    @Test
    public void cambiarEstado_pasaLaMesaYElEstadoAlRepositorio() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);

        viewModel.cambiarEstado(mesa(2, 7, EstadoMesa.OCUPADA, true), EstadoMesa.LIBRE);

        assertEquals(2, repositorio.ultimoIdLocal);
        assertEquals(EstadoMesa.LIBRE, repositorio.ultimoEstado);
    }

    @Test
    public void cambiarEstado_siElFiltroLoEsconderia_loSuelta() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorEstado(EstadoMesa.LIBRE);

        // La mesa 1 estaba Libre (visible con el filtro) y se marca Ocupada.
        viewModel.cambiarEstado(mesa(1, 4, EstadoMesa.LIBRE, true), EstadoMesa.OCUPADA);

        // Con el filtro "Libre" activo, la mesa recién ocupada desaparecería de la
        // pantalla y el mesero leería el guardado como si hubiera fallado (Plan Fase 2c, §5.1).
        assertNull(viewModel.getEstado().getValue().getFiltroEstado());
    }

    @Test
    public void cambiarEstado_siSigueCoincidiendoConElFiltro_loConserva() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);
        viewModel.filtrarPorEstado(EstadoMesa.OCUPADA);

        viewModel.cambiarEstado(mesa(2, 7, EstadoMesa.OCUPADA, true), EstadoMesa.OCUPADA);

        assertEquals(EstadoMesa.OCUPADA, viewModel.getEstado().getValue().getFiltroEstado());
    }

    @Test
    public void cambiarEstado_rechazado_publicaElError() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        repositorio.resultadoOperacion = Result.fail("Esa mesa está dada de baja.");
        MesasViewModel viewModel = viewModelCon(repositorio);

        viewModel.cambiarEstado(mesa(1, 4, EstadoMesa.LIBRE, false), EstadoMesa.OCUPADA);

        assertEquals("Esa mesa está dada de baja.", viewModel.getEstado().getValue().getError());
    }

    // ------------------------------------------------------------------ crear/editar/baja

    @Test
    public void crear_exitoso_anunciaYMantieneElMensajeDeUnSoloDisparo() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);

        viewModel.crear(new NuevaMesa(9, 4, null));

        assertEquals("Mesa creada.", viewModel.getEstado().getValue().getMensajeExito());
        assertEquals(9, repositorio.ultimaNueva.getNumeroMesa());

        viewModel.onMensajeConsumido();

        assertNull(viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void crear_rechazado_publicaElError() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        repositorio.resultadoCrear = Result.fail("Ya existe una mesa con ese número");
        MesasViewModel viewModel = viewModelCon(repositorio);

        viewModel.crear(new NuevaMesa(4, 4, null));

        assertEquals("Ya existe una mesa con ese número",
                viewModel.getEstado().getValue().getError());
    }

    @Test
    public void actualizar_pasaLosDatosAlRepositorio() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);

        viewModel.actualizar(1, new NuevaMesa(4, 8, "Terraza"));

        assertEquals(1, repositorio.ultimoIdLocal);
        assertEquals(8, repositorio.ultimaNueva.getCapacidad());
        assertEquals("Mesa actualizada.", viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void cambiarBaja_activar_publicaElMensajeCorrecto() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);

        viewModel.cambiarBaja(mesa(1, 4, EstadoMesa.LIBRE, false), true);

        assertTrue(repositorio.ultimoActivo);
        assertEquals("Mesa reactivada.", viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void cambiarBaja_desactivar_publicaElMensajeCorrecto() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);

        viewModel.cambiarBaja(mesa(1, 4, EstadoMesa.LIBRE, true), false);

        assertFalse(repositorio.ultimoActivo);
        assertEquals("Mesa dada de baja.", viewModel.getEstado().getValue().getMensajeExito());
    }

    @Test
    public void construir_sincronizaUnaVezSinEsperarElPullToRefresh() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        viewModelCon(repositorio);

        assertEquals(1, repositorio.vecesSincronizo);
    }

    @Test
    public void sincronizar_pideAlRepositorio() {
        FakeMesaRepository repositorio = new FakeMesaRepository();
        MesasViewModel viewModel = viewModelCon(repositorio);
        repositorio.vecesSincronizo = 0; // descarta el sync-on-launch del constructor

        viewModel.sincronizar();

        assertEquals(1, repositorio.vecesSincronizo);
    }

    /** Fake de {@link MesaRepository}: LiveData en memoria, escrituras que solo anotan. */
    private static final class FakeMesaRepository implements MesaRepository {

        final MutableLiveData<List<Mesa>> mesas = new MutableLiveData<>(salon());
        final MutableLiveData<List<EstadoMesa>> estados =
                new MutableLiveData<>(Arrays.asList(EstadoMesa.values()));
        final MutableLiveData<EstadoSincronizacion> sincronizacion =
                new MutableLiveData<>(new EstadoSincronizacion(false, null));

        Result<Long> resultadoCrear = Result.ok(9L);
        Result<Void> resultadoOperacion = Result.ok(null);

        int vecesSincronizo = 0;
        int ultimoIdLocal;
        boolean ultimoActivo;
        NuevaMesa ultimaNueva;
        EstadoMesa ultimoEstado;

        @Override
        public LiveData<List<Mesa>> observarMesas() {
            return mesas;
        }

        @Override
        public LiveData<List<EstadoMesa>> observarEstadosMesa() {
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
        public Result<Long> crearMesa(NuevaMesa nueva) {
            ultimaNueva = nueva;
            return resultadoCrear;
        }

        @Override
        public Result<Void> actualizarMesa(int idLocal, NuevaMesa datos) {
            ultimoIdLocal = idLocal;
            ultimaNueva = datos;
            return resultadoOperacion;
        }

        @Override
        public Result<Void> cambiarEstadoMesa(int idLocal, EstadoMesa nuevoEstado) {
            ultimoIdLocal = idLocal;
            ultimoEstado = nuevoEstado;
            return resultadoOperacion;
        }

        @Override
        public Result<Void> cambiarBajaMesa(int idLocal, boolean activo) {
            ultimoIdLocal = idLocal;
            ultimoActivo = activo;
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

package com.example.proyectofinalrestaurante.ui.buzon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proyectofinalrestaurante.domain.model.Notificacion;
import com.example.proyectofinalrestaurante.domain.model.TipoNotificacion;
import com.example.proyectofinalrestaurante.domain.repository.NotificacionRepository;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests de {@link BuzonViewModel} (Plan Fase 3, E9). Mismo patrón que el resto de los
 * ViewModel: repositorio fake sin Room ni Retrofit.
 */
public class BuzonViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static Notificacion notificacion(long idLocal, TipoNotificacion tipo,
                                             @Nullable String arg1, boolean leida) {
        return new Notificacion(idLocal, tipo, null, null, arg1, 1_000L + idLocal, leida);
    }

    private BuzonViewModel viewModelCon(FakeBuzonRepository repositorio) {
        BuzonViewModel viewModel = new BuzonViewModel(repositorio, new ExecutorServiceSincrono());
        viewModel.getEstado().observeForever(ignorado -> { });
        return viewModel;
    }

    // ------------------------------------------------------------------ lista

    @Test
    public void construir_muestraListaVacia() {
        FakeBuzonRepository repositorio = new FakeBuzonRepository();
        BuzonViewModel viewModel = viewModelCon(repositorio);

        assertTrue(viewModel.getEstado().getValue().isVacio());
        assertEquals(0, viewModel.getEstado().getValue().getNotificaciones().size());
    }

    @Test
    public void alLlegarNotificaciones_muestraLaLista() {
        FakeBuzonRepository repositorio = new FakeBuzonRepository();
        repositorio.lista.setValue(Arrays.asList(
                notificacion(1, TipoNotificacion.PEDIDO_NUEVO, "41", false),
                notificacion(2, TipoNotificacion.PEDIDO_LISTO, "42", true)));
        BuzonViewModel viewModel = viewModelCon(repositorio);

        EstadoBuzon estado = viewModel.getEstado().getValue();
        assertEquals(2, estado.getNotificaciones().size());
        assertFalse(estado.isVacio());
    }

    @Test
    public void sinNotificaciones_quedaVacio() {
        FakeBuzonRepository repositorio = new FakeBuzonRepository();
        repositorio.lista.setValue(new ArrayList<>());
        BuzonViewModel viewModel = viewModelCon(repositorio);

        assertTrue(viewModel.getEstado().getValue().isVacio());
    }

    // ------------------------------------------------------------------ badge

    @Test
    public void elConteoNoLeidas_alimentaElBadge() {
        FakeBuzonRepository repositorio = new FakeBuzonRepository();
        BuzonViewModel viewModel = viewModelCon(repositorio);

        repositorio.noLeidas.setValue(3);

        assertEquals(3, viewModel.getEstado().getValue().getNoLeidas());
    }

    @Test
    public void cuandoElConteoBaja_aCeroElBadgeAvisa() {
        FakeBuzonRepository repositorio = new FakeBuzonRepository();
        BuzonViewModel viewModel = viewModelCon(repositorio);
        repositorio.noLeidas.setValue(2);

        repositorio.noLeidas.setValue(0);

        assertEquals(0, viewModel.getEstado().getValue().getNoLeidas());
    }

    // ------------------------------------------------------------------ abrir / leer

    @Test
    public void abrir_marcaTodoLeido() {
        FakeBuzonRepository repositorio = new FakeBuzonRepository();
        BuzonViewModel viewModel = viewModelCon(repositorio);

        viewModel.abrir();

        assertTrue(repositorio.marcadasTodas);
    }

    @Test
    public void abrir_purgaLeidasDeMasDe48Horas() {
        FakeBuzonRepository repositorio = new FakeBuzonRepository();
        BuzonViewModel viewModel = viewModelCon(repositorio);

        viewModel.abrir();

        long ahora = System.currentTimeMillis();
        // El umbral es ahora − 48 h, con un margen de una hora por el reloj del test.
        assertTrue(repositorio.ultimoAntesDe < ahora - 47L * 60 * 60 * 1000);
        assertTrue(repositorio.ultimoAntesDe > ahora - 49L * 60 * 60 * 1000);
    }

    @Test
    public void marcarLeida_pideAlRepositorio() {
        FakeBuzonRepository repositorio = new FakeBuzonRepository();
        BuzonViewModel viewModel = viewModelCon(repositorio);

        viewModel.marcarLeida(notificacion(7, TipoNotificacion.PEDIDO_NUEVO, "41", false));

        assertEquals(1, repositorio.leidasMarcadas.size());
        assertEquals(7L, repositorio.leidasMarcadas.get(0).longValue());
    }

    // ------------------------------------------------------------------ fake

    /** Fake de {@link NotificacionRepository}: LiveData en memoria, escrituras que solo anotan. */
    private static final class FakeBuzonRepository implements NotificacionRepository {

        final MutableLiveData<List<Notificacion>> lista = new MutableLiveData<>(new ArrayList<>());
        final MutableLiveData<Integer> noLeidas = new MutableLiveData<>(0);

        boolean marcadasTodas = false;
        long ultimoAntesDe;
        final List<Long> leidasMarcadas = new ArrayList<>();

        @Override
        public LiveData<Integer> contarNoLeidas() {
            return noLeidas;
        }

        @Override
        public LiveData<List<Notificacion>> observarBuzon(int ventana) {
            return lista;
        }

        @Override
        public void marcarLeida(long idLocal) {
            leidasMarcadas.add(idLocal);
        }

        @Override
        public void marcarTodasLeidas() {
            marcadasTodas = true;
        }

        @Override
        public void purgarViejas(long antesDe) {
            ultimoAntesDe = antesDe;
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
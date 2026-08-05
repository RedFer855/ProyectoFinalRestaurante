package com.example.proyectofinalrestaurante.data.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * R5 sobre {@link DisparadorDebounce} (Plan Fase 3, B1–B3). Reloj, programador y jitter
 * inyectados para no dormir los 3 segundos reales de la ventana:
 *
 * <ul>
 *   <li>B1 — 25 señales en 500 ms → ≤ 2 llamadas a la acción.</li>
 *   <li>B2 — una señal aislada → dispara inmediato, sin esperar la ventana.</li>
 *   <li>B3 — señal, silencio de ventana, señal → 2 disparos, no 1.</li>
 * </ul>
 */
public class DisparadorDebounceTest {

    private static final long VENTANA = 3_000L;

    private final AtomicLong reloj = new AtomicLong();
    private final ProgramadorFalso programador = new ProgramadorFalso();
    private final List<Integer> llamadas = new ArrayList<>();
    private final JitterFijo jitter = new JitterFijo();

    private DisparadorDebounce crearDisparador() {
        return new DisparadorDebounce(reloj::get, programador, VENTANA, 1_500L, jitter,
                () -> llamadas.add(1));
    }

    /** B2 — una señal aislada dispara de inmediato, en el mismo instante de la señal. */
    @Test
    public void señalAislada_disparaInmediato() {
        DisparadorDebounce disparador = crearDisparador();

        reloj.set(1_000L);
        disparador.senal();

        assertEquals(1, llamadas.size());
    }

    /** B2 — la señal aislada no programa un cierre: una sola llamada. */
    @Test
    public void señalAislada_noCierraLaVentana() {
        DisparadorDebounce disparador = crearDisparador();

        reloj.set(1_000L);
        disparador.senal();

        programador.ejecutarVencidas(reloj.get() + VENTANA);
        programador.ejecutarVencidas(reloj.get() + VENTANA + 1_500L);

        assertEquals(1, llamadas.size());
    }

    /** B1 — 25 señales en 500 ms producen ≤ 2 llamadas. */
    @Test
    public void ráfagaDeVeinticincoEnQuinientosMs_disparaDos() {
        DisparadorDebounce disparador = crearDisparador();
        reloj.set(0);

        for (int i = 0; i < 25; i++) {
            reloj.addAndGet(20); // 25 × 20 ms = 500 ms
            disparador.senal();
        }

        // leading (1) en la primera señal; el cierre se disparará una vez más.
        programador.ejecutarVencidas(reloj.get() + VENTANA + 1_500L);

        assertEquals(2, llamadas.size());
    }

    /** Las señales dentro de la ventana no disparan: solo la primera y el cierre. */
    @Test
    public void señalesDentroDeLaVentana_seTraganTodas() {
        DisparadorDebounce disparador = crearDisparador();
        reloj.set(0);

        disparador.senal();      // t=0: leading
        reloj.set(500L);
        disparador.senal();
        reloj.set(1_500L);
        disparador.senal();

        programador.ejecutarVencidas(reloj.get() + VENTANA + 1_500L);

        assertEquals(2, llamadas.size());
    }

    /** B3 — señal, silencio de una ventana entera, señal → 2 disparos, no 1. */
    @Test
    public void señal_silencioDeVentana_señal_disparaDos() {
        DisparadorDebounce disparador = crearDisparador();
        reloj.set(0);

        disparador.senal();                    // disparo 1
        reloj.set(VENTANA);                    // se cumplió toda la ventana
        disparador.senal();                    // nueva ráfaga → disparo 2

        assertEquals(2, llamadas.size());
    }

    /** El cierre solo se dispara si la ráfaga tuvo más de una señal. */
    @Test
    public void cierreSinCola_noDispara() {
        DisparadorDebounce disparador = crearDisparador();
        reloj.set(0);

        disparador.senal();
        reloj.set(VENTANA);
        disparador.senal(); // segunda ráfaga: también una sola → su cierre no dispara

        programador.ejecutarVencidas(0 + VENTANA + 1_500L);
        programador.ejecutarVencidas(VENTANA + VENTANA + 1_500L);

        // Dos leading, ningún cierre.
        assertEquals(2, llamadas.size());
    }

    /** El cierre espera la ventana completa más el jitter. */
    @Test
    public void cierreSeDisparaAlVencerVentanaMasJitter() {
        DisparadorDebounce disparador = crearDisparador();
        jitter.fijo = 1_000;
        reloj.set(0);

        disparador.senal();
        reloj.set(100L);
        disparador.senal(); // deja cola

        // Con ventana (3000) + jitter (1000) la tarea vence a los 4000; antes no dispara.
        programador.ejecutarVencidas(VENTANA);
        assertEquals(1, llamadas.size());

        programador.ejecutarVencidas(VENTANA + 1_000L);
        assertEquals(2, llamadas.size());
    }

    /** El jitter agrega retardo <i>antes</i> de la llamada de cierre. */
    @Test
    public void jitterAplazaElCierre() {
        DisparadorDebounce disparador = crearDisparador();
        jitter.fijo = 1_000;
        reloj.set(0);

        disparador.senal();
        reloj.set(100L);
        disparador.senal();

        // La tarea se programó con ventana + jitter(1000), no con ventana a secas.
        long origen = 0L;
        assertTrue(programador.debeEjecutarseAntesDe(origen + VENTANA + 1_000L));
    }

    /** La misma ráfaga nunca dispara dos cierres. */
    @Test
    public void unaSolaRáfaga_unSoloCierre() {
        DisparadorDebounce disparador = crearDisparador();
        reloj.set(0);

        for (int i = 0; i < 10; i++) {
            reloj.addAndGet(50L);
            disparador.senal();
        }

        programador.ejecutarVencidas(reloj.get() + VENTANA);
        programador.ejecutarVencidas(reloj.get() + VENTANA + 1_500L);
        programador.ejecutarVencidas(reloj.get() + VENTANA + 3_000L);

        // leading + un cierre; nunca dos.
        assertEquals(2, llamadas.size());
    }

    // ------------------------------------------------------------------ dobletes de prueba

    private static final class JitterFijo implements DisparadorDebounce.FuenteAleatoria {
        int fijo;

        @Override
        public int siguiente(int limiteExclusivo) {
            return Math.min(fijo, limiteExclusivo - 1);
        }
    }

    /** {@link ScheduledExecutorService} que registra las tareas para ejecutarlas a mano. */
    private static final class ProgramadorFalso implements ScheduledExecutorService {
        final List<ParTarea> tareas = new ArrayList<>();

        static final class ParTarea {
            final Runnable funcion;
            final long retardoMs;

            ParTarea(Runnable funcion, long retardoMs) {
                this.funcion = funcion;
                this.retardoMs = retardoMs;
            }
        }

        boolean debeEjecutarseAntesDe(long retardoMax) {
            return tareas.stream().anyMatch(t -> t.retardoMs <= retardoMax);
        }

        void ejecutarVencidas(long ahora) {
            List<ParTarea> porEjecutar = new ArrayList<>();
            for (ParTarea t : new ArrayList<>(tareas)) {
                if (t.retardoMs <= ahora) {
                    porEjecutar.add(t);
                    tareas.remove(t);
                }
            }
            for (ParTarea t : porEjecutar) {
                t.funcion.run();
            }
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            tareas.add(new ParTarea(command, unit.toMillis(delay)));
            return null;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay,
                                               TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
                                                      long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
                                                         long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> v)
                throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> v,
                                             long timeout, TimeUnit unit)
                throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> v)
                throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> v, long timeout,
                               TimeUnit unit) throws InterruptedException,
                ExecutionException, TimeoutException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException();
        }
    }
}
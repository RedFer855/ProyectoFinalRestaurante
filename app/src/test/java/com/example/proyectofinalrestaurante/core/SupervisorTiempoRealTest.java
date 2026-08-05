package com.example.proyectofinalrestaurante.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.realtime.CanalTiempoReal;

import org.junit.Before;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ciclo de vida y backoff del {@link SupervisorTiempoReal} (Plan Fase 3, E6). Cubre B12
 * (reconexión exitosa → fuerza sincronización) y B13 (token nulo → no conecta ni reintenta).
 */
public class SupervisorTiempoRealTest {

    private final CanalFalso canal = new CanalFalso();
    private final ProgramadorFalso programador = new ProgramadorFalso();
    private final AtomicLong reloj = new AtomicLong();
    private final JitterFijo jitter = new JitterFijo();
    private final AtomicReference<String> token = new AtomicReference<>("token");
    private int fuerzasSync = 0;

    private SupervisorTiempoReal supervisor;

    @Before
    public void setUp() {
        jitter.fijo = 0;
        supervisor = new SupervisorTiempoReal(canal, token::get, programador, reloj::get,
                jitter, () -> fuerzasSync++);
    }

    /** B13 — sin token no conecta y no programa ningún reintento. */
    @Test
    public void sinToken_conectarNoConectaNiPrograma() {
        token.set(null);

        supervisor.conectarConSesion();

        assertEquals(0, canal.conectadas);
        assertEquals(0, programador.tareas.size());
    }

    /** B13 — el reintento con token ya nulo tampoco conecta ni encadena otro. */
    @Test
    public void sinToken_enReintentoNoConecta() {
        supervisor.conectarConSesion();
        canal.caer("temporal"); // programa un reintento

        token.set(null);
        programador.ejecutarTodas(); // reconectar: token nulo → no conecta ni re-programa

        assertEquals(1, canal.conectadas);
        assertEquals(0, programador.tareas.size());
    }

    @Test
    public void caer_programaUnReintentoConElPrimerBackoff() {
        supervisor.conectarConSesion();

        canal.caer("temporal");

        assertEquals(1, programador.tareas.size());
        assertEquals(SupervisorTiempoReal.BACKOFF_MS[0], programador.tareas.get(0).retardoMs);
    }

    @Test
    public void caidasSucesivas_escalanElBackoffHastaElTope() {
        supervisor.conectarConSesion();

        for (int i = 0; i < 8; i++) {
            programador.tareas.clear();
            canal.caer("temporal " + i);
            assertEquals(1, programador.tareas.size());
            long esperado = SupervisorTiempoReal.BACKOFF_MS[
                    Math.min(i, SupervisorTiempoReal.BACKOFF_MS.length - 1)];
            assertEquals(esperado, programador.tareas.get(0).retardoMs);
        }
    }

    @Test
    public void jitterAplazaElReintento() {
        jitter.fijo = 500;
        supervisor.conectarConSesion();

        canal.caer("temporal");

        long esperado = SupervisorTiempoReal.BACKOFF_MS[0] + 500;
        assertEquals(esperado, programador.tareas.get(0).retardoMs);
    }

    /** B12 — reconexión exitosa reinicia el backoff y fuerza una sincronización. */
    @Test
    public void reconexionExitosa_reiniciaElBackoffYFuerzaSync() {
        supervisor.conectarConSesion();
        canal.caer("temporal");
        programador.ejecutarTodas(); // reintento → reconecta

        assertEquals(2, canal.conectadas);
        canal.conectarOk();

        assertEquals(1, fuerzasSync);
        // El backoff se reinició: la próxima caída vuelve a 1 s, no sigue la escala.
        programador.tareas.clear();
        canal.caer("temporal otra vez");
        assertEquals(SupervisorTiempoReal.BACKOFF_MS[0], programador.tareas.get(0).retardoMs);
    }

    @Test
    public void errorDeJwt_noReintentaYDesconecta() {
        supervisor.conectarConSesion();

        canal.caer("InvalidJWTExpiration: Token expired");

        assertEquals(0, programador.tareas.size());
        assertEquals(1, canal.desconectadas);
    }

    @Test
    public void alDesconectar_laCaidaPosteriorNoProgramaReintentos() {
        supervisor.conectarConSesion();
        supervisor.desconectar();

        canal.caer("temporal");

        assertEquals(0, programador.tareas.size());
    }

    @Test
    public void reconectar_soloSiSigueActivo() {
        supervisor.conectarConSesion();
        canal.caer("temporal");
        supervisor.desconectar(); // cancela la intención: ya no está en primer plano

        programador.ejecutarTodas();

        // El reintento se descarta: no hay una conexión nueva.
        assertEquals(1, canal.conectadas);
    }

    // ------------------------------------------------------------------ dobletes

    private static final class CanalFalso implements CanalTiempoReal {
        int conectadas;
        int desconectadas;
        CanalTiempoReal.OyenteEstado oyente;

        void caer(String razon) {
            oyente.alCaer(-1, razon);
        }

        void conectarOk() {
            oyente.alConectar();
        }

        @Override
        public void conectar(String token) {
            conectadas++;
        }

        @Override
        public void desconectar() {
            desconectadas++;
        }

        @Override
        public void observarSenales(OyenteSenales oyente) {
        }

        @Override
        public void observarEstado(OyenteEstado oyente) {
            this.oyente = oyente;
        }
    }

    private static final class JitterFijo implements SupervisorTiempoReal.FuenteDeJitter {
        int fijo;

        @Override
        public int siguiente(int limiteExclusivo) {
            return Math.min(fijo, limiteExclusivo - 1);
        }
    }

    private static final class ProgramadorFalso implements ScheduledExecutorService {
        final List<TareaProgramada> tareas = new ArrayList<>();

        static final class TareaProgramada {
            final Runnable funcion;
            final long retardoMs;

            TareaProgramada(Runnable funcion, long retardoMs) {
                this.funcion = funcion;
                this.retardoMs = retardoMs;
            }
        }

        void ejecutarTodas() {
            List<TareaProgramada> aEjecutar = new ArrayList<>(tareas);
            tareas.clear();
            for (TareaProgramada t : aEjecutar) {
                t.funcion.run();
            }
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            tareas.add(new TareaProgramada(command, unit.toMillis(delay)));
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
                               TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException();
        }
    }
}
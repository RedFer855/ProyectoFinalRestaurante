package com.example.proyectofinalrestaurante.data.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

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

/**
 * El curso del protocolo Phoenix v1.0.0 de {@link CanalRealtimeSupabase}, sin red: la
 * {@code FabricaDeSocket} de prueba captura el oyente y el socket registra lo que se manda.
 * Cubre el join con token, la conversión de broadcast en señal y la desconexión limpia.
 */
public class CanalRealtimeSupabaseTest {

    private final Gson gson = new Gson();
    private final AtomicLong reloj = new AtomicLong();
    private final ProgramadorFalso programador = new ProgramadorFalso();

    private CanalRealtimeSupabase.OyenteDeSocket oyente;
    private final List<String> enviados = new ArrayList<>();
    private final List<Integer> cierres = new ArrayList<>();
    private final List<SenalDeCambio> senales = new ArrayList<>();
    private final List<String> caidas = new ArrayList<>();
    private boolean conectadoAvisado;

    private CanalRealtimeSupabase canal;

    @Before
    public void setUp() {
        canal = new CanalRealtimeSupabase(
                "wss://proyecto.supabase.co/realtime/v1/websocket", "apikey",
                gson, reloj::get, programador,
                (url, oy) -> {
                    oyente = oy;
                    return new CanalRealtimeSupabase.SocketRealtime() {
                        @Override
                        public void enviar(String json) {
                            enviados.add(json);
                        }

                        @Override
                        public void cerrar(int codigo, String razon) {
                            cierres.add(codigo);
                        }
                    };
                });
        canal.observarSenales(senales::add);
        canal.observarEstado(new CanalTiempoReal.OyenteEstado() {
            @Override
            public void alConectar() {
                conectadoAvisado = true;
            }

            @Override
            public void alCaer(int codigo, String razon) {
                caidas.add(razon == null ? "(" + codigo + ")" : razon);
            }
        });
        // Abre el socket (fabrica de prueba) y deja el oyente listo; el join llega en alAbrir().
        canal.conectar("jwt-de-prueba");
    }

    private JsonObject mensaje(int indice) {
        return gson.fromJson(enviados.get(indice), JsonObject.class);
    }

    @Test
    public void alAbrir_envíaElJoinConTokenYConfigPrivado() {
        oyente.alAbrir();

        JsonObject join = mensaje(0);
        assertEquals("phx_join", join.get("event").getAsString());
        assertEquals("realtime:pedidos", join.get("topic").getAsString());
        assertEquals("1", join.get("ref").getAsString());
        assertEquals("1", join.get("join_ref").getAsString());

        JsonObject payload = join.getAsJsonObject("payload");
        assertEquals("jwt-de-prueba", payload.get("access_token").getAsString());
        assertNotNull(payload.getAsJsonObject("config").get("broadcast"));
        assertTrue(payload.getAsJsonObject("config").get("private").getAsBoolean());
    }

    @Test
    public void alAbrir_avisaQueSeConectó() {
        oyente.alAbrir();
        assertTrue(conectadoAvisado);
    }

    @Test
    public void broadcastCambio_emiteUnaSeñalDePedidos() {
        oyente.alRecibir("{\"topic\":\"realtime:pedidos\",\"event\":\"broadcast\","
                + "\"payload\":{\"event\":\"cambio\",\"type\":\"broadcast\","
                + "\"payload\":{\"t\":\"pedido\"}}}");

        assertEquals(1, senales.size());
        assertEquals(SenalDeCambio.MODULO_PEDIDOS, senales.get(0).getModulo());
    }

    @Test
    public void broadcastDeOtroEvento_noEmiteSeñal() {
        oyente.alRecibir("{\"event\":\"broadcast\",\"payload\":{\"event\":\"otro\","
                + "\"payload\":{}}}");

        assertEquals(0, senales.size());
    }

    @Test
    public void mensajeQueNoEsBroadcast_ignorado() {
        oyente.alRecibir("{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"payload\":{}}");
        oyente.alRecibir("{\"event\":\"phx_reply\",\"payload\":{\"status\":\"ok\","
                + "\"response\":{}}}");

        assertEquals(0, senales.size());
    }

    @Test
    public void desconectar_envíaPhoenixLeaveYCierraCon1000() {
        oyente.alAbrir();
        canal.desconectar();

        JsonObject ultimo = gson.fromJson(enviados.get(enviados.size() - 1), JsonObject.class);
        assertEquals("phx_leave", ultimo.get("event").getAsString());
        assertEquals("realtime:pedidos", ultimo.get("topic").getAsString());
        assertEquals(1, cierres.size());
        assertEquals(1000, cierres.get(0).intValue());
    }

    @Test
    public void alAbrir_programaElHeartbeatDePhoenix() {
        oyente.alAbrir();
        int enviadosEnJoin = enviados.size();

        programador.ejecutarVencidas(25_000L);

        JsonObject latido = mensaje(enviadosEnJoin);
        assertEquals("heartbeat", latido.get("event").getAsString());
        assertEquals("phoenix", latido.get("topic").getAsString());
    }

    @Test
    public void errorEnElReply_avisaAlObservadorDeEstado() {
        oyente.alRecibir("{\"event\":\"phx_reply\",\"payload\":{\"status\":\"error\","
                + "\"response\":{\"reason\":\"InvalidJWTExpiration: Token expired\"}}}");

        assertEquals(1, caidas.size());
        assertTrue(caidas.get(0).contains("InvalidJWTExpiration"));
    }

    // ------------------------------------------------------------------ dobletes

    /** {@link ScheduledExecutorService} mínimo: registra las tareas para correrlas a mano. */
    private static final class ProgramadorFalso implements ScheduledExecutorService {
        final List<Runnable> tareas = new ArrayList<>();

        void ejecutarVencidas(long ahora) {
            for (Runnable t : new ArrayList<>(tareas)) {
                tareas.remove(t);
                t.run();
            }
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            tareas.add(command);
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
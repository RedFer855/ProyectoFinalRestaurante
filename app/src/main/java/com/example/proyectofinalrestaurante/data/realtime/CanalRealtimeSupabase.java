package com.example.proyectofinalrestaurante.data.realtime;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * El canal de tiempo real hacia Supabase Realtime hablando el protocolo <b>Phoenix v1.0.0</b>
 * crudo (Plan Fase 3, §4.2; referencia: "Supabase Realtime sin SDK"). Solo OkHttp — que ya
 * está en el classpath como transitiva de Retrofit, verificado en E0 —, sin depender del SDK
 * Kotlin ([[ADR-002]]).
 *
 * <p><b>Señal, no dato.</b> La base hace {@code realtime.send('{"t":"pedido"}'::jsonb,
 * 'cambio', 'pedidos', true)} con un trigger {@code FOR EACH STATEMENT}; este canal se une al
 * tópico {@code realtime:pedidos} y convierte cada {@code broadcast} cuyo evento interno sea
 * {@code "cambio"} en una {@link SenalDeCambio}. No baja la fila: el que recibe la señal
 * dispara el {@code SincronizadorPedidos} por el camino HTTP ya probado. (ADR-008.)</p>
 *
 * <p>El <b>heartbeat</b> de Phoenix es distinto del {@code pingInterval} de OkHttp: uno es un
 * mensaje de protocolo y el otro de aplicación, hacen falta los dos. Acá el de Phoenix se
 * manda cada 25 s sobre el tópico literal {@code phoenix}.</p>
 *
 * <p>El socket, el reloj y el programador se inyectan para poder testear el curso del
 * protocolo sin red: la factura ("conectar/desconectar según {@code ProcessLifecycleOwner}
 * y el backoff de reconexión") la cobra {@code core/SupervisorTiempoReal} en E6.</p>
 */
public final class CanalRealtimeSupabase implements CanalTiempoReal {

    /** Tópico de Phoenix: literal, no canal. */
    static final String TOPICO_FENIX = "phoenix";
    /** Tópico del canal: siempre prefijado con {@code realtime:}. */
    static final String TOPICO_PEDIDOS = "realtime:pedidos";
    /** Evento externo con el que llega todo broadcast. */
    static final String EVENTO_BROADCAST = "broadcast";
    /** Evento interno que la base emite (la "señal"). */
    static final String EVENTO_CAMBIO = "cambio";
    static final String VSN_JSON = "1.0.0";

    /** Cada 25 s hay que mandar el heartbeat de Phoenix o el servidor corta. */
    static final long LATIDO_PERIODO_MS = 25_000L;

    private final String urlBase;
    private final String apikey;
    private final Gson gson;
    private final Supplier<Long> reloj;
    private final ScheduledExecutorService programador;
    private final FabricaDeSocket fabrica;

    /** El socket activo, o {@code null} si no hay. Sea el que sea, el oyente es {@code esta}. */
    @Nullable
    private SocketRealtime socket;
    private boolean conectado;

    private int ref = 0;

    private OyenteSenales oyenteSenales = s -> {
    };
    private OyenteEstado oyenteEstado = new OyenteEstado() {
        @Override
        public void alConectar() {
        }

        @Override
        public void alCaer(int codigo, String razon) {
        }
    };

    public CanalRealtimeSupabase(String urlWebSocketBase, String apikey, Gson gson,
                                 Supplier<Long> reloj, ScheduledExecutorService programador,
                                 FabricaDeSocket fabrica) {
        this.urlBase = Objects.requireNonNull(urlWebSocketBase, "urlWebSocketBase");
        this.apikey = apikey;
        this.gson = Objects.requireNonNull(gson, "gson");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.programador = Objects.requireNonNull(programador, "programador");
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
    }

    /** Instancia de producción, con el OkHttpClient propio del canal (P-028 lo exige). */
    public static CanalRealtimeSupabase desdeConfiguracion(Gson gson,
                                                           Supplier<Long> reloj,
                                                           ScheduledExecutorService programador) {
        String base = BuildConfig.SUPABASE_URL.isEmpty()
                ? "wss://supabase-no-configurado.invalid/realtime/v1/websocket"
                : BuildConfig.SUPABASE_URL.replace("https://", "wss://")
                        + "/realtime/v1/websocket";
        OkHttpClient cliente = new OkHttpClient.Builder()
                // ping de protocolo de OkHttp; el heartbeat de Phoenix (25 s) viaja por el canal.
                .pingInterval(LATIDO_PERIODO_MS, TimeUnit.MILLISECONDS)
                .build();
        return new CanalRealtimeSupabase(base, BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                gson, reloj, programador, new FabricaOkHttp(cliente));
    }

    // ------------------------------------------------------------------ CanalTiempoReal

    @Override
    public synchronized void conectar(@Nullable String token) {
        if (conectado || socket != null) {
            return;
        }
        String url = urlBase
                + (urlBase.contains("?") ? "&" : "?")
                + "apikey=" + apikey + "&vsn=" + VSN_JSON;
        socket = fabrica.abrir(url, new OyenteDeSocket() {
            @Override
            public void alAbrir() {
                synchronized (CanalRealtimeSupabase.this) {
                    conectado = true;
                }
                enviar(mensajeJoin(token));
                programarLatido();
                oyenteEstado.alConectar();
            }

            @Override
            public void alRecibir(String json) {
                procesarMensaje(json);
            }

            @Override
            public void alCerrar(int codigo, String razon) {
                synchronized (CanalRealtimeSupabase.this) {
                    conectado = false;
                }
                oyenteEstado.alCaer(codigo, razon);
            }
        });
    }

    @Override
    public synchronized void desconectar() {
        if (!conectado || socket == null) {
            return;
        }
        conectado = false;
        enviar(mensaje("phx_leave", TOPICO_PEDIDOS, Collections.emptyMap()));
        socket.cerrar(1000, "desconexion normal");
        socket = null;
    }

    @Override
    public void observarSenales(OyenteSenales oyente) {
        if (oyente != null) {
            oyenteSenales = oyente;
        }
    }

    @Override
    public void observarEstado(OyenteEstado oyente) {
        if (oyente != null) {
            oyenteEstado = oyente;
        }
    }

    // ------------------------------------------------------------------ protocolo

    private void enviar(String json) {
        SocketRealtime s = socket;
        if (s != null) {
            s.enviar(json);
        }
    }

    /** El {@code phx_join}: payload con {@code config} (broadcast + privado) y el token. */
    private String mensajeJoin(@Nullable String token) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("broadcast", Map.of("ack", false, "self", false));
        config.put("private", true);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("config", config);
        if (token != null) {
            payload.put("access_token", token);
        }
        return mensaje("phx_join", TOPICO_PEDIDOS, payload);
    }

    private String mensaje(String evento, String topico, Map<String, Object> payload) {
        String actual = String.valueOf(++ref);
        Map<String, Object> envoltorio = new LinkedHashMap<>();
        envoltorio.put("event", evento);
        envoltorio.put("topic", topico);
        envoltorio.put("payload", payload);
        envoltorio.put("ref", actual);
        envoltorio.put("join_ref", actual);
        return gson.toJson(envoltorio);
    }

    /** Heartbeat de Phoenix, re-programado mientras haya socket conectado. */
    private void programarLatido() {
        programador.schedule(() -> {
            if (conectado) {
                enviar(mensaje("heartbeat", TOPICO_FENIX, Collections.emptyMap()));
                programarLatido();
            }
        }, LATIDO_PERIODO_MS, TimeUnit.MILLISECONDS);
    }

    private void procesarMensaje(String json) {
        JsonObject raiz;
        try {
            raiz = gson.fromJson(json, JsonObject.class);
        } catch (RuntimeException ignorado) {
            return;
        }
        String evento = tex(raiz, "event");
        if (EVENTO_BROADCAST.equals(evento)) {
            JsonObject payload = raiz.getAsJsonObject("payload");
            if (EVENTO_CAMBIO.equals(tex(payload, "event"))) {
                oyenteSenales.alRecibir(SenalDeCambio.pedidos());
            }
        } else if ("phx_reply".equals(evento)) {
            JsonObject payload = raiz.getAsJsonObject("payload");
            if ("error".equals(tex(payload, "status"))) {
                String razon = tex(payload.getAsJsonObject("response"), "reason");
                oyenteEstado.alCaer(-1, razon == null ? "error en el join" : razon);
            }
        }
    }

    @Nullable
    private static String tex(@Nullable JsonObject objeto, String clave) {
        if (objeto == null || !objeto.has(clave) || objeto.get(clave).isJsonNull()) {
            return null;
        }
        return objeto.get(clave).getAsString();
    }

    // ------------------------------------------------------------------ excepciones del socket

    /** Abstracción mínima sobre el socket para poder testear el protocolo sin red. */
    public interface SocketRealtime {
        void enviar(String json);

        void cerrar(int codigo, String razon);
    }

    public interface OyenteDeSocket {
        void alAbrir();

        void alRecibir(String json);

        void alCerrar(int codigo, String razon);
    }

    /** Abre un socket dado un URL y un oyente; la forma de Inyectar un socket de prueba. */
    public interface FabricaDeSocket {
        SocketRealtime abrir(String url, OyenteDeSocket oyente);
    }

    /** La implementación real sobre {@code okhttp3.WebSocket} (la única que usa OkHttp). */
    public static final class FabricaOkHttp implements FabricaDeSocket {
        private final OkHttpClient cliente;

        public FabricaOkHttp(OkHttpClient cliente) {
            this.cliente = cliente;
        }

        @Override
        public SocketRealtime abrir(String url, OyenteDeSocket oyente) {
            Request peticion = new Request.Builder().url(url).build();
            WebSocket socket = cliente.newWebSocket(peticion, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    oyente.alAbrir();
                }

                @Override
                public void onMessage(WebSocket webSocket, String texto) {
                    oyente.alRecibir(texto);
                }

                @Override
                public void onClosed(WebSocket webSocket, int codigo, String razon) {
                    oyente.alCerrar(codigo, razon);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    oyente.alCerrar(-1, t == null ? "fallo de red" : t.getMessage());
                }
            });
            return new SocketOkHttp(socket);
        }
    }

    private static final class SocketOkHttp implements SocketRealtime {
        private final WebSocket socket;

        SocketOkHttp(WebSocket socket) {
            this.socket = socket;
        }

        @Override
        public void enviar(String json) {
            socket.send(json);
        }

        @Override
        public void cerrar(int codigo, String razon) {
            socket.close(codigo, razon);
        }
    }
}
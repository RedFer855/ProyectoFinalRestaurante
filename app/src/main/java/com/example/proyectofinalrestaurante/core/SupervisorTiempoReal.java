package com.example.proyectofinalrestaurante.core;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.realtime.CanalTiempoReal;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Orquesta el ciclo de vida del canal de tiempo real (Plan Fase 3, §4.2, E6).
 *
 * <ul>
 *   <li><b>Un socket por proceso.</b> Conecta cuando la app está en primer plano y hay sesión;
 *       desconecta limpio al pasar a segundo plano ({@code ProcessLifecycleOwner}).</li>
 *   <li><b>Backoff exponencial</b> {@code [1s, 2s, 5s, 10s, 30s]} con jitter en la
 *       reconexión, tope 30 s.</li>
 *   <li><b>Reconexión exitosa → fuerza una sincronización</b> (B12): el socket pudo estar caído
 *       y perderse señales, y el {@code SincronizadorPedidos} cubre el hueco por el camino HTTP
 *       ya probado.</li>
 *   <li><b>Token nulo → no conecta y no entra en bucle</b> (B13): ni al conectar ni en el
 *       reintento.</li>
 *   <li><b>{@code InvalidJWTExpiration} → no reintentar en bucle</b> (P-009): el JWT se venció y
 *       el canal no puede recuperarse solo; se desconecta y se queda en el disparador periódico.
 *       Límite conocido, no oculto.</li>
 * </ul>
 *
 * <p>Inyecta el {@link CanalTiempoReal}, el {@code ScheduledExecutorService}, el reloj, la
 * fuente de jitter y el {@code Runnable} de fuerza-de-sincronización para testear B12/B13 sin
 * red ni esperas reales.</p>
 */
public final class SupervisorTiempoReal {

    /** Backoff exponencial de reconexión, tope 30 s (Plan Fase 3, §4.2). */
    static final long[] BACKOFF_MS = {1_000L, 2_000L, 5_000L, 10_000L, 30_000L};

    /** Jitter sobre el backoff: 0–1s, para que 25 dispositivos no reapileten los reintentos. */
    static final long JITTER_MAX_MS = 1_000L;

    private final CanalTiempoReal canal;
    private final Supplier<String> token;
    private final ScheduledExecutorService programador;
    private final Supplier<Long> reloj;
    private final FuenteDeJitter jitter;
    private final Runnable alConectar;

    /** {@code true} mientras la app esté en primer plano (y haya habido sesión). */
    private boolean activo;
    /** Índice del paso de backoff; se reinicia con cada conexión exitosa. */
    private int reintentos;

    public SupervisorTiempoReal(CanalTiempoReal canal, Supplier<String> token,
                                ScheduledExecutorService programador, Supplier<Long> reloj,
                                FuenteDeJitter jitter, Runnable alConectar) {
        this.canal = Objects.requireNonNull(canal, "canal");
        this.token = Objects.requireNonNull(token, "token");
        this.programador = Objects.requireNonNull(programador, "programador");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.jitter = Objects.requireNonNull(jitter, "jitter");
        this.alConectar = alConectar;
        canal.observarEstado(new CanalTiempoReal.OyenteEstado() {
            @Override
            public void alConectar() {
                SupervisorTiempoReal.this.alConectar();
            }

            @Override
            public void alCaer(int codigo, String razon) {
                SupervisorTiempoReal.this.alCaer(razon);
            }
        });
    }

    /** Instancia lista para producción, con {@link Random} como fuente de jitter. */
    public static SupervisorTiempoReal conJitterAleatorio(CanalTiempoReal canal,
                                                          Supplier<String> token,
                                                          ScheduledExecutorService programador,
                                                          Supplier<Long> reloj,
                                                          Runnable alConectar) {
        return new SupervisorTiempoReal(canal, token, programador, reloj,
                new JitterAleatorio(), alConectar);
    }

    /** La app pasa a primer plano: intenta conectar si hay token (B13). */
    public void conectarConSesion() {
        activo = true;
        reintentos = 0;
        if (token.get() == null) {
            return; // B13: sin token no se conecta ni se programa un reintento.
        }
        canal.conectar(token.get());
    }

    /** La app pasa a segundo plano: desconexión limpia. */
    public void desconectar() {
        activo = false;
        reintentos = 0;
        canal.desconectar();
    }

    /** El canal logró abrir (o reabrir): reinicia el backoff y fuerza una sincronización. */
    private void alConectar() {
        reintentos = 0;
        if (alConectar != null) {
            alConectar.run();
        }
    }

    private void alCaer(@Nullable String razon) {
        if (!activo) {
            return;
        }
        if (esErrorDeJwt(razon)) {
            // P-009: el token vence y el canal no se recupera solo. No reentrar en bucle que
            // quemaría batería: se desconecta y se queda en el disparador periódico.
            canal.desconectar();
            return;
        }
        reintentos++;
        int paso = Math.min(reintentos, BACKOFF_MS.length) - 1;
        long retardo = BACKOFF_MS[paso] + jitter.siguiente((int) JITTER_MAX_MS + 1);
        programador.schedule(this::reconectar, retardo, TimeUnit.MILLISECONDS);
    }

    private void reconectar() {
        if (!activo) {
            return;
        }
        if (token.get() == null) {
            return; // B13: sin token el reintento no conecta y no vuelve a encadenar.
        }
        canal.conectar(token.get());
    }

    private static boolean esErrorDeJwt(@Nullable String razon) {
        if (razon == null) {
            return false;
        }
        return razon.contains("InvalidJWT") || razon.contains("MalformedJWT")
                || razon.contains("Unauthorized");
    }

    /** Fuente de jitter separada para fijar el retardo en los tests. */
    public interface FuenteDeJitter {
        /** Un entero en {@code [0, limiteExclusivo)}. */
        int siguiente(int limiteExclusivo);
    }

    /** Implementación por defecto (jitter aleatorio de producción). */
    static final class JitterAleatorio implements FuenteDeJitter {
        private final Random random = new Random();

        @Override
        public int siguiente(int limiteExclusivo) {
            return random.nextInt(limiteExclusivo);
        }
    }
}
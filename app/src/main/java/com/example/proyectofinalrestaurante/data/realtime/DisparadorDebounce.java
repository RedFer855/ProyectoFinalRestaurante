package com.example.proyectofinalrestaurante.data.realtime;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Colapsa la ráfaga de señales del canal en ≤ 2 llamadas a la acción (Plan Fase 3, §4.3, R5).
 *
 * <p>Es un <b>throttle con borde de salida</b> (leading + trailing), no un debounce literal:
 * <pre>
 *   señal 1              →  acción YA          (latencia sub-segundo en el caso normal)
 *   señales 2..n en 3 s  →  se tragan
 *   al cerrar la ventana →  acción UNA vez más (nada se pierde)
 * </pre>
 * Con 25 señales en 500 ms produce <b>2</b> llamadas, no 25 — cumple R5 (≤ 2) con latencia
 * de la primera y garantía de la última. El borde de salida existe porque {@code KEEP} de
 * WorkManager descarta con pérdida si la señal llega justo después de la última página.</p>
 *
 * <p>El <b>jitter</b> (0–1500 ms por defecto) se aplica a la llamada de cierre: los 25
 * dispositivos que reciben el mismo broadcast no apilan 25 {@code GET} idénticos a la vez,
 * se reparten.</p>
 *
 * <p>Es <b>Java puro</b> con el reloj, el programador y la fuente de aleatoriedad inyectados
 * por constructor; sin eso R5 no se puede testear sin dormir 3 segundos reales en la suite.</p>
 */
public final class DisparadorDebounce {

    /** Ventana del throttle (Plan Fase 3, §4.3): 3 segundos. */
    public static final long VENTANA_MS = 3_000L;

    /** Tope del jitter en la llamada de cierre: 0–1500 ms. */
    public static final long JITTER_MAX_MS = 1_500L;

    private final Supplier<Long> reloj;
    private final ScheduledExecutorService programador;
    private final long ventanaMs;
    private final long jitterMaxMs;
    private final FuenteAleatoria aleatoriedad;
    private final Runnable accion;

    /** Primera señal de la ráfaga actual ({@code -1} si no hay ninguna). */
    private long inicioRafaga = -1L;
    /** Hubo algún señal además de la primera (decide si se dispara el cierre). */
    private boolean huboCola;
    /** Token de la ráfaga actual, para que un cierre viejo no toque una ráfaga nueva. */
    private long rafagaActual = -1L;

    public DisparadorDebounce(@NonNull Supplier<Long> reloj,
                              @NonNull ScheduledExecutorService programador,
                              long ventanaMs, long jitterMaxMs,
                              @NonNull FuenteAleatoria aleatoriedad,
                              @NonNull Runnable accion) {
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.programador = Objects.requireNonNull(programador, "programador");
        this.ventanaMs = ventanaMs;
        this.jitterMaxMs = jitterMaxMs;
        this.aleatoriedad = Objects.requireNonNull(aleatoriedad, "aleatoriedad");
        this.accion = Objects.requireNonNull(accion, "accion");
    }

    /** Instancia listo para producción: ventana de 3 s, jitter 0–1500 ms, {@code Random}. */
    public static DisparadorDebounce deTresSegundos(@NonNull Supplier<Long> reloj,
                                                    @NonNull ScheduledExecutorService programador,
                                                    @NonNull Runnable accion) {
        return new DisparadorDebounce(reloj, programador, VENTANA_MS, JITTER_MAX_MS,
                new AleatorioRandom(), accion);
    }

    /**
     * Llega una señal. La primera de cada ráfaga dispara la acción de inmediato (leading);
     * las siguientes dentro de la ventana solo marcan que hubo tráfico, y el cierre
     * programado dispara una vez más si la ráfaga tuvo cola.
     */
    public void senal() {
        long ahora = reloj.get();
        if (!enRafaga(ahora)) {
            // Nueva ráfaga: dispara YA y programa el cierre con jitter.
            rafagaActual++;
            inicioRafaga = ahora;
            huboCola = false;
            accion.run();
            programaCierre(rafagaActual, ahora);
        } else {
            huboCola = true;
        }
    }

    private boolean enRafaga(long ahora) {
        return inicioRafaga >= 0 && (ahora - inicioRafaga) < ventanaMs;
    }

    private void programaCierre(long rafaga, long desde) {
        long retardo = ventanaMs + aleatoriedad.siguiente((int) jitterMaxMs + 1);
        programador.schedule(() -> cerrar(rafaga), retardo, TimeUnit.MILLISECONDS);
    }

    private void cerrar(long rafaga) {
        if (rafaga != rafagaActual || !huboCola) {
            return; // cierre de una ráfaga vieja o sin nada que entregar
        }
        huboCola = false;
        accion.run();
    }

    /** Fuente de aleatoriedad separada para poder fijar el jitter en los tests. */
    public interface FuenteAleatoria {
        /** Un entero en {@code [0, limiteExclusivo)}. */
        int siguiente(int limiteExclusivo);
    }

    /** Implementación por defecto sobre {@link Random}. */
    public static final class AleatorioRandom implements FuenteAleatoria {
        private final Random random = new Random();

        @Override
        public int siguiente(int limiteExclusivo) {
            return random.nextInt(limiteExclusivo);
        }
    }
}
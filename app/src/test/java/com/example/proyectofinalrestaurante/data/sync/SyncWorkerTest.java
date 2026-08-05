package com.example.proyectofinalrestaurante.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestWorkerBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * {@link SyncWorker} contra un worker real construido con {@code work-testing}.
 *
 * <p>Lo que de verdad se protege acá es el caso "sin token" (regresión de la Sesión
 * 2026-08-04). Devolver {@code Result.retry()} ahí dejaba el trabajo único vivo con
 * backoff, y como {@link SyncScheduler} encola con {@code ExistingWorkPolicy.KEEP}, todo
 * pedido posterior —incluido el sync-on-launch de cada ViewModel y el pull-to-refresh— se
 * descartaba en silencio. En instalación desde cero eso se veía como un Menú que nunca
 * cargaba.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SyncWorkerTest {

    private Context contexto;

    @Before
    public void prepararContexto() {
        contexto = RuntimeEnvironment.getApplication();
    }

    // ------------------------------------------------------------------ sin sesión

    @Test
    public void sinToken_terminaConExitoParaNoRetenerElTrabajoUnico() {
        SincronizadorEspia sincronizador = new SincronizadorEspia(ResultadoSync.ok());
        ObservadorEspia observador = new ObservadorEspia();

        ListenableWorker.Result resultado =
                correr(sinToken(), observador, sincronizador);

        // success() y no retry(): un worker que no puede hacer nada no debe quedarse con
        // el slot. Si volviera a ser retry(), KEEP tragaría el sync de después del login.
        assertEquals(ListenableWorker.Result.success(), resultado);
    }

    @Test
    public void sinToken_noLlamaANingunSincronizador() {
        SincronizadorEspia sincronizador = new SincronizadorEspia(ResultadoSync.ok());

        correr(sinToken(), new ObservadorEspia(), sincronizador);

        // Llamarlos daría 401 y le quemaría un intento a cada operación del outbox.
        assertEquals(0, sincronizador.veces);
    }

    @Test
    public void sinToken_noAvisaALaUiQueSincronizo() {
        ObservadorEspia observador = new ObservadorEspia();

        correr(sinToken(), observador, new SincronizadorEspia(ResultadoSync.ok()));

        // El return va antes de alIniciar(): la UI no debe ver un ciclo que no ocurrió.
        assertFalse(observador.inicio);
        assertFalse(observador.termino);
    }

    // ------------------------------------------------------------------ con sesión

    @Test
    public void conToken_corretodosLosSincronizadoresEnOrden() {
        SincronizadorEspia primero = new SincronizadorEspia(ResultadoSync.ok());
        SincronizadorEspia segundo = new SincronizadorEspia(ResultadoSync.ok());
        ObservadorEspia observador = new ObservadorEspia();

        ListenableWorker.Result resultado =
                correr(conToken(), observador, primero, segundo);

        assertEquals(ListenableWorker.Result.success(), resultado);
        assertEquals(1, primero.veces);
        assertEquals(1, segundo.veces);
        assertTrue(observador.inicio);
        assertTrue(observador.termino);
        assertEquals(null, observador.ultimoError);
    }

    @Test
    public void errorTransitorio_sigueDevolviendoRetry() {
        SincronizadorEspia falla = new SincronizadorEspia(ResultadoSync.transitorio("sin red"));
        SincronizadorEspia siguiente = new SincronizadorEspia(ResultadoSync.ok());

        ListenableWorker.Result resultado =
                correr(conToken(), new ObservadorEspia(), falla, siguiente);

        // El cambio de la Sesión 2026-08-04 fue SOLO para el caso sin token: un fallo de
        // red sí tiene que reintentarse, y la pasada se corta sin tocar al siguiente.
        assertEquals(ListenableWorker.Result.retry(), resultado);
        assertEquals(0, siguiente.veces);
    }

    @Test
    public void errorPermanente_terminaConExitoPeroAvisaElMensaje() {
        SincronizadorEspia falla =
                new SincronizadorEspia(ResultadoSync.permanente("el precio es inválido"));
        ObservadorEspia observador = new ObservadorEspia();

        ListenableWorker.Result resultado = correr(conToken(), observador, falla);

        // Reintentar no arreglaría un error permanente; se informa y la pasada termina.
        assertEquals(ListenableWorker.Result.success(), resultado);
        assertEquals("el precio es inválido", observador.ultimoError);
    }

    // ------------------------------------------------------------------ andamiaje

    /**
     * Construye y corre el worker. Se usa {@link TestWorkerBuilder} con una
     * {@link WorkerFactory} propia porque {@link SyncWorker} no tiene constructor vacío:
     * sus colaboradores se inyectan (igual que en producción lo hace {@code FactoryDeSync}).
     */
    private ListenableWorker.Result correr(Supplier<String> token,
                                           ObservadorSincronizacion observador,
                                           Sincronizador... sincronizadores) {
        List<Sincronizador> lista = Arrays.asList(sincronizadores);
        SyncWorker worker = TestWorkerBuilder.from(contexto, SyncWorker.class,
                        Executors.newSingleThreadExecutor())
                .setWorkerFactory(new WorkerFactory() {
                    @Nullable
                    @Override
                    public ListenableWorker createWorker(@NonNull Context contextoApp,
                                                         @NonNull String nombreClase,
                                                         @NonNull WorkerParameters parametros) {
                        return new SyncWorker(contextoApp, parametros, lista, token, observador);
                    }
                })
                .build();
        return worker.doWork();
    }

    private static Supplier<String> sinToken() {
        return () -> null;
    }

    private static Supplier<String> conToken() {
        return () -> "token-de-prueba";
    }

    /** Cuenta invocaciones y devuelve siempre el mismo resultado. */
    private static final class SincronizadorEspia implements Sincronizador {

        private final ResultadoSync resultado;
        private int veces;

        SincronizadorEspia(ResultadoSync resultado) {
            this.resultado = resultado;
        }

        @Override
        public String modulo() {
            return "espia";
        }

        @Override
        public ResultadoSync sincronizar() {
            veces++;
            return resultado;
        }
    }

    /** Registra si el worker anunció el ciclo de sincronización y con qué error. */
    private static final class ObservadorEspia implements ObservadorSincronizacion {

        private boolean inicio;
        private boolean termino;
        @Nullable private String ultimoError;
        private final List<String> avisos = new ArrayList<>();

        @Override
        public void alIniciar() {
            inicio = true;
            avisos.add("inicio");
        }

        @Override
        public void alTerminar(@Nullable String error) {
            termino = true;
            ultimoError = error;
            avisos.add("fin");
        }
    }
}

package com.example.proyectofinalrestaurante.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * {@link SyncScheduler} contra un {@link WorkManager} real (en memoria vía
 * {@code work-testing}), no un mock — así se prueba lo que de verdad queda encolado.
 *
 * <p>Ver la Sesión 2026-08-04: {@link SyncScheduler#programarPeriodico} es la mitad de
 * "casi-tiempo-real sin push" (la otra mitad, el resync al volver a primer plano vía
 * {@code ProcessLifecycleOwner}, vive en {@code SyncApplication} y no es testeable en JVM
 * pura porque depende de un {@code Activity} real entrando a primer plano).</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SyncSchedulerTest {

    private Context contexto;

    @Before
    public void inicializarWorkManagerDePrueba() {
        contexto = RuntimeEnvironment.getApplication();
        WorkManagerTestInitHelper.initializeTestWorkManager(contexto);
    }

    @Test
    public void programarPeriodico_encolaUnTrabajoPeriodicoUnico()
            throws ExecutionException, InterruptedException {
        SyncScheduler.programarPeriodico(contexto);

        List<WorkInfo> trabajos = WorkManager.getInstance(contexto)
                .getWorkInfosForUniqueWork(SyncScheduler.NOMBRE_UNICO_PERIODICO)
                .get();

        assertEquals(1, trabajos.size());
        assertFalse(trabajos.get(0).getState().isFinished());
    }

    @Test
    public void programarPeriodico_llamarloDosVecesNoDuplicaElTrabajo()
            throws ExecutionException, InterruptedException {
        SyncScheduler.programarPeriodico(contexto);
        SyncScheduler.programarPeriodico(contexto);

        List<WorkInfo> trabajos = WorkManager.getInstance(contexto)
                .getWorkInfosForUniqueWork(SyncScheduler.NOMBRE_UNICO_PERIODICO)
                .get();

        // ExistingPeriodicWorkPolicy.KEEP: la segunda llamada no debe agregar un segundo
        // trabajo ni reiniciar el que ya está encolado.
        assertEquals(1, trabajos.size());
    }

    @Test
    public void solicitar_yProgramarPeriodico_usanNombresDeTrabajoDistintos()
            throws ExecutionException, InterruptedException {
        SyncScheduler.solicitar(contexto);
        SyncScheduler.programarPeriodico(contexto);

        WorkManager gestor = WorkManager.getInstance(contexto);
        List<WorkInfo> unico = gestor.getWorkInfosForUniqueWork(SyncScheduler.NOMBRE_UNICO).get();
        List<WorkInfo> periodico =
                gestor.getWorkInfosForUniqueWork(SyncScheduler.NOMBRE_UNICO_PERIODICO).get();

        // Si compartieran namespace, encolar el periódico después del único lo pisaría
        // (o viceversa): cada uno tiene que verse solo a sí mismo.
        assertEquals(1, unico.size());
        assertEquals(1, periodico.size());
    }
}

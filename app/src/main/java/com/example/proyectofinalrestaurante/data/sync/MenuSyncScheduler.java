package com.example.proyectofinalrestaurante.data.sync;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Dispara la sincronización del Menú (Plan Fase 2b, §4.4).
 *
 * <p>Trabajo único: nunca hay dos workers compitiendo por la misma cola. Con
 * {@code ExistingWorkPolicy.KEEP}, si ya hay uno encolado o corriendo, el pedido nuevo se
 * ignora — el que está ya va a barrer la cola completa.</p>
 *
 * <p>Las restricciones y el backoff son la segunda línea de defensa: la primera es que el
 * propio sincronizador corta la pasada ante un error transitorio y el worker devuelve
 * {@code Result.retry()} (el outbox agota intentos por operación antes de darla por perdida).</p>
 */
public final class MenuSyncScheduler {

    /** Nombre del trabajo único; no debería colisionar con otros módulos. */
    static final String NOMBRE_UNICO = "sync-menu";

    private static final long BACKOFF_INICIAL_SEGUNDOS = 15;

    private MenuSyncScheduler() {
    }

    /** Encola (o mantiene) la sincronización pendiente. Se llama tras cada escritura local. */
    public static void solicitar(Context contexto) {
        Constraints restricciones = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest trabajo = new OneTimeWorkRequest.Builder(MenuSyncWorker.class)
                .setConstraints(restricciones)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INICIAL_SEGUNDOS,
                        TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(contexto)
                .enqueueUniqueWork(NOMBRE_UNICO, ExistingWorkPolicy.KEEP, trabajo);
    }
}

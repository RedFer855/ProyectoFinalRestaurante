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
 * Dispara la sincronización (Plan Fase 2b, §4.4).
 *
 * <p>Trabajo único para <b>todos</b> los módulos: nunca hay dos workers compitiendo por la
 * misma cola. Con {@code ExistingWorkPolicy.KEEP}, si ya hay uno encolado o corriendo, el
 * pedido nuevo se ignora — el que está ya va a barrer la cola completa.</p>
 *
 * <p>El nombre único se mantiene en {@code "sync-menu"} a propósito, aunque el worker ahora
 * sincronice también Empleados: cambiarlo dejaría huérfano el trabajo ya encolado en los
 * dispositivos que tengan la versión anterior instalada, y esas operaciones pendientes no se
 * drenarían nunca.</p>
 *
 * <p>Las restricciones y el backoff son la segunda línea de defensa: la primera es que el
 * propio sincronizador corta la pasada ante un error transitorio y el worker devuelve
 * {@code Result.retry()} (el outbox agota intentos por operación antes de darla por perdida).</p>
 */
public final class SyncScheduler {

    /**
     * Nombre del trabajo único. Ver arriba por qué conserva el nombre viejo: es una clave de
     * compatibilidad con lo que ya está encolado en dispositivos reales, no un descuido.
     */
    static final String NOMBRE_UNICO = "sync-menu";

    private static final long BACKOFF_INICIAL_SEGUNDOS = 15;

    private SyncScheduler() {
    }

    /** Encola (o mantiene) la sincronización pendiente. Se llama tras cada escritura local. */
    public static void solicitar(Context contexto) {
        Constraints restricciones = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest trabajo = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(restricciones)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INICIAL_SEGUNDOS,
                        TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(contexto)
                .enqueueUniqueWork(NOMBRE_UNICO, ExistingWorkPolicy.KEEP, trabajo);
    }
}

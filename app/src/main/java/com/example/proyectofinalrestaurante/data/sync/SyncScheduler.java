package com.example.proyectofinalrestaurante.data.sync;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
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
 *
 * <p><b>Casi-tiempo-real, no push (Sesión 2026-08-04):</b> el usuario pidió "escuchar
 * cambios" en vez de depender de que alguien entre a la pantalla. Un push real (Supabase
 * Realtime) es una decisión de fondo que además contradice
 * {@code ADR-002 - Supabase Auth via REST directo}: necesitaría el SDK que ese ADR evitó a
 * propósito, y una conexión persistente encaja mal con el requisito no funcional #1 ("Wi-Fi
 * intermitente", P-014). En su lugar: {@link #programarPeriodico} deja un
 * {@code PeriodicWorkRequest} corriendo mientras la app esté instalada, y
 * {@code SyncApplication} llama a {@link #solicitar} cada vez que la app vuelve a primer
 * plano ({@code ProcessLifecycleOwner}). Con eso ningún módulo depende ya de que el usuario
 * entre a su pantalla o deslice hacia abajo — la primera sincronización sí, sigue siendo
 * automática vía {@code XxxViewModel} (sync-on-launch), esto cubre lo que pasa después.</p>
 */
public final class SyncScheduler {

    /**
     * Nombre del trabajo único. Ver arriba por qué conserva el nombre viejo: es una clave de
     * compatibilidad con lo que ya está encolado en dispositivos reales, no un descuido.
     */
    static final String NOMBRE_UNICO = "sync-menu";

    /** Trabajo periódico, separado del de arriba: son namespaces distintos en WorkManager. */
    static final String NOMBRE_UNICO_PERIODICO = "sync-periodico";

    private static final long BACKOFF_INICIAL_SEGUNDOS = 15;

    /** 15 minutos es el mínimo que WorkManager admite para trabajo periódico. */
    private static final long INTERVALO_PERIODICO_MINUTOS = 15;

    private SyncScheduler() {
    }

    /** Encola (o mantiene) la sincronización pendiente. Se llama tras cada escritura local. */
    public static void solicitar(Context contexto) {
        OneTimeWorkRequest trabajo = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(restriccionDeRed())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INICIAL_SEGUNDOS,
                        TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(contexto)
                .enqueueUniqueWork(NOMBRE_UNICO, ExistingWorkPolicy.KEEP, trabajo);
    }

    /**
     * Deja una sincronización de fondo corriendo cada {@link #INTERVALO_PERIODICO_MINUTOS}
     * mientras la app esté instalada. Se llama una sola vez, desde
     * {@code SyncApplication.onCreate()}; {@code ExistingPeriodicWorkPolicy.KEEP} hace que
     * reinstalarla en cada arranque de proceso no la duplique ni le reinicie el conteo.
     */
    public static void programarPeriodico(Context contexto) {
        PeriodicWorkRequest trabajo = new PeriodicWorkRequest.Builder(
                SyncWorker.class, INTERVALO_PERIODICO_MINUTOS, TimeUnit.MINUTES)
                .setConstraints(restriccionDeRed())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INICIAL_SEGUNDOS,
                        TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(contexto)
                .enqueueUniquePeriodicWork(NOMBRE_UNICO_PERIODICO,
                        ExistingPeriodicWorkPolicy.KEEP, trabajo);
    }

    private static Constraints restriccionDeRed() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }
}

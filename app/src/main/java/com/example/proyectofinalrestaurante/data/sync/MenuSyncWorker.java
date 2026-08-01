package com.example.proyectofinalrestaurante.data.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.function.Supplier;

/**
 * El worker que drena el outbox y baja el delta (Plan Fase 2b, §5.3).
 *
 * <p>Sin sesión no toca la cola: si no hay token, devuelve {@code Result.retry()} sin llamar
 * al sincronizador (si se llamara, la primera llamada a {@code MenuRemoto} devolvería 401 y
 * le quemaría un intento a la operación sin necesidad).</p>
 *
 * <p>No depende de {@code SesionActual}: el token entra por un {@link Supplier} que inyecta
 * la factory. Tampoco conoce a la UI: el estado se avisa por {@link ObservadorSincronizacion},
 * que el repositorio del Menú implementa.</p>
 */
public final class MenuSyncWorker extends Worker {

    private final SincronizadorMenu sincronizador;
    private final Supplier<String> proveedorToken;
    private final ObservadorSincronizacion observador;

    public MenuSyncWorker(@NonNull Context context, @NonNull WorkerParameters params,
                          @NonNull SincronizadorMenu sincronizador,
                          @NonNull Supplier<String> proveedorToken,
                          @NonNull ObservadorSincronizacion observador) {
        super(context, params);
        this.sincronizador = sincronizador;
        this.proveedorToken = proveedorToken;
        this.observador = observador;
    }

    @NonNull
    @Override
    public Result doWork() {
        if (proveedorToken.get() == null) {
            return Result.retry();
        }
        observador.alIniciar();
        ResultadoSync resultado = sincronizador.sincronizar();
        if (resultado.esTransitorio()) {
            // Se va a reintentar con backoff; no hay error permanente que mostrar todavía.
            observador.alTerminar(null);
            return Result.retry();
        }
        observador.alTerminar(resultado.getMensaje());
        return Result.success();
    }
}

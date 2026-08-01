package com.example.proyectofinalrestaurante.data.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.function.Supplier;

/**
 * El worker <b>único</b> que sincroniza todos los módulos (Plan Fase 2b, §5.3).
 *
 * <p>Nació como {@code MenuSyncWorker}. Al migrar Empleados a offline-first se renombró en
 * vez de agregar un segundo worker: la regla 3 de {@code Offline-First con Room y Outbox} es
 * explícita en que debe haber uno solo, para que nunca haya dos drenando la misma cola en
 * paralelo. Cada módulo aporta su {@link Sincronizador} y se corren en orden.</p>
 *
 * <p>Sin sesión no toca la cola: si no hay token, devuelve {@code Result.retry()} sin llamar
 * a ningún sincronizador (si se llamara, la primera llamada de red devolvería 401 y le
 * quemaría un intento a la operación sin necesidad).</p>
 *
 * <p>No depende de {@code SesionActual}: el token entra por un {@link Supplier} que inyecta
 * la factory. Tampoco conoce a la UI: el estado se avisa por
 * {@link ObservadorSincronizacion}, que implementa el repositorio de cada módulo.</p>
 */
public final class SyncWorker extends Worker {

    private final List<Sincronizador> sincronizadores;
    private final Supplier<String> proveedorToken;
    private final ObservadorSincronizacion observador;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params,
                      @NonNull List<Sincronizador> sincronizadores,
                      @NonNull Supplier<String> proveedorToken,
                      @NonNull ObservadorSincronizacion observador) {
        super(context, params);
        this.sincronizadores = sincronizadores;
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

        String errorPermanente = null;
        for (Sincronizador sincronizador : sincronizadores) {
            ResultadoSync resultado = sincronizador.sincronizar();
            if (resultado.esTransitorio()) {
                // Se corta la pasada entera: el reintento la repite desde el principio, y
                // seguir con el módulo siguiente sobre una red que ya falló solo gasta
                // intentos. Se va a reintentar con backoff, así que todavía no hay error
                // permanente que mostrarle al usuario.
                observador.alTerminar(null);
                return Result.retry();
            }
            if (resultado.esPermanente() && resultado.getMensaje() != null) {
                // El último gana: mostrar dos errores a la vez en un banner no ayuda, y el
                // detalle de cada operación ya quedó en su fila (estado ERROR).
                errorPermanente = resultado.getMensaje();
            }
        }

        observador.alTerminar(errorPermanente);
        return Result.success();
    }
}

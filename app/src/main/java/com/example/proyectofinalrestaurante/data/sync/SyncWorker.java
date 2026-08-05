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
 * <p>Sin sesión no toca la cola: si no hay token, termina sin llamar a ningún sincronizador
 * (si se llamara, la primera llamada de red devolvería 401 y le quemaría un intento a la
 * operación sin necesidad).</p>
 *
 * <p><b>Y termina con {@code success()}, no con {@code retry()} (2026-08-04).</b> Devolver
 * {@code retry()} dejaba el trabajo único vivo en estado {@code ENQUEUED} con backoff, y
 * como {@code SyncScheduler} encola con {@link androidx.work.ExistingWorkPolicy#KEEP},
 * <b>todo pedido posterior se descartaba en silencio</b>: el sync-on-launch de cada
 * ViewModel y hasta el pull-to-refresh. En una instalación desde cero eso se veía como un
 * Menú que nunca cargaba, porque el trabajo se encolaba en la pantalla de login —todavía
 * sin sesión— y envenenaba el slot antes de que el usuario llegara a ninguna pantalla.</p>
 *
 * <p>El invariante correcto es: <b>un worker que no puede hacer nada no debe retener el
 * trabajo único.</b> Sin token no hay trabajo posible, así que la pasada terminó. No se
 * pierde nada: las operaciones viven en {@code operaciones_pendientes} (Room), lo que se
 * descarta es el disparador, y hay cuatro caminos que lo recrean (foreground, periódico,
 * el {@code sincronizar()} de cada ViewModel y cada escritura local).</p>
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
            // Ver el Javadoc: success() y no retry(), para no dejar el trabajo único
            // ocupado y hacer que KEEP descarte los pedidos que sí van a tener sesión.
            // El return va antes de alIniciar(), así que la UI nunca ve un
            // "sincronizado" que no ocurrió.
            return Result.success();
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

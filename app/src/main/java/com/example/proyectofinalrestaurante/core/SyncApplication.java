package com.example.proyectofinalrestaurante.core;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;

import com.example.proyectofinalrestaurante.data.local.AppDatabase;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.repository.MenuRemoto;
import com.example.proyectofinalrestaurante.data.sync.MenuSyncWorker;
import com.example.proyectofinalrestaurante.data.sync.ObservadorSincronizacion;
import com.example.proyectofinalrestaurante.data.sync.SincronizadorMenu;
import com.example.proyectofinalrestaurante.domain.model.Sesion;

/**
 * Application = composition root del Menú (Plan Fase 2b, E5/E6).
 *
 * <p>Implementa {@link Configuration.Provider} para que WorkManager use la
 * {@link WorkerFactory} propia: es la única forma de construir {@link MenuSyncWorker} con el
 * {@link SincronizadorMenu} real (DAOs de Room + {@link MenuRemoto} con el token de la
 * sesión) sin recurrir a un constructor vacío ni a singletons escondidos.</p>
 *
 * <p>La base queda acá como singleton del proceso, accesible por el repositorio del Menú
 * (E6) cuando lo construya el ViewModelFactory.</p>
 */
public final class SyncApplication extends Application implements Configuration.Provider {

    private static final String NOMBRE_BASE = "restaurante.db";

    private AppDatabase baseDeDatos;

    /**
     * Quién avisa a la UI del estado de la sincronización. Por defecto no hace nada; el
     * repositorio del Menú (E6) lo reemplaza con el que alimenta {@code EstadoSincronizacion}.
     */
    private static volatile ObservadorSincronizacion observadorSincronizacion =
            ObservadorSincronizacion.NINGUNO;

    public static void setObservadorSincronizacion(ObservadorSincronizacion observador) {
        observadorSincronizacion = observador;
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(new FactoryDeSync(this))
                .build();
    }

    public AppDatabase baseDeDatos() {
        if (baseDeDatos == null) {
            synchronized (this) {
                if (baseDeDatos == null) {
                    baseDeDatos = Room.databaseBuilder(this, AppDatabase.class, NOMBRE_BASE).build();
                }
            }
        }
        return baseDeDatos;
    }

    private static final class FactoryDeSync extends WorkerFactory {

        private final SyncApplication aplicacion;

        FactoryDeSync(SyncApplication aplicacion) {
            this.aplicacion = aplicacion;
        }

        @Nullable
        @Override
        public ListenableWorker createWorker(@NonNull Context contexto,
                                             @NonNull String nombreClase,
                                             @NonNull WorkerParameters parametros) {
            if (!MenuSyncWorker.class.getName().equals(nombreClase)) {
                return null;
            }
            AppDatabase base = aplicacion.baseDeDatos();
            MenuRemoto remoto = new MenuRemoto(SupabaseClient.getMenuApi(),
                    SupabaseClient.getStorageApi(), SyncApplication::tokenDeLaSesion);
            SincronizadorMenu sincronizador = new SincronizadorMenu(remoto,
                    new Outbox(base.operacionPendienteDao()),
                    base.platilloDao(), base.categoriaDao(), base.sincronizacionDao(),
                    contexto.getFilesDir());
            return new MenuSyncWorker(contexto, parametros, sincronizador,
                    SyncApplication::tokenDeLaSesion, observadorSincronizacion);
        }
    }

    @Nullable
    private static String tokenDeLaSesion() {
        Sesion sesion = SesionActual.obtener();
        return sesion == null ? null : sesion.getAccessToken();
    }
}

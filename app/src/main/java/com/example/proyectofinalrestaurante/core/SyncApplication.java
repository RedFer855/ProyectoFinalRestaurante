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
import com.example.proyectofinalrestaurante.data.local.Migraciones;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.repository.EmpleadoRemoto;
import com.example.proyectofinalrestaurante.data.repository.MenuRemoto;
import com.example.proyectofinalrestaurante.data.sync.ObservadorSincronizacion;
import com.example.proyectofinalrestaurante.data.sync.Sincronizador;
import com.example.proyectofinalrestaurante.data.sync.SincronizadorEmpleados;
import com.example.proyectofinalrestaurante.data.sync.SincronizadorMenu;
import com.example.proyectofinalrestaurante.data.sync.SyncWorker;
import com.example.proyectofinalrestaurante.domain.model.Sesion;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application = composition root de la app (Plan Fase 2b, E5/E6).
 *
 * <p>Implementa {@link Configuration.Provider} para que WorkManager use la
 * {@link WorkerFactory} propia: es la única forma de construir {@link SyncWorker} con los
 * sincronizadores reales (DAOs de Room + los clientes remotos con el token de la sesión) sin
 * recurrir a un constructor vacío ni a singletons escondidos.</p>
 *
 * <p>La base queda acá como singleton del proceso, accesible por los repositorios cuando los
 * construya cada ViewModelFactory.</p>
 */
public final class SyncApplication extends Application implements Configuration.Provider {

    private static final String NOMBRE_BASE = "restaurante.db";

    private AppDatabase baseDeDatos;

    /**
     * Quién avisa a la UI del estado de la sincronización, <b>por módulo</b>.
     *
     * <p>Es un mapa y no un solo observador porque hay dos repositorios local-first (Menú y
     * Empleados) y cada uno alimenta su propio {@code EstadoSincronizacion}. La clave por
     * módulo además evita el duplicado obvio: cada rotación de pantalla reconstruye el
     * repositorio y vuelve a registrarse, y con una lista se irían acumulando observadores
     * muertos.</p>
     */
    private static final Map<String, ObservadorSincronizacion> OBSERVADORES =
            new ConcurrentHashMap<>();

    public static void registrarObservador(String modulo, ObservadorSincronizacion observador) {
        OBSERVADORES.put(modulo, observador);
    }

    /** Avisa a todos los módulos registrados. Si no hay ninguno, no hace nada. */
    private static ObservadorSincronizacion observadorDeTodos() {
        return new ObservadorSincronizacion() {
            @Override
            public void alIniciar() {
                for (ObservadorSincronizacion observador : instantanea()) {
                    observador.alIniciar();
                }
            }

            @Override
            public void alTerminar(@Nullable String ultimoError) {
                for (ObservadorSincronizacion observador : instantanea()) {
                    observador.alTerminar(ultimoError);
                }
            }

            private Collection<ObservadorSincronizacion> instantanea() {
                return OBSERVADORES.values();
            }
        };
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
                    baseDeDatos = Room.databaseBuilder(this, AppDatabase.class, NOMBRE_BASE)
                            // Migración explícita: fallbackToDestructiveMigration() borraría
                            // los cambios que el usuario todavía no subió.
                            .addMigrations(Migraciones.DE_1_A_2)
                            .build();
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
            if (!SyncWorker.class.getName().equals(nombreClase)) {
                return null;
            }
            AppDatabase base = aplicacion.baseDeDatos();

            MenuRemoto menuRemoto = new MenuRemoto(SupabaseClient.getMenuApi(),
                    SupabaseClient.getStorageApi(), SyncApplication::tokenDeLaSesion);
            Sincronizador menu = new SincronizadorMenu(menuRemoto,
                    new Outbox(base.operacionPendienteDao(), TipoOperacion.Modulo.MENU),
                    base.platilloDao(), base.categoriaDao(), base.sincronizacionDao(),
                    contexto.getFilesDir());

            EmpleadoRemoto empleadoRemoto = new EmpleadoRemoto(
                    SupabaseClient.getEmpleadoApi(), SyncApplication::tokenDeLaSesion);
            Sincronizador empleados = new SincronizadorEmpleados(empleadoRemoto,
                    new Outbox(base.operacionPendienteDao(), TipoOperacion.Modulo.EMPLEADOS),
                    base.empleadoDao(), base.sincronizacionDao());

            return new SyncWorker(contexto, parametros, Arrays.asList(menu, empleados),
                    SyncApplication::tokenDeLaSesion, observadorDeTodos());
        }
    }

    @Nullable
    private static String tokenDeLaSesion() {
        Sesion sesion = SesionActual.obtener();
        return sesion == null ? null : sesion.getAccessToken();
    }
}

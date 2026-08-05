package com.example.proyectofinalrestaurante.core;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.room.Room;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;

import com.example.proyectofinalrestaurante.data.local.AppDatabase;
import com.example.proyectofinalrestaurante.data.local.Migraciones;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.realtime.CanalRealtimeSupabase;
import com.example.proyectofinalrestaurante.data.repository.ClienteRemoto;
import com.example.proyectofinalrestaurante.data.repository.EmpleadoRemoto;
import com.example.proyectofinalrestaurante.data.repository.MenuRemoto;
import com.example.proyectofinalrestaurante.data.repository.MesaRemoto;
import com.example.proyectofinalrestaurante.data.repository.PedidoRemoto;
import com.example.proyectofinalrestaurante.data.repository.SesionLocal;
import com.example.proyectofinalrestaurante.data.sync.ObservadorSincronizacion;
import com.example.proyectofinalrestaurante.data.sync.Sincronizador;
import com.example.proyectofinalrestaurante.data.sync.SincronizadorClientes;
import com.example.proyectofinalrestaurante.data.sync.SincronizadorEmpleados;
import com.example.proyectofinalrestaurante.data.sync.SincronizadorMenu;
import com.example.proyectofinalrestaurante.data.sync.SincronizadorMesas;
import com.example.proyectofinalrestaurante.data.sync.SincronizadorPedidos;
import com.example.proyectofinalrestaurante.data.sync.SyncScheduler;
import com.example.proyectofinalrestaurante.data.sync.SyncWorker;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.example.proyectofinalrestaurante.domain.repository.SesionRepository;
import com.google.gson.Gson;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
    private SupervisorTiempoReal supervisorTiempoReal;
    private ScheduledExecutorService programadorTiempoReal;
    private volatile SesionRepository sesionRepository;

    /**
     * Quién avisa a la UI del estado de la sincronización, <b>por módulo</b>.
     *
     * <p>Es un mapa y no un solo observador porque hay varios repositorios local-first (Menú,
     * Empleados, Mesas) y cada uno alimenta su propio {@code EstadoSincronizacion}. La clave por
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

    @Override
    public void onCreate() {
        super.onCreate();
        // P-009: hidratar SesionActual ANTES que cualquier otra cosa. El guard de más abajo
        // (SesionActual.obtener() != null) y LoginActivity.onCreate() ya preguntan por una
        // sesión en este mismo método — sin esto, la respuesta siempre era "no" al arrancar
        // y había que volver a loguearse en cada apertura de la app.
        Sesion sesionPersistida = sesionRepository().leer();
        if (sesionPersistida != null) {
            SesionActual.guardar(sesionPersistida);
        }
        // Casi-tiempo-real sin push (Sesión 2026-08-04, ver el Javadoc de SyncScheduler):
        // sync de fondo cada 15 min mientras la app esté instalada...
        SyncScheduler.programarPeriodico(this);
        // ...y un empujón extra cada vez que la app vuelve a primer plano, para no esperar
        // hasta el próximo tick periódico si el usuario reabre la app después de un rato.
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                // Solo con sesión: en una instalación desde cero este onStart ocurre en la
                // pantalla de login, y encolar ahí un trabajo que no puede hacer nada era
                // parte de por qué el Menú no cargaba (ver el Javadoc de SyncWorker).
                // MainActivity dispara la sincronización apenas hay sesión.
                if (SesionActual.obtener() != null) {
                    SyncScheduler.solicitar(SyncApplication.this);
                    // Un socket por proceso (Plan Fase 3, §4.2): se une al canal cuando la app
                    // vuelve a primer plano y hay sesión; al ir a segundo plano se desconecta.
                    supervisorTiempoReal().conectarConSesion();
                }
            }

            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                supervisorTiempoReal().desconectar();
            }
        });
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
                            .addMigrations(Migraciones.DE_1_A_2, Migraciones.DE_2_A_3,
                                    Migraciones.DE_3_A_4, Migraciones.DE_4_A_5)
                            .build();
                }
            }
        }
        return baseDeDatos;
    }

    /**
     * El supervisor de tiempo real, único por proceso (Plan Fase 3, E6). El programador de
     * reintentos es el mismo que alimenta el heartbeat del canal: un solo hilo para todo el
     * real-time. La reconexión exitosa fuerza una sincronización (B12) porque las señales
     * pudieron perderse mientras el socket estuvo caído.
     */
    public SupervisorTiempoReal supervisorTiempoReal() {
        if (supervisorTiempoReal == null) {
            synchronized (this) {
                if (supervisorTiempoReal == null) {
                    ScheduledExecutorService programador = programadorTiempoReal();
                    CanalRealtimeSupabase canal = CanalRealtimeSupabase.desdeConfiguracion(
                            new Gson(), System::currentTimeMillis, programador);
                    supervisorTiempoReal = SupervisorTiempoReal.conJitterAleatorio(canal,
                            SyncApplication::tokenDeLaSesion, programador,
                            System::currentTimeMillis, () -> SyncScheduler.solicitar(this));
                }
            }
        }
        return supervisorTiempoReal;
    }

    private ScheduledExecutorService programadorTiempoReal() {
        if (programadorTiempoReal == null) {
            synchronized (this) {
                if (programadorTiempoReal == null) {
                    programadorTiempoReal = Executors.newScheduledThreadPool(1);
                }
            }
        }
        return programadorTiempoReal;
    }

    /** Singleton del proceso (P-009), mismo patrón de doble chequeo que {@link #baseDeDatos()}. */
    public SesionRepository sesionRepository() {
        if (sesionRepository == null) {
            synchronized (this) {
                if (sesionRepository == null) {
                    sesionRepository = new SesionLocal(new AlmacenSeguro(this));
                }
            }
        }
        return sesionRepository;
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
            // P-009: un único ProveedorDeToken para toda la pasada — su lock de single-flight
            // solo tiene sentido si los cinco (cuatro remotos + el propio SyncWorker) lo
            // comparten. Antes cada uno leía SesionActual directo con
            // SyncApplication::tokenDeLaSesion, que nunca refrescaba nada.
            ProveedorDeToken proveedorDeToken = new ProveedorDeToken(
                    SupabaseClient.getAuthApi(), aplicacion.sesionRepository());

            MenuRemoto menuRemoto = new MenuRemoto(SupabaseClient.getMenuApi(),
                    SupabaseClient.getStorageApi(), proveedorDeToken);
            Sincronizador menu = new SincronizadorMenu(menuRemoto,
                    new Outbox(base.operacionPendienteDao(), TipoOperacion.Modulo.MENU),
                    base.platilloDao(), base.categoriaDao(), base.sincronizacionDao(),
                    contexto.getFilesDir(), base::runInTransaction);

            EmpleadoRemoto empleadoRemoto = new EmpleadoRemoto(
                    SupabaseClient.getEmpleadoApi(), proveedorDeToken);
            Sincronizador empleados = new SincronizadorEmpleados(empleadoRemoto,
                    new Outbox(base.operacionPendienteDao(), TipoOperacion.Modulo.EMPLEADOS),
                    base.empleadoDao(), base.sincronizacionDao(), base::runInTransaction);

            MesaRemoto mesaRemoto = new MesaRemoto(
                    SupabaseClient.getMesaApi(), proveedorDeToken);
            Sincronizador mesas = new SincronizadorMesas(mesaRemoto,
                    new Outbox(base.operacionPendienteDao(), TipoOperacion.Modulo.MESAS),
                    base.mesaDao(), base.sincronizacionDao(), base::runInTransaction);

            ClienteRemoto clienteRemoto = new ClienteRemoto(
                    SupabaseClient.getClienteApi(), proveedorDeToken);
            Sincronizador clientes = new SincronizadorClientes(clienteRemoto,
                    new Outbox(base.operacionPendienteDao(), TipoOperacion.Modulo.CLIENTES),
                    base.clienteDao(), base.sincronizacionDao(), base::runInTransaction);

            // Fase 3: Pedidos se suma a la lista del worker. En frío (sin marca) pide desde
            // ahora − 48 h (R6 también del lado servidor); el delta va al molde del Menú.
            // El token se comparte con el resto de la pasada: el ProveedorDeToken refresca.
            PedidoRemoto pedidoRemoto = new PedidoRemoto(
                    SupabaseClient.getPedidoApi(), proveedorDeToken);
            Sincronizador pedidos = new SincronizadorPedidos(pedidoRemoto,
                    new Outbox(base.operacionPendienteDao(), TipoOperacion.Modulo.PEDIDOS),
                    base.pedidoDao(), base.notificacionDao(), base.sincronizacionDao(),
                    base::runInTransaction, SyncApplication::pisoDeArranqueDePedidos,
                    System::currentTimeMillis);

            return new SyncWorker(contexto, parametros,
                    Arrays.asList(menu, empleados, mesas, clientes, pedidos),
                    proveedorDeToken, observadorDeTodos());
        }
    }

    /** Piso de arranque en frío del delta de Pedidos: ahora − 48 h (Plan Fase 3, §4.4). */
    private static String pisoDeArranqueDePedidos() {
        return Instant.now().minus(48, ChronoUnit.HOURS).toString();
    }

    /**
     * Token de la sesión en memoria. Tras P-009 solo lo usa {@link SupervisorTiempoReal} para
     * el WebSocket del canal; el {@link SyncWorker} pasó a {@link ProveedorDeToken}, que
     * refresca el JWT antes de cada pasada.
     */
    @Nullable
    private static String tokenDeLaSesion() {
        Sesion sesion = SesionActual.obtener();
        return sesion == null ? null : sesion.getAccessToken();
    }
}
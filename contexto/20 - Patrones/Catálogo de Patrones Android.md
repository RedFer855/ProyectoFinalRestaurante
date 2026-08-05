---
title: "Catálogo de Patrones Android"
tags:
  - patron
  - catalogo
  - android
date: 2026-07-29
lifecycle: verified
---

# Catálogo de Patrones Android

> [!abstract] Para qué sirve esta nota
> No es un repaso teórico de patrones de diseño. Es la lista de **cómo se llaman y dónde van** en este proyecto, para que cualquier agente produzca el mismo nombre ante el mismo problema. Cada patrón trae usos concretos del dominio *restaurante*.

---

## 1. MVVM — un ViewModel por pantalla, nunca por componente

1. `LoginViewModel` expone `LiveData<LoginUiState>` y `login(correo, contrasenia)`.
2. `MenuListViewModel` combina Room + estado de red en un solo `UiState`.
3. `DetalleProductoViewModel` recibe el `id` por `SavedStateHandle`, **nunca** por setter público.
4. `PedidoViewModel` con alcance de grafo de navegación, compartido entre las pantallas del flujo de pedido.
5. `PerfilViewModel` con `SavedStateHandle` para sobrevivir a la muerte de proceso.

Ver [[MVVM en Android (ViewModel + LiveData)]].

## 2. MVI / UDF — estado inmutable

1. `LoginUiState` inmutable con `cargando`, `datos`, `error`, `navegar`.
2. Acción modelada como método: `onCorreoChanged`, `onSubmitClicked`, `onReintentarClicked`.
3. Evento de un solo disparo con `Event<T>` + `getContentIfNotHandled()`.
4. Reductor puro `LoginReducer.reduce(estado, resultado)`, testeable sin Android.
5. `SyncUiState` con `enum Estado {IDLE, SINCRONIZANDO, ERROR_RED, CONFLICTO}` **en vez de 4 booleanos**.

Ver [[UiState Inmutable y Flujo Unidireccional]].

## 3. Clean Architecture — frontera explícita

1. `domain/usecase/RegistrarPedidoUseCase` sin un solo import de Android.
2. `domain/repository/PedidoRepository` (interfaz) implementada por `data/repository/PedidoRepositoryImpl`.
3. `data/mapper/PedidoMapper` convierte `PedidoDto` ↔ `PedidoEntity` ↔ `Pedido`.
4. `core/common/Result<T>` cruzando capas (nunca excepciones como control de flujo).
5. `RegistrarPedidoUseCaseTest` corriendo en JVM pura con `FakePedidoRepository`.

Ver [[Clean Architecture]].

## 4. Repository — única fuente de verdad = base local

1. `ProductoRepositoryImpl` expone `LiveData<List<Producto>>` desde Room y refresca de la red en background.
2. `AuthRepositoryImpl` centraliza token, refresh y cierre de sesión.
3. `ConfiguracionRepositoryImpl` sobre DataStore.
4. `SyncRepositoryImpl` encola operaciones pendientes cuando no hay red.
5. `ReporteRepositoryImpl` con caché en memoria + TTL para consultas costosas.

Ver [[Repository Pattern]].

## 5. Data Source — separar local de remoto

1. `ProductoLocalDataSource` — envuelve el DAO, **nunca lo expone**.
2. `ProductoRemoteDataSource` — envuelve `ApiService`, traduce HTTP a `AppException`.
3. `ArchivoRemoteDataSource` para Supabase Storage (fotos de platillos).
4. `SesionLocal` sobre `AlmacenSeguro` (Android Keystore directo — no `EncryptedSharedPreferences`, deprecado). Ver [[Sesión persistida con Android Keystore]].
5. `TiempoDataSource` — abstrae `System.currentTimeMillis()` para poder testear.

## 6. Use Case / Command — un verbo, un `execute`

`ObtenerMenuUseCase` · `RegistrarPedidoUseCase` · `SincronizarPendientesUseCase` · `CerrarSesionUseCase` · `ValidarDisponibilidadUseCase`.

Todos con **un único método público** y dependencias por constructor.

## 7. Singleton — vía DI, nunca `static`

1. `OkHttpClient` único (`@Singleton @Provides`).
2. `AppDatabase`.
3. `AppExecutors` (io / computation / main).
4. `NetworkMonitor` registrado una vez con `ConnectivityManager.NetworkCallback`.
5. `Moshi`/`Gson` compartido.

> [!bug] Prohibido
> `public static Context sContext;` · `getInstance()` que recibe un `Context` de Activity.

## 8. Factory

1. `ViewModelProvider.Factory` (generado por Hilt con `@HiltViewModel`).
2. `HiltWorkerFactory` para inyectar en `Worker`.
3. `AppException.from(Throwable)` que clasifica el error.
4. `NotificationChannelFactory` por tipo de canal (comanda lista, pedido cancelado).
5. `RecyclerViewHolderFactory` para listas con múltiples `viewType`.

## 9. Observer

1. `LiveData.observe(getViewLifecycleOwner(), this::render)` — **siempre** `viewLifecycleOwner` en Fragments, nunca `this`.
2. `DefaultLifecycleObserver` en vez de sobrescribir `onResume`.
3. `NetworkCallback` para reaccionar a cambios de conectividad.
4. `WorkManager.getWorkInfoByIdLiveData()` para el progreso de sync.
5. Room devolviendo `LiveData` que se re-emite ante cambios en la tabla.

## 10. Strategy

1. `RetryPolicy` (`ExponentialBackoffRetryPolicy`, `NoRetryPolicy`).
2. `ConflictResolver` (`LastWriteWins`, `ServerWins`, `ManualMerge`).
3. `ImageCompressionStrategy` según RAM/red del dispositivo.
4. `SyncStrategy` (`FullSync`, `DeltaSync`).
5. `AuthStrategy` (`EmailPassword`, `OAuthGoogle`, `Anonimo`).

## 11. Adapter

1. `ProductoListAdapter extends ListAdapter<Producto, VH>` con `DiffUtil.ItemCallback`.
2. Mapper DTO→dominio (adaptador de forma de datos).
3. `CallAdapter` de Retrofit para `ListenableFuture`.
4. `TypeConverter` de Room (`Instant` ↔ `long`).
5. `ConcatAdapter` para cabecera + lista + footer de carga.

## 12. Builder

1. `OkHttpClient.Builder` centralizado.
2. `Room.databaseBuilder` con migraciones explícitas.
3. `NotificationCompat.Builder`.
4. `Constraints.Builder` de WorkManager.
5. `LoginUiState.Builder` para copias inmutables (`estado.toBuilder().cargando(true).build()`).

## 13. Facade

1. `SyncManager` que oculta WorkManager + repos + política de reintento.
2. `AuthManager` (login, refresh, logout, expiración).
3. `AnalyticsFacade` — una interfaz, cero acoplamiento al SDK.
4. `MediaManager` (cámara, galería, compresión, subida).
5. `PermissionManager` sobre `ActivityResultContracts`.

## 14. Decorator / Chain of Responsibility — interceptores OkHttp

1. `AuthInterceptor` — inyecta `apikey` + `Bearer`.
2. `TokenRefreshAuthenticator` — 401 → refresh → reintento.
3. `RetryInterceptor` — backoff exponencial + jitter.
4. `CacheControlInterceptor` — fuerza caché cuando no hay red.
5. `LoggingInterceptor` — **solo en `debug`** y con redacción de cabeceras sensibles.

> [!note] Ya en uso
> `SupabaseClient` usa este patrón hoy para inyectar el header `apikey` en cada request.

## 15. State

1. `SyncState` (`IDLE → ENCOLADO → SUBIENDO → OK/ERROR`).
2. `SessionState` (`ANONIMO`, `AUTENTICADO`, `EXPIRADO`).
3. `ConnectivityState` (`ONLINE_WIFI`, `ONLINE_MOVIL`, `OFFLINE`, `AHORRO_DATOS`).
4. `PagingLoadState` en la UI de listas.
5. `FormValidationState` por campo.

## 16. Result / Either — manejo de errores

1. `Result.Success<T>` / `Result.Error(AppException)` / `Result.Loading`.
2. `AppException` por casos: `NoConnection`, `Timeout`, `Unauthorized`, `ServerError(code)`, `Validation(field)`, `Unknown`.
3. `ErrorMapper` traduce `AppException` → `@StringRes` — **la UI nunca muestra `e.getMessage()` crudo**.
4. `safeApiCall(...)` como único punto donde se capturan `IOException`/`HttpException`.
5. Los `UseCase` devuelven `Result<T>`, jamás lanzan excepciones al ViewModel.

Ver [[Result Pattern]].

---

## Relaciones

- [[Clean Architecture]]
- [[Repository Pattern]]
- [[Result Pattern]]
- [[MVVM en Android (ViewModel + LiveData)]]
- [[UiState Inmutable y Flujo Unidireccional]]
- [[Offline-First con Room y Outbox]]
- [[Estándar de Ingeniería Android]]

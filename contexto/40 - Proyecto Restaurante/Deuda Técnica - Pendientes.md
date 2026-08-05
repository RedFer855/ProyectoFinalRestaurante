---
title: "Deuda Técnica — Pendientes"
tags:
  - pendiente
  - deuda-tecnica
  - auditoria
date: 2026-07-29
---

# Deuda Técnica — Pendientes

> [!info] Origen
> Registro creado en el bootstrap de la bóveda (2026-07-29). Los ítems **P-003 a P-018** salieron de auditar el código de la Fase 1 contra el [[Estándar de Ingeniería Android]] adoptado el mismo día — ver [[Sesión 2026-07-29 - Auditoría contra el Estándar de Ingeniería Android]].

> [!warning] Contexto honesto
> El estándar se adoptó **después** de escribir la Fase 1. Esta lista no es una lista de errores por descuido: es la brecha esperable entre "código que funciona" y "código de producción bajo un estándar". Está catalogada para cerrarse en la **Fase 0** de [[Roadmap de Fases]], no para acumularse.

---

## 🔴 Críticos — bloquean el uso real de la app

---

### ~~P-003~~ ✅ · `minSdk = 37` deja la app fuera del 100% de los teléfonos del mercado

**Archivo:** `app/build.gradle.kts` — `defaultConfig.minSdk`
**Introducido en:** el esqueleto generado por Android Studio (commit inicial `886033e`).

```kotlin
minSdk = 37   // ← Android 17 "Cinnamon Bun", salió el 2026-06-16
```

La API 37 es **Android 17**, publicada hace poco más de un mes. Su cuota de dispositivos es **~0%**: solo unidades de desarrollo y betas. Con este valor, **la app no se instala en ningún teléfono real** — ni de los usuarios del restaurante, ni de quien la vaya a evaluar.

Para comparación (datos de abril 2026): API 24 → **96.6%** de los dispositivos; API 30 → 91.1%; API 36 → 22.3%. Ver [[Niveles de API y minSdk - Cobertura Real]].

**Riesgo:** máximo. La app es hoy indistribuible. No da ningún error: compila, empaqueta y hasta corre en un emulador de Android 17.

**Solución:** `minSdk = 24` + habilitar desugaring:
```kotlin
minSdk = 24
compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies { coreLibraryDesugaring(libs.desugar.jdk.libs) }
```
Verificar que nada del código use APIs por encima de 24 sin guard. Cierra también **P-006**.

**Estado:** `[x] Resuelto` (2026-07-31) — `minSdk = 24`, `sourceCompatibility`/`targetCompatibility` a `VERSION_17` (cierra también **P-006**), `isCoreLibraryDesugaringEnabled = true` + dependencia `coreLibraryDesugaring(libs.desugar.jdk.libs)` (versión `2.1.4`, verificada en Maven Central). `compileSdk`/`targetSdk` se mantienen en 37 — solo el piso baja, no el techo.

**Efecto colateral encontrado y corregido:** con `minSdk=24`, el linker de recursos (`aapt2`) rechazó `mipmap-anydpi/ic_launcher.xml` porque `<adaptive-icon>` requiere API 26+ y esa carpeta no tenía calificador de versión. Se renombró a `mipmap-anydpi-v26/` (con `git mv`, preservando historial) — los bitmaps de `mipmap-hdpi/mdpi/xhdpi/xxhdpi/xxxhdpi` ya existían como *fallback* para API 24-25.

**Verificado:** `./gradlew clean assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, 17 tests sin fallas. `aapt2 dump badging` sobre el APK real confirma `minSdkVersion="24"` `targetSdkVersion="37"` — no solo en el `.kts`, en el binario. Ver [[Sesión 2026-07-31 - Fix de minSdk y Java 17]].

---

### ~~P-004~~ ✅ · `LoginActivity` no maneja edge-to-edge ni insets

**Archivo:** `app/src/main/java/.../ui/login/LoginActivity.java`

Con `targetSdk 36+`, **edge-to-edge es obligatorio y no se puede desactivar** (`windowOptOutEdgeToEdgeEnforcement` está deprecado y desactivado). El proyecto está en `targetSdk 37`, así que ya aplica.

`MainActivity` sí llama `EdgeToEdge.enable()` y aplica insets; `LoginActivity` **no hace ninguna de las dos cosas**. El resultado: el título del login puede quedar bajo la barra de estado y el botón "Ingresar" bajo la barra de navegación o el teclado.

**Riesgo:** alto y visible para el usuario, pero **silencioso en desarrollo** — no hay error de compilación ni excepción; solo se ve en un dispositivo real.

**Solución:** replicar el bloque de `MainActivity` en `LoginActivity`, incluyendo `WindowInsetsCompat.Type.ime()` porque la pantalla tiene campos de texto. Ver [[Android 16 y 17 - Cambios de Comportamiento]].

**Estado:** `[x] Resuelto` (2026-07-29) — `LoginActivity` llama `EdgeToEdge.enable(this)` y aplica insets de `systemBars() | ime()` sobre `login_root` en el método `aplicarInsets()`. Ver [[Sesión 2026-07-29 - Rediseño visual del login y plan de conexión Supabase]].
**Falta verificar:** en un teléfono físico. Es el **último ítem abierto de la Fase 1** — el login/Empleados en emulador y **S-2** se cerraron el 2026-08-01. Lo que hay que mirar: que el título no quede bajo la barra de estado y que el botón "Ingresar" no quede tapado por la barra de navegación ni por el teclado.

---

### ~~P-014~~ ✅ · Sin arquitectura offline-first (ni Room, ni WorkManager, ni outbox)

**Alcance:** todo el proyecto.

El requisito no funcional #1 es que la app sea usable sin red — un restaurante con Wi-Fi intermitente no puede depender de la conexión para tomar un pedido. Cuando se abrió este ítem no había **nada** de eso: ni base local, ni cola de salida, y la única pantalla iba directo a la red.

**Riesgo:** si los módulos de Menú, Pedidos y Mesas se escriben contra la red directamente, meter offline-first después **no es un refactor: es una reescritura** de cada pantalla.

**Solución:** implementar Room + `SyncWorker` + tabla `operaciones_pendientes` **en la Fase 2 (Menú)**, no después. Ver [[Offline-First con Room y Outbox]].

**Estado:** `[x] Resuelto` (2026-08-01) — **todo módulo con datos propios es local-first**.

| Módulo | Estado |
|---|---|
| Menú (Fase 2b) | 🟢 Room + outbox + delta. `MenuRepositorioLocal` |
| Empleados | 🟢 Room + outbox + delta. `EmpleadoRepositorioLocal` |
| Login | ➖ N/A — autenticar **exige** red por definición. Lo que le falta es persistir la sesión, que es **P-009**, no cachearla |

Infraestructura compartida: `data/local` (Room 2.8.4, esquema v2), `data/outbox`
(particionado por módulo) y `data/sync` con un **`SyncWorker` único** que corre los dos
sincronizadores — la regla 3 de [[Offline-First con Room y Outbox]] exige que no haya dos
workers compitiendo por la misma cola.

> [!warning] La excepción declarada: el alta de empleado exige conexión
> Crear un empleado llama a la Edge Function que le da de alta su cuenta en Supabase Auth
> con una contraseña temporal. Encolarla obligaría a **guardar esa contraseña en el
> dispositivo** —contra **P-009**, que pide cifrar hasta el token de sesión— y a reintentar
> un `POST` **no idempotente que crea cuentas**, justo lo que [[Offline-First con Room y Outbox]]
> prohíbe sin *idempotency key*.
>
> Es la única operación del módulo que puede fallar por red, y la app lo dice con todas las
> letras en vez de fingir que se guardó. Todo lo demás —listar, editar datos, cambiar rol,
> activar/desactivar— funciona sin internet.

**Lo que este ítem deja al siguiente módulo:** Pedidos y Mesas **nacen** sobre esta
infraestructura. Agregar un módulo hoy es una entidad, un DAO, un mapper, un sincronizador y
sumarlo a la lista del `SyncWorker` — no volver a diseñar nada.

---

## 🟡 Importantes — no bloquean pero generan deuda en cascada

---

### ~~P-005~~ ✅ · `LoginViewModel` crea su propio `Executor` → intesteable; cero pruebas en el proyecto

**Archivo:** `app/src/main/java/.../ui/login/LoginViewModel.java`

```java
private final ExecutorService executor = Executors.newSingleThreadExecutor();  // ❌
```

El `Executor` se instancia dentro del ViewModel en vez de inyectarse. Un test no puede forzar ejecución síncrona (`MoreExecutors.directExecutor()`), así que termina antes de que el hilo de fondo publique el resultado.

Consecuencia directa: **el proyecto no tiene ni una sola prueba propia** — solo los dos archivos de ejemplo de Android Studio.

**Riesgo:** el patrón se replicará a cada ViewModel nuevo, y el proyecto llegará a Pedidos (Fase 4) sin red de seguridad.

**Solución:** recibir `Executor` por constructor (lo provee `LoginViewModelFactory` o Hilt) y escribir `LoginViewModelTest` con `InstantTaskExecutorRule` + `FakeAuthRepository`. Ver [[Asincronia en Java para Android]] y [[Estrategia de Pruebas Android]].

**Estado:** `[x] Resuelto` (2026-07-31) — `LoginViewModel` recibe `ExecutorService` por constructor; `LoginViewModelFactory` lo provee con `Executors.newSingleThreadExecutor()`. Se agregó `androidx.arch.core:core-testing` (`InstantTaskExecutorRule`) y `LoginViewModelTest` con un `ExecutorService` síncrono y un `FakeAuthRepository` — primer test real del proyecto sobre un ViewModel. 3 casos: campos vacíos, login exitoso, credenciales inválidas.

---

### ~~P-006~~ ✅ · `sourceCompatibility` en Java 11, el estándar pide 17

**Archivo:** `app/build.gradle.kts` — `compileOptions`

AGP 9.x y Gradle 9.x **requieren JDK 17** para correr, y Hilt 2.57.1+ también lo exige. El proyecto compila el código fuente en nivel 11, lo que deja fuera features del lenguaje y desalinea con el estándar.

**Riesgo:** bajo hoy, bloqueante al adoptar Hilt.

**Solución:** `VERSION_17` en `sourceCompatibility`/`targetCompatibility`. Se resuelve junto con **P-003**. Ver [[Toolchain Android 2026 - AGP, Gradle y JDK]].

**Estado:** `[x] Resuelto` (2026-07-31) — resuelto junto con **P-003** en el mismo cambio de `app/build.gradle.kts`.

---

### P-008 · Sin R8, sin Baseline Profile, sin módulo de benchmark

**Archivo:** `app/build.gradle.kts` — `buildTypes.release`

```kotlin
release { optimization { enable = false } }   // ← R8 desactivado
```

No hay minificación, ni `shrinkResources`, ni Baseline Profile, ni Startup Profile, ni módulo Macrobenchmark. El Baseline Profile es la mejora de arranque con mejor relación esfuerzo/resultado (típicamente 20–50% en gama baja).

**Riesgo:** ninguno mientras no se publique; alto para los presupuestos de [[Presupuestos de Rendimiento en Gama Baja]] (TTID ≤ 1500 ms, AAB ≤ 15 MB) cuando se publique.

**Solución:** activar R8 full mode + `shrinkResources`, agregar el plugin `androidx.baselineprofile` con su módulo generador, y un módulo `benchmark`. Hacerlo **antes** de la primera release, no antes de la Fase 2.

**Estado:** `[ ] Pendiente — antes de publicar`

---

### P-009 · El `access_token` no se persiste ni se cifra; sin refresh de token

**Archivos:** `ui/login/LoginActivity.java`, `data/repository/SupabaseAuthRepository.java`

El login devuelve `access_token`, `refresh_token` y `expires_in`, pero la `Sesion` **se descarta**: `LoginActivity` solo la usa como señal para navegar. Al cerrar la app hay que volver a loguearse, y ninguna llamada posterior puede autenticarse.

Además, al consumir Auth por REST (sin SDK), **el refresh de token hay que implementarlo a mano** contra `/auth/v1/token?grant_type=refresh_token` — consecuencia aceptada en [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]].

**Riesgo:** bloqueante en cuanto exista una segunda pantalla que consulte datos protegidos por RLS.

**Solución:** `SesionLocalDataSource` sobre `EncryptedSharedPreferences` + `AuthInterceptor` que inyecte el `Bearer` + `TokenRefreshAuthenticator` para el 401. **Nunca en texto plano.** Ver [[Seguridad y Privacidad Android]].

**Estado:** `[ ] Pendiente — requerido en Fase 2`

---

### P-010 · Pantalla de login sin accesibilidad

**Archivo:** `app/src/main/res/layout/activity_login.xml`

Sin `contentDescription`, sin `android:labelFor`, y los campos usan `android:hint` suelto en vez de `TextInputLayout` (con hint flotante, el usuario pierde la etiqueta al empezar a escribir). No verificado con TalkBack ni con fuente al 200%.

**Riesgo:** medio. Barato de arreglar ahora, carísimo en 30 pantallas.

**Solución:** ver el checklist de [[Accesibilidad Android]].

**Estado:** `[~] Parcial` (actualizado 2026-07-31) — el layout se rehízo con `TextInputLayout` (hint flotante que no se pierde al escribir) y `contentDescription` en el `ProgressBar`. El 2026-07-31 se resolvió la segunda mitad: `txt_error_login` (el `TextView` suelto con `accessibilityLiveRegion`) se eliminó y el mensaje de error ahora se asocia con `TextInputLayout#setError()` sobre `til_correo` y `til_contrasenia` — el login no distingue qué campo causó el fallo, así que se marca en los dos; Material anuncia el error solo, sin necesitar el `live region` manual. **Falta:** verificar con TalkBack real y con fuente al 200 % — requiere un dispositivo/emulador, no se pudo hacer desde este entorno (sin Android SDK/adb).

---

### ~~P-013~~ ✅ · El evento de navegación del login no se marca como consumido

**Archivos:** `ui/login/EstadoLogin.java`, `ui/login/LoginActivity.java`

`LoginActivity.render()` navega cada vez que observa un estado con `getSesion() != null`. Como es un **evento de un solo disparo** modelado como estado permanente, si la Activity se recreara con ese estado vivo volvería a navegar.

Hoy no se manifiesta porque hay `finish()` inmediato después del `startActivity`, pero el patrón es incorrecto y se va a copiar a las pantallas siguientes.

**Riesgo:** bajo hoy, medio al replicarse.

**Solución:** `Event<T>` con `getContentIfNotHandled()` o campo `consumido` + `onNavegacionConsumida()`. Ver [[UiState Inmutable y Flujo Unidireccional]].

**Estado:** `[x] Resuelto` (2026-07-31) — se optó por el campo `consumido` (no `Event<T>` genérico): `EstadoLogin` guarda `sesionConsumida` y expone `debeNavegar()`; `LoginViewModel.onNavegacionConsumida()` lo marca. `LoginActivity.render()` navega solo si `debeNavegar()` es cierto.

---

### P-015 · `Activity` + `findViewById` en vez de `Fragment` + ViewBinding + Navigation Component

**Archivos:** `ui/login/LoginActivity.java`, `MainActivity.java`

El estándar pide arquitectura **single-Activity** con Fragments, ViewBinding y Navigation Component + Safe Args. El código usa dos `Activity` que navegan con `Intent` explícito y resuelven vistas con `findViewById` (sin seguridad de tipos ni de nulos, y en la [[Lista Negra de APIs Android]]).

**Riesgo:** medio. Con dos pantallas es manejable; con diez, la navegación por `Intent` se vuelve imposible de razonar y no hay back stack coherente.

**Solución:** migrar a single-Activity + `nav_graph.xml` + ViewBinding **antes de la Fase 4 (Pedidos)**, cuando aún hay pocas pantallas que convertir.

**Estado:** `[ ] Pendiente — antes de Fase 4 (Pedidos)`

---

### P-016 · `Result` transporta un `String`, no un `AppException`

**Archivo:** `domain/Result.java`

Con el error como `String`, la UI no puede reaccionar distinto según el tipo de fallo: no sabe si ofrecer "Reintentar" (red), mandar al login (401) o marcar un campo (validación). Además, el mensaje se arma en `data`, que no debería decidir cómo se le habla al usuario.

**Riesgo:** medio. Se replicará a cada repositorio nuevo.

**Solución:** `AppException` con casos (`NoConnection`, `Timeout`, `Unauthorized`, `ServerError`, `Validation`, `Unknown`) + `ErrorMapper` que traduce a `@StringRes` en la capa `ui`. Ver [[Result Pattern]].

**Estado:** `[ ] Pendiente — junto con P-001`

---

## 🟢 Menores — aceptables por ahora

---

### P-001 · `SupabaseAuthRepository` no tiene clase base compartida (`BaseRepository`)

**Archivo:** `data/repository/SupabaseAuthRepository.java`

Es el único repositorio, así que el `try/catch` está inline en vez de en una clase base reutilizable.

**Riesgo:** al agregar el segundo repositorio (Fase 2), riesgo de copiar/pegar el bloque en vez de extraerlo.

**Solución:** ver [[Base Repository con manejo de errores]] — extraer `safeApiCall` al agregar el repositorio de Menú, junto con **P-016**.

**Estado:** `[ ] Pendiente — bloqueado hasta que exista un segundo repositorio`

---

### P-002 · DI manual sin framework (Hilt)

**Archivo:** `ui/login/LoginViewModelFactory.java`

La inyección es manual por constructor, aceptable para una sola pantalla.

**Riesgo:** con más ViewModels y repositorios, la composition root manual se vuelve difícil de mantener. Hilt además es requisito para `HiltWorkerFactory` cuando entre WorkManager (**P-014**).

**Estado:** `[ ] Reevaluar en Fase 2 — requiere resolver P-006 (Java 17) primero`

---

### P-007 · Retrofit 2 con `.execute()` síncrono, sin adaptadores

**Archivo:** `data/repository/SupabaseAuthRepository.java`

Se usa Retrofit 2.11.0 con llamadas bloqueantes dentro de un `ExecutorService`. Funciona y es simple, pero no usa `CallAdapter` (`ListenableFuture`/`CompletableFuture`), así que el encadenamiento y la cancelación son manuales.

**Riesgo:** bajo. Nota: subir a Retrofit 3 arrastra una dependencia transitiva de Kotlin (por OkHttp 4.12), que suma peso al APK — relevante para los presupuestos de tamaño. Ver [[Librerias Java-Friendly vs Kotlin-Only]].

**Estado:** `[ ] Decidir al agregar el segundo repositorio`

---

### P-011 · IDs de vista en `snake_case` y color hardcodeado en el layout

**Archivo:** `app/src/main/res/layout/activity_login.xml`

- IDs como `txt_correo`, `btn_login` en vez del patrón `camelCase` (`etCorreo`, `btnLogin`) que define [[Convenciones Java]].
- `android:textColor="#D32F2F"` hardcodeado en vez de un color semántico (`@color/color_error`).

**Riesgo:** bajo, pero el color hardcodeado rompe el modo oscuro y se replicará por copia.

**Estado:** `[~] Parcial` (2026-07-29) — el color hardcodeado se reemplazó por `?attr/colorError` del tema Material 3 (mejor que un `@color/` propio: se adapta solo a claro/oscuro). Los `dp` sueltos del layout también pasaron a `@dimen/`. **Falta:** renombrar los IDs `snake_case` → `camelCase` (`txt_correo` → `etCorreo`, etc.), que obliga a tocar `LoginActivity`.

> [!warning] El alcance creció (2026-07-31)
> Al revisar para cerrar este ítem se encontró que **los ~15 layouts restantes del proyecto usan el mismo patrón `snake_case`** (`fragment_empleados.xml`, `dialog_empleado.xml`, `activity_cambiar_contrasenia.xml`, etc.) — incluidos los de Fase 1c/1d, escritos **después** de adoptar el estándar el 2026-07-29. Renombrar solo `activity_login.xml` dejaría el proyecto más inconsistente, no menos. Se decide **no** tocar IDs ahora: la reorganización feature-first de **P-017** (Fase 2) ya implica tocar todos los layouts y sus `Activity`/`Fragment`/`Adapter`, así que el renombrado de IDs se hace en el mismo pase en vez de duplicar el trabajo. Este ítem pasa a bloqueado por P-017.

---

### ~~P-012~~ ✅ · `SUPABASE_ANON_KEY` conserva el nombre de la llave legada

**Archivos:** `app/build.gradle.kts`, `core/SupabaseClient.java`, `local.properties`

Supabase **deprecó las llaves `anon`/`service_role` a finales de 2026**; el reemplazo es `sb_publishable_...`. El *valor* correcto ya es la llave publishable, pero la constante sigue llamándose `SUPABASE_ANON_KEY`, lo que induce a poner la llave vieja.

**Solución:** renombrar a `SUPABASE_PUBLISHABLE_KEY`. Ver [[Supabase Auth REST - Login Android]].

**Estado:** `[x] Resuelto` (2026-07-31) — renombrada en `app/build.gradle.kts` (`buildConfigField`), `SupabaseClient.java` y la clave en `local.properties` (mismo valor, solo cambió el nombre). Verificado que no queda ninguna referencia a `SUPABASE_ANON_KEY` en el código.

---

### P-017 · Paquetes organizados por capa, no por feature

**Alcance:** `app/src/main/java/com/example/proyectofinalrestaurante/`

La estructura es `domain/model`, `domain/repository`, `data/repository`, `ui/login` — es decir **layer-first**. El estándar pide **feature-first** (`feature.pedidos.data`).

**Riesgo:** con un solo feature no molesta. Con Menú + Pedidos + Mesas, `data/repository/` acumula repositorios sin relación y ningún feature es autocontenido.

**Solución:** decidir explícitamente al arrancar la Fase 2: migrar a feature-first, o aceptar layer-first documentándolo. Ver [[Modularizacion por Feature]].

**Estado:** `[ ] Decidir en Fase 2`

---

### P-018 · `applicationId` sigue siendo `com.example.*`

**Archivo:** `app/build.gradle.kts`

`com.example.proyectofinalrestaurante` es el placeholder de Android Studio. **Google Play rechaza cualquier `applicationId` que empiece con `com.example`.** Además, el `applicationId` **no se puede cambiar después de publicar** sin perder la app y todos sus usuarios.

**Solución:** elegir un dominio real (ej. `hn.restaurante.app`) **antes de la primera publicación**. Cambiarlo ahora es trivial; después de publicar es imposible.

**Estado:** `[ ] Pendiente — antes de publicar`

---

### P-019 · Mensajes de error hardcodeados en el ViewModel y el repositorio

**Archivos:** `ui/login/LoginViewModel.java`, `data/repository/SupabaseAuthRepository.java`

Los textos que ve el usuario están como literales Java: `"Completá correo y contraseña"`, `"Correo o contraseña incorrectos"`, `"Sin conexión al servidor. Intentá de nuevo."`, `"Respuesta inesperada del servidor"`. Viola la regla de oro #8 (cero strings hardcodeados) y hace intraducible la app.

Detectado al rehacer el layout del login: la pantalla quedó con **cero strings hardcodeados en XML**, pero los del `ViewModel` siguen ahí.

**Riesgo:** bajo hoy, medio al replicarse — cada módulo nuevo va a copiar el patrón.

**Solución:** que `EstadoLogin` transporte un `@StringRes int` (o el `AppException` tipado de **P-016**) en vez de un `String`, y que la `Activity` resuelva el texto con `getString()`. Va junto con P-016, no antes.

**Estado:** `[ ] Pendiente`

---

### ~~P-020~~ ✅ · `SupabaseAuthRepository` sin ningún test

**Archivo:** `data/repository/SupabaseAuthRepository.java`

Desde el 2026-07-29 el repositorio orquesta 3 llamadas de red (login → GET perfil → logout condicional) con 4 caminos de error distintos (credenciales inválidas, perfil inexistente, perfil inactivo, sin conexión). Cero cobertura de test — mismo problema de fondo que **P-005** (el `Executor` no inyectado impide testear el `ViewModel`; acá el problema es que no hay ningún fake/mock de `SupabaseAuthApi`/`SupabasePerfilApi` para testear el repositorio en aislamiento).

**Riesgo:** medio — la lógica de "cuenta activa" es justo la que no se puede permitir romper en silencio.

**Solución:** introducir una interfaz fake de los dos Retrofit services (o Mockito, hoy no está en las dependencias) y testear los 4 caminos con JUnit puro, sin red real.

**Estado:** `[x] Resuelto` (2026-07-31) — se optó por fakes manuales (no Mockito, se mantiene sin agregar la dependencia): `FakeCall<T>` implementa `retrofit2.Call<T>` devolviendo una `Response` ya armada, y `FakeSupabaseAuthApi`/`FakeSupabasePerfilApi` implementan las interfaces Retrofit. Los DTOs de fixture se arman con `Gson.fromJson()` en vez de agregarles constructores solo para testear. `SupabaseAuthRepositoryTest` cubre los 5 caminos: éxito, credenciales inválidas, perfil inexistente (con logout), perfil inactivo (con logout) y sin conexión. Mismo patrón aplicado a `SupabaseEmpleadoRepositoryTest` (6 casos), que cerraba la deuda equivalente del [[Plan Fase 1d - Modulo Empleados Funcional]].

---

### ~~P-021~~ ✅ · Dos sistemas de autenticación conviviendo: `usuarios` vs `perfiles`

**Alcance:** base de datos Supabase + `data/repository/SupabaseAuthRepository.java`

El esquema relacional subido el 2026-07-29 trae `public.usuarios` con columna **`contrasena VARCHAR(255)`**, en paralelo al par `auth.users` + `public.perfiles` que **es el que el código usa hoy**. Son dos modelos de identidad incompatibles: uno con PK `uuid` gestionado por Supabase Auth, otro con PK `INT` y credencial propia.

**Riesgo:** alto si `usuarios.contrasena` se llega a usar. Implica hacerse cargo de hashing, salt, política de fuerza, rate limiting y recuperación de contraseña — todo lo que Supabase Auth ya resuelve y que Bimbo documentó como trabajo real en su `Plan de Seguridad - Roadmap 10-10`. Una contraseña mal hasheada es la falla más común de este tipo de proyecto.

**Solución propuesta:** eliminar `usuarios.contrasena` y enlazar con `usuarios.id_auth_user uuid REFERENCES auth.users(id)`, dejando las credenciales en Supabase Auth y `usuarios` como tabla de datos de negocio (rol, empleado, estado). Alternativa: descartar `perfiles` y absorber su rol dentro de `usuarios` con el mismo enlace por `uuid`.

Es una **decisión de fondo** → cuando se resuelva va un ADR en `45 - Decisiones/`. Ver [[Esquema de Base de Datos]].

**Estado:** `[x] Resuelto` (2026-07-29) — se eliminó `usuarios.contrasena` y se agregó `usuarios.id_auth_user uuid REFERENCES auth.users(id)` con policy RLS propia. Verificado que Supabase Auth usa bcrypt + salt aleatorio (`auth.users.encrypted_password`) — nunca se reimplementa hashing propio. **Sigue abierto:** decidir si `perfiles` y `usuarios` conviven o se consolidan en una sola tabla (el código Android hoy solo lee `perfiles`).

---

### ~~P-022~~ ✅ · `AndroidManifest.xml` sin permiso `INTERNET` — crasheaba la app al loguear

**Archivo:** `app/src/main/AndroidManifest.xml`, `data/repository/SupabaseAuthRepository.java`

Al probar el login por primera vez en un emulador real (2026-07-31), la app **se cerraba** al tocar "Ingresar". El log (`adb logcat`) mostró la causa exacta:

```
java.lang.SecurityException: Permission denied (missing INTERNET permission?)
    at okhttp3.Dns... → SupabaseAuthRepository.login(SupabaseAuthRepository.java:40)
```

Faltaba `<uses-permission android:name="android.permission.INTERNET" />` en el manifest — sin eso, **ninguna llamada de red puede funcionar nunca**, sin importar qué tan bien esté el resto del código. Y como `SecurityException` no es un `IOException`, el `catch` del repositorio no la atrapaba: se escapaba del hilo del `Executor` sin manejar y Android mataba todo el proceso.

**Solución aplicada:**
1. Se agregó el permiso `INTERNET` al manifest.
2. Se agregó `catch (SecurityException ex)` en `SupabaseAuthRepository.login()`, como defensa adicional para que un problema de permisos nunca vuelva a tumbar la app en vez de mostrar un error en pantalla.

**Verificado en vivo:** reinstalada la APK en el emulador, se automatizó el login por `adb shell input` (correo/contraseña del admin) y se confirmó en `logcat` + captura de pantalla que navega a `MainActivity` ("¡Bienvenido!") sin crash. Es la **primera vez que el login corre de punta a punta**.

**Estado:** `[x] Resuelto` (2026-07-31)

---

### P-023 · Nadie limpia los archivos huérfanos del bucket `platillos`

**Alcance:** Supabase Storage, bucket `platillos` (creado 2026-07-31, Fase 2a).

El flujo de imágenes que define [[Plan Fase 2a - CRUD de Platillos y Categorias]] sube el
archivo **antes** de tocar la fila, y al reemplazar una foto usa siempre una ruta nueva
(un UUID) para no pelearse con la caché de Glide. De ahí salen dos formas de dejar basura:

1. El insert de `platillo` falla después de subir la foto. El plan pide **compensar
   borrando el objeto**, pero si ese `DELETE` también falla, el archivo queda.
2. Se reemplaza la foto de un platillo: la vieja se borra en un tercer paso que puede
   fallar sin que el usuario se entere.
3. **Agregado al implementarlo (2026-07-31):** si el insert se corta por red
   (`IOException`) en vez de ser rechazado por el servidor, la implementación **no
   compensa a propósito** — sin respuesta no se sabe si la fila entró, y borrar la foto de
   un platillo que sí se creó deja una imagen rota y visible al usuario. Se prefiere el
   archivo huérfano, que es invisible. Ver
   [[Sesión 2026-07-31 - Fase 2a implementada (CRUD de Menú con fotos en Storage)]].

No hay recolector de basura del bucket ni un inventario que cruce `storage.objects`
contra `platillo.ruta_imagen`.

**Riesgo:** bajo a corto plazo (cuestan centavos y no se ven), medio a largo: nadie sabrá
después qué archivo pertenece a qué platillo, y el bucket es público — un archivo huérfano
sigue siendo servible por su URL indefinidamente.

**Solución:** una función programada (`pg_cron` + una consulta que compare
`storage.objects` del bucket contra las `ruta_imagen` vigentes) o un barrido manual
documentado. Requiere acceso a Supabase, así que **no es trabajo del agente de código**.

**Estado:** `[ ] Pendiente — al cerrar la Fase 2`

---

### P-024 · `CompresorDeImagen` sin pruebas: no se puede testear en la JVM

**Archivo:** `ui/menu/CompresorDeImagen.java` (creado 2026-07-31, Fase 2a).

Es la pieza que convierte la foto que eligió el usuario en los bytes que se suben: mide con
`inJustDecodeBounds`, calcula `inSampleSize`, rota según `ExifInterface.TAG_ORIENTATION` y
comprime a JPEG. Toda esa lógica depende de `BitmapFactory` y `Bitmap`, que en los unit
tests de la JVM son *stubs* que lanzan `RuntimeException("Stub!")`. Resultado: **el resto
del módulo Menú tiene 68 tests y esta clase tiene cero**.

Lo que queda sin cubrir no es trivial:

- que `inSampleSize` deje efectivamente el lado largo en ~1024 px y no en 2048 o en 512;
- que una foto con `ORIENTATION_ROTATE_90` salga derecha (el caso que hace que las fotos de
  cámara aparezcan acostadas);
- que el resultado entre en los 2 MB del bucket para una foto de 12 MP real.

**Riesgo:** medio. Un error acá no rompe el build ni lanza una excepción: sube una foto
acostada, o gigante, o pixelada. Se descubre mirando la app, que es justo lo que las
pruebas deberían evitar.

**Solución:** una de dos, y la elección tiene su costo.

1. **Robolectric** — corre en `testDebugUnitTest` con implementaciones reales de
   `BitmapFactory`. Era una dependencia nueva y pesada (~40 MB de artefactos de Android
   emulados) para cubrir una sola clase.
2. **Test instrumentado** en `androidTest/`, con imágenes de prueba en `assets/`. Sin
   dependencia nueva, pero **el proyecto todavía no tiene harness de tests instrumentados
   corriendo** y necesita un emulador o dispositivo en el pipeline.

La 2 es más honesta (prueba el `BitmapFactory` de verdad, no una emulación) y encaja con
que la verificación funcional del proyecto ya depende de un dispositivo. Decidirlo cuando
se arme el harness de instrumentación, no antes.

> [!success] El argumento del costo ya no aplica (2026-08-01)
> La Fase 2b **agregó Robolectric 4.16.1** para poder testear los DAOs de Room
> (`data/local/*Test`), así que la opción 1 dejó de costar una dependencia nueva: hoy son
> unas pocas líneas de test sobre infraestructura que ya está pagada y corriendo en la
> suite. Este ítem pasó de *"hay que decidir y bancarse el costo"* a **simplemente
> pendiente y barato**.
>
> Sigue valiendo la salvedad de la opción 2: Robolectric emula `BitmapFactory`, no lo
> ejecuta. Para lo que hay que cubrir acá —que `inSampleSize` deje el lado largo en ~1024
> px y que una foto con `ORIENTATION_ROTATE_90` salga derecha— la emulación alcanza; el
> caso que **no** cubre es una foto de 12 MP real contra el límite de 2 MB del bucket.

**Estado:** `[ ] Pendiente — desbloqueado 2026-08-01, Robolectric ya está en el proyecto`

---

### P-025 · `actualizado_en` usa `now()`, que es la hora de **inicio** de la transacción

**Alcance:** `tocar_actualizado_en()` en Supabase + el sync delta de
[[Plan Fase 2b - Offline-First con Room y Outbox]].

El trigger hace `new.actualizado_en := now()`. En Postgres `now()` es sinónimo de
`transaction_timestamp()`: devuelve la hora en que **empezó** la transacción, no la del
momento del `UPDATE`. Se verificó en la base: `now() = transaction_timestamp()` → `true`, y
`clock_timestamp() > now()` dentro de la misma transacción → también `true`.

Eso abre una ventana en el sync delta. Si una transacción empieza en `T` y confirma en
`T+5s`, la fila queda con `actualizado_en = T` pero **recién se vuelve visible en `T+5s`**.
Un cliente que sincroniza en `T+2s` no la ve, guarda `last_sync_at = T+2s`, y en la próxima
corrida pide `actualizado_en > T+2s` — donde esa fila **ya no entra**. Se pierde para
siempre, en silencio.

**Riesgo:** bajo hoy, y por una razón concreta: la app manda `PATCH` de una sola sentencia,
así que sus transacciones duran milisegundos y la ventana es despreciable. Sube en cuanto
aparezca cualquier operación multi-sentencia larga — un alta de pedido con su detalle, una
carga masiva desde el SQL Editor, o una Edge Function que agrupe varias escrituras.

**Descubierto:** 2026-08-01, verificando el criterio de aceptación de §2.3 del plan de 2b.
El primer test dio "0 filas en el delta" y la causa no era el índice recién creado sino
esto: el corte y el trigger escribían **el mismo** `now()`. Entre transacciones separadas el
delta funciona bien (verificado: devuelve exactamente la fila tocada).

**Solución:** cambiar el trigger a `clock_timestamp()`, que devuelve la hora real del
momento. No es gratis: dos filas actualizadas en la misma transacción dejarían de compartir
timestamp, lo que es más correcto pero cambia el orden observable. Alternativa más sólida y
más cara: numerar los cambios con una secuencia monótona en vez de con reloj.

**Estado:** `[ ] Pendiente — revisar antes de que exista la primera escritura multi-sentencia (Pedidos, Fase 4)`

---

### P-026 · El id de cliente offline para Pedidos sigue sin resolver

**Alcance:** `domain/repository/ClienteRepository.buscarOCrearCliente(...)`, Fase 4 (Pedidos).

[[Plan Fase 2d - CRUD de Clientes]] §5.1 acepta explícitamente que el RPC
`buscar_o_crear_cliente` **no puede ser offline**: el `id_cliente` lo genera el servidor. Este
módulo implementa la opción A (crear el cliente local con `id_local` y encolar, como todo lo
demás del CRUD) y deja el RPC expuesto en el repositorio, sin consumidor, para que Pedidos
decida.

**Riesgo:** un pedido tomado sin red que referencia a un cliente que **tampoco** tiene
`id_servidor` todavía es un caso que Pedidos va a encontrarse de entrada. Probablemente se
resuelve encolando ambas operaciones juntas y resolviendo el id al drenar el outbox — mismo
espíritu que el pliegue de ediciones sobre un `CREAR` pendiente del Menú—, pero no está
diseñado.

**Solución:** diseñarlo al abrir la Fase 4, con el caso de uso real de Pedidos delante en vez
de anticiparlo en abstracto.

**Estado:** `[ ] Pendiente — registrado al cerrar la Fase 2d (Parte B), 2026-08-01`

---

### P-027 · Room guarda datos personales de clientes sin cifrar

**Alcance:** `data/local/entity/ClienteEntity.java`, base `restaurante.db`.

Desde el módulo Clientes (Fase 2d), Room guarda nombre, apellido, identidad y teléfono de
gente real en SQLite sin cifrar. Un teléfono perdido es, entre otras cosas, una base de
clientes perdida. El plan (§5.4) pide explícitamente **no improvisar** esto: registrarlo como
deuda en vez de decidirlo al pasar.

**Riesgo:** bajo mientras el volumen de clientes sea chico y el dispositivo esté bajo control
del restaurante; sube con la cantidad de datos y con cualquier escenario de robo/pérdida del
teléfono.

**Solución:** SQLCipher (o `SQLiteDatabase` cifrada con `EncryptedFile`/Jetpack Security) para
la tabla `clientes` — evaluar si conviene cifrar la base entera o solo esa tabla, y el costo
de rendimiento en Room. Ver [[Seguridad y Privacidad Android]].

**Estado:** `[ ] Pendiente — decisión propia, diferida a propósito`

---

### P-028 · Capa HTTP fragmentada: 7 `OkHttpClient`, sin caché y con timeouts incompletos

**Alcance:** `core/SupabaseClient.java:115-131`, `ui/menu/UrlDeImagen.java`.

Salió a la luz investigando la carga lenta del Menú
([[Sesión 2026-08-04 - La carga inicial del Menú y el trabajo único envenenado]]). Se
arregló lo que tocaba al Menú y **esto quedó afuera a propósito**, porque toca los siete
APIs y por lo tanto todos los módulos.

Cuatro cosas, en orden de impacto:

1. **`buildRetrofit()` construye un `OkHttpClient` nuevo en cada una de sus 7 invocaciones.**
   Son 7 `ConnectionPool` y 7 pools de hilos independientes contra el mismo host: la bajada
   del menú y la subida a Storage no reutilizan la conexión TLS.
2. **Sin `Cache` HTTP.** [[Presupuestos de Rendimiento en Gama Baja]] pide ~10 MB con
   `max-stale`. Ojo: hoy no serviría de mucho —PostgREST no manda `Cache-Control` y las
   imágenes ni siquiera pasan por OkHttp—, así que va después de los otros puntos.
3. **Timeouts incompletos:** hay `connect` y `read` (15 s), faltan `write` y `callTimeout`.
   El presupuesto pide connect 15 / read 30 / write 30 / callTimeout 45. Sin `callTimeout`,
   una subida lenta puede colgarse indefinidamente.
4. **Una sola resolución de imagen para todos los usos.** `CompresorDeImagen` sube a 1024 px
   (dentro del presupuesto de 1600), pero la tarjeta del menú mide 150 dp: se bajan archivos
   5-9× más grandes de lo necesario. Las dos salidas —el endpoint de transformación de
   Supabase o subir un thumbnail aparte— son decisiones de fondo: **la primera es función de
   plan pago** (en Free devolvería imágenes rotas), la segunda arrastra columna nueva,
   migración de Room y backfill.

**Riesgo:** bajo en Wi-Fi; medio en la red 3G intermitente que es el objetivo del proyecto.

**Solución:** empezar por (1) y (3), que son contenidos y testeables
(`SupabaseClientTest` ya existe). (2) y (4) recién después de medir.

**Estado:** `[ ] Pendiente — fuera del alcance acordado del arreglo del Menú`

---

## Historial de resolución

| ID | Descripción | Severidad | Estado | Sesión |
|---|---|---|---|---|
| P-001 | Falta `BaseRepository` compartido | 🟢 | `[ ]` Pendiente | [[Sesión 2026-07-29 - Bootstrap de bóveda y Fase 1 Login]] |
| P-002 | DI manual sin framework | 🟢 | `[ ]` Pendiente | [[Sesión 2026-07-29 - Bootstrap de bóveda y Fase 1 Login]] |
| ~~P-003~~ | `minSdk 37` → 0% de dispositivos | 🔴 | `[x]` **Resuelto** 2026-07-31 | [[Sesión 2026-07-31 - Fix de minSdk y Java 17]] |
| ~~P-004~~ | `LoginActivity` sin edge-to-edge ni insets | 🔴 | `[x]` **Resuelto** 2026-07-29 | [[Sesión 2026-07-29 - Rediseño visual del login y plan de conexión Supabase]] |
| ~~P-005~~ | `Executor` no inyectado → cero pruebas | 🟡 | `[x]` **Resuelto** 2026-07-31 | [[Sesión 2026-07-31 - Remediación P-005 P-012 P-013 P-020 y arranque Fase 2]] |
| ~~P-006~~ | Java 11 en vez de 17 | 🟡 | `[x]` **Resuelto** 2026-07-31 | [[Sesión 2026-07-31 - Fix de minSdk y Java 17]] |
| P-007 | Retrofit 2 con `.execute()` sin adaptadores | 🟢 | `[ ]` Pendiente | idem |
| P-008 | Sin R8, Baseline Profile ni benchmark | 🟡 | `[ ]` Pendiente | idem |
| P-009 | Token no persistido ni cifrado; sin refresh | 🟡 | `[ ]` Pendiente | idem |
| P-010 | Login sin accesibilidad | 🟡 | `[~]` Parcial 2026-07-29 | [[Sesión 2026-07-29 - Rediseño visual del login y plan de conexión Supabase]] |
| P-011 | IDs `snake_case` y color hardcodeado | 🟢 | `[~]` Parcial 2026-07-29 | idem |
| ~~P-012~~ | `SUPABASE_ANON_KEY` con nombre legado | 🟢 | `[x]` **Resuelto** 2026-07-31 | [[Sesión 2026-07-31 - Remediación P-005 P-012 P-013 P-020 y arranque Fase 2]] |
| ~~P-013~~ | Evento de navegación sin marcar consumido | 🟡 | `[x]` **Resuelto** 2026-07-31 | [[Sesión 2026-07-31 - Remediación P-005 P-012 P-013 P-020 y arranque Fase 2]] |
| ~~P-014~~ | Sin offline-first (Room/WorkManager/outbox) | 🔴 | `[x]` **Resuelto** 2026-08-01 — Menú y Empleados local-first | [[Sesión 2026-08-01 - Empleados offline-first y cierre de P-014]] |
| P-015 | `Activity` + `findViewById` en vez de Fragment/ViewBinding | 🟡 | `[ ]` Pendiente | idem |
| P-016 | `Result` con `String` en vez de `AppException` | 🟡 | `[ ]` Pendiente | idem |
| P-017 | Paquetes layer-first en vez de feature-first | 🟢 | `[ ]` Pendiente | idem |
| P-018 | `applicationId` sigue en `com.example.*` | 🟢 | `[ ]` Pendiente | idem |
| P-019 | Mensajes de error hardcodeados en VM/repositorio | 🟢 | `[ ]` Pendiente | [[Sesión 2026-07-29 - Rediseño visual del login y plan de conexión Supabase]] |
| ~~P-022~~ | Sin permiso `INTERNET` — crasheaba al loguear | 🔴 | `[x]` **Resuelto** 2026-07-31 | [[Sesión 2026-07-31 - Primer login verificado en emulador]] |
| ~~P-020~~ | `SupabaseAuthRepository` sin test (login+perfil+logout) | 🟡 | `[x]` **Resuelto** 2026-07-31 | [[Sesión 2026-07-31 - Remediación P-005 P-012 P-013 P-020 y arranque Fase 2]] |
| ~~P-021~~ | Dos sistemas de auth: `usuarios.contrasena` vs `perfiles`+Auth | 🔴 | `[x]` **Resuelto** 2026-07-29 | [[Sesión 2026-07-29 - Resolución P-021 y admin pendiente de datos]] |
| P-023 | Archivos huérfanos en el bucket `platillos` sin recolector | 🟢 | `[ ]` Pendiente | [[Sesión 2026-07-31 - Plan técnico de Fase 2a (CRUD de Menú) y preparación de Supabase]] |
| P-024 | `CompresorDeImagen` sin pruebas | 🟢 | `[ ]` Pendiente — **desbloqueado** 2026-08-01 (Robolectric ya está) | [[Sesión 2026-07-31 - Fase 2a implementada (CRUD de Menú con fotos en Storage)]] |
| P-025 | `actualizado_en` usa `now()` (inicio de transacción) — ventana en el sync delta | 🟢 | `[ ]` Pendiente | [[Sesión 2026-08-01 - Indices del sync delta y puesta al dia de P-014 y P-024]] |
| P-026 | Id de cliente offline sin resolver para Pedidos (buscar-o-crear exige conexión) | 🟢 | `[ ]` Pendiente | [[Sesión 2026-08-01 - Fase 2c y 2d completas, Parte B — Mesas y Clientes]] |
| P-027 | Datos personales de clientes sin cifrar en Room | 🟢 | `[ ]` Pendiente | idem |
| P-028 | Capa HTTP fragmentada: 7 `OkHttpClient`, sin caché, timeouts incompletos | 🟡 | `[ ]` Pendiente | [[Sesión 2026-08-04 - La carga inicial del Menú y el trabajo único envenenado]] |

---

## Relaciones

- [[Estándar de Ingeniería Android]] — el criterio contra el que se auditó
- [[Gate de Autoverificación]] — el gate aplicado a la Fase 1
- [[Roadmap de Fases]] — Fase 0 agrupa la remediación
- [[Arquitectura Actual]]
- [[Sesión 2026-07-29 - Auditoría contra el Estándar de Ingeniería Android]]

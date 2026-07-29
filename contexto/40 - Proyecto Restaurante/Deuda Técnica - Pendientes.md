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

### P-003 · `minSdk = 37` deja la app fuera del 100% de los teléfonos del mercado

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

**Estado:** `[ ] Pendiente — máxima prioridad`

---

### P-004 · `LoginActivity` no maneja edge-to-edge ni insets

**Archivo:** `app/src/main/java/.../ui/login/LoginActivity.java`

Con `targetSdk 36+`, **edge-to-edge es obligatorio y no se puede desactivar** (`windowOptOutEdgeToEdgeEnforcement` está deprecado y desactivado). El proyecto está en `targetSdk 37`, así que ya aplica.

`MainActivity` sí llama `EdgeToEdge.enable()` y aplica insets; `LoginActivity` **no hace ninguna de las dos cosas**. El resultado: el título del login puede quedar bajo la barra de estado y el botón "Ingresar" bajo la barra de navegación o el teclado.

**Riesgo:** alto y visible para el usuario, pero **silencioso en desarrollo** — no hay error de compilación ni excepción; solo se ve en un dispositivo real.

**Solución:** replicar el bloque de `MainActivity` en `LoginActivity`, incluyendo `WindowInsetsCompat.Type.ime()` porque la pantalla tiene campos de texto. Ver [[Android 16 y 17 - Cambios de Comportamiento]].

**Estado:** `[ ] Pendiente`

---

### P-014 · Sin arquitectura offline-first (ni Room, ni WorkManager, ni outbox)

**Alcance:** todo el proyecto.

El requisito no funcional #1 es que la app sea usable sin red — un restaurante con Wi-Fi intermitente no puede depender de la conexión para tomar un pedido. Hoy no hay **nada** de eso: no hay base local, la única pantalla va directo a la red y falla si no hay conexión.

**Riesgo:** si los módulos de Menú, Pedidos y Mesas se escriben contra la red directamente, meter offline-first después **no es un refactor: es una reescritura** de cada pantalla.

**Solución:** implementar Room + `SyncWorker` + tabla `operaciones_pendientes` **en la Fase 2 (Menú)**, no después. Ver [[Offline-First con Room y Outbox]].

**Estado:** `[ ] Pendiente — decisión obligada al arrancar Fase 2`

---

## 🟡 Importantes — no bloquean pero generan deuda en cascada

---

### P-005 · `LoginViewModel` crea su propio `Executor` → intesteable; cero pruebas en el proyecto

**Archivo:** `app/src/main/java/.../ui/login/LoginViewModel.java`

```java
private final ExecutorService executor = Executors.newSingleThreadExecutor();  // ❌
```

El `Executor` se instancia dentro del ViewModel en vez de inyectarse. Un test no puede forzar ejecución síncrona (`MoreExecutors.directExecutor()`), así que termina antes de que el hilo de fondo publique el resultado.

Consecuencia directa: **el proyecto no tiene ni una sola prueba propia** — solo los dos archivos de ejemplo de Android Studio.

**Riesgo:** el patrón se replicará a cada ViewModel nuevo, y el proyecto llegará a la Fase 3 sin red de seguridad.

**Solución:** recibir `Executor` por constructor (lo provee `LoginViewModelFactory` o Hilt) y escribir `LoginViewModelTest` con `InstantTaskExecutorRule` + `FakeAuthRepository`. Ver [[Asincronia en Java para Android]] y [[Estrategia de Pruebas Android]].

**Estado:** `[ ] Pendiente`

---

### P-006 · `sourceCompatibility` en Java 11, el estándar pide 17

**Archivo:** `app/build.gradle.kts` — `compileOptions`

AGP 9.x y Gradle 9.x **requieren JDK 17** para correr, y Hilt 2.57.1+ también lo exige. El proyecto compila el código fuente en nivel 11, lo que deja fuera features del lenguaje y desalinea con el estándar.

**Riesgo:** bajo hoy, bloqueante al adoptar Hilt.

**Solución:** `VERSION_17` en `sourceCompatibility`/`targetCompatibility`. Se resuelve junto con **P-003**. Ver [[Toolchain Android 2026 - AGP, Gradle y JDK]].

**Estado:** `[ ] Pendiente`

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

**Estado:** `[ ] Pendiente`

---

### P-013 · El evento de navegación del login no se marca como consumido

**Archivos:** `ui/login/EstadoLogin.java`, `ui/login/LoginActivity.java`

`LoginActivity.render()` navega cada vez que observa un estado con `getSesion() != null`. Como es un **evento de un solo disparo** modelado como estado permanente, si la Activity se recreara con ese estado vivo volvería a navegar.

Hoy no se manifiesta porque hay `finish()` inmediato después del `startActivity`, pero el patrón es incorrecto y se va a copiar a las pantallas siguientes.

**Riesgo:** bajo hoy, medio al replicarse.

**Solución:** `Event<T>` con `getContentIfNotHandled()` o campo `consumido` + `onNavegacionConsumida()`. Ver [[UiState Inmutable y Flujo Unidireccional]].

**Estado:** `[ ] Pendiente`

---

### P-015 · `Activity` + `findViewById` en vez de `Fragment` + ViewBinding + Navigation Component

**Archivos:** `ui/login/LoginActivity.java`, `MainActivity.java`

El estándar pide arquitectura **single-Activity** con Fragments, ViewBinding y Navigation Component + Safe Args. El código usa dos `Activity` que navegan con `Intent` explícito y resuelven vistas con `findViewById` (sin seguridad de tipos ni de nulos, y en la [[Lista Negra de APIs Android]]).

**Riesgo:** medio. Con dos pantallas es manejable; con diez, la navegación por `Intent` se vuelve imposible de razonar y no hay back stack coherente.

**Solución:** migrar a single-Activity + `nav_graph.xml` + ViewBinding **antes de la Fase 3**, cuando aún hay pocas pantallas que convertir.

**Estado:** `[ ] Pendiente — antes de Fase 3`

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

**Estado:** `[ ] Pendiente`

---

### P-012 · `SUPABASE_ANON_KEY` conserva el nombre de la llave legada

**Archivos:** `app/build.gradle.kts`, `core/SupabaseClient.java`, `local.properties`

Supabase **deprecó las llaves `anon`/`service_role` a finales de 2026**; el reemplazo es `sb_publishable_...`. El *valor* correcto ya es la llave publishable, pero la constante sigue llamándose `SUPABASE_ANON_KEY`, lo que induce a poner la llave vieja.

**Solución:** renombrar a `SUPABASE_PUBLISHABLE_KEY`. Ver [[Supabase Auth REST - Login Android]].

**Estado:** `[ ] Pendiente`

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

## Historial de resolución

| ID | Descripción | Severidad | Estado | Sesión |
|---|---|---|---|---|
| P-001 | Falta `BaseRepository` compartido | 🟢 | `[ ]` Pendiente | [[Sesión 2026-07-29 - Bootstrap de bóveda y Fase 1 Login]] |
| P-002 | DI manual sin framework | 🟢 | `[ ]` Pendiente | [[Sesión 2026-07-29 - Bootstrap de bóveda y Fase 1 Login]] |
| P-003 | `minSdk 37` → 0% de dispositivos | 🔴 | `[ ]` Pendiente | [[Sesión 2026-07-29 - Auditoría contra el Estándar de Ingeniería Android]] |
| P-004 | `LoginActivity` sin edge-to-edge ni insets | 🔴 | `[ ]` Pendiente | idem |
| P-005 | `Executor` no inyectado → cero pruebas | 🟡 | `[ ]` Pendiente | idem |
| P-006 | Java 11 en vez de 17 | 🟡 | `[ ]` Pendiente | idem |
| P-007 | Retrofit 2 con `.execute()` sin adaptadores | 🟢 | `[ ]` Pendiente | idem |
| P-008 | Sin R8, Baseline Profile ni benchmark | 🟡 | `[ ]` Pendiente | idem |
| P-009 | Token no persistido ni cifrado; sin refresh | 🟡 | `[ ]` Pendiente | idem |
| P-010 | Login sin accesibilidad | 🟡 | `[ ]` Pendiente | idem |
| P-011 | IDs `snake_case` y color hardcodeado | 🟢 | `[ ]` Pendiente | idem |
| P-012 | `SUPABASE_ANON_KEY` con nombre legado | 🟢 | `[ ]` Pendiente | idem |
| P-013 | Evento de navegación sin marcar consumido | 🟡 | `[ ]` Pendiente | idem |
| P-014 | Sin offline-first (Room/WorkManager/outbox) | 🔴 | `[ ]` Pendiente | idem |
| P-015 | `Activity` + `findViewById` en vez de Fragment/ViewBinding | 🟡 | `[ ]` Pendiente | idem |
| P-016 | `Result` con `String` en vez de `AppException` | 🟡 | `[ ]` Pendiente | idem |
| P-017 | Paquetes layer-first en vez de feature-first | 🟢 | `[ ]` Pendiente | idem |
| P-018 | `applicationId` sigue en `com.example.*` | 🟢 | `[ ]` Pendiente | idem |

---

## Relaciones

- [[Estándar de Ingeniería Android]] — el criterio contra el que se auditó
- [[Gate de Autoverificación]] — el gate aplicado a la Fase 1
- [[Roadmap de Fases]] — Fase 0 agrupa la remediación
- [[Arquitectura Actual]]
- [[Sesión 2026-07-29 - Auditoría contra el Estándar de Ingeniería Android]]

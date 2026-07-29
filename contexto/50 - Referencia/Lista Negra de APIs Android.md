---
title: "Lista Negra de APIs Android"
tags:
  - referencia
  - android
  - deprecado
date: 2026-07-29
lifecycle: verified
---

# Lista Negra de APIs Android

> [!danger] Regla dura
> El uso de cualquiera de estas APIs en código nuevo **invalida la entrega**. Son la causa #1 de código Android generado que no compila, o que compila y falla en runtime: provienen del corpus de ejemplos viejos que circula en internet.

## Prohibidas y su reemplazo

| ❌ Prohibido | ✅ Usar en su lugar | Por qué |
|---|---|---|
| `AsyncTask` | `ExecutorService` inyectado + `LiveData.postValue()` | Deprecado desde API 30; fugas de memoria garantizadas |
| `new Thread()` para I/O | `AppExecutors.io()` | Sin control de pool, sin cancelación |
| `Handler` como planificador de trabajo | `WorkManager` | `Handler` no sobrevive al proceso |
| `findViewById` | **ViewBinding** | Sin seguridad de tipos ni de nulos |
| Data Binding en código nuevo | ViewBinding | Data Binding está en mantenimiento |
| `onBackPressed()` | `OnBackPressedDispatcher` + `OnBackPressedCallback` | **Ya no se invoca con `targetSdk 36+`** |
| `windowOptOutEdgeToEdgeEnforcement` | Manejar insets con `WindowInsetsCompat` | Deprecado y desactivado en API 36 |
| `startActivityForResult` / `onActivityResult` | `ActivityResultContracts` | Deprecado; frágil ante muerte de proceso |
| `LocalBroadcastManager` | `LiveData` / `SharedFlow` / callbacks del repositorio | Deprecado |
| `SharedPreferences` en código nuevo | **DataStore** (variante RxJava3 para Java) | API síncrona que bloquea el hilo principal |
| `notifyDataSetChanged()` | `ListAdapter` + `DiffUtil` | Redibuja toda la lista; mata el scroll en gama baja |
| `Context` estático (`public static Context sCtx`) | Inyección con Hilt | Fuga de memoria de por vida |
| `fallbackToDestructiveMigration()` en release | Migraciones explícitas y probadas | **Borra los datos del usuario** |
| `printStackTrace()` | Logger + `Result.Error(AppException)` | No llega a crash reporting; ruido en producción |
| `catch (Exception e) {}` vacío | Traducir a `AppException` y propagar | Traga fallos y produce bugs invisibles |
| `select=*` en PostgREST | `?select=id,nombre,precio` | Descarga columnas que no se usan; caro en 3G |
| `service_role` / `sb_secret_` en la app | Edge Function del lado servidor | **Fuga total de la base de datos** |
| `import x.*` | Imports explícitos | Ambigüedad y colisiones |
| `System.out.println` | Logger con niveles | Invisible en logcat filtrado, no se elimina en release |
| `SimpleDateFormat` estático compartido | `java.time` (con desugaring) | No es thread-safe: corrompe fechas bajo concurrencia |
| APK universal en Play | **AAB** | Ver [[Requisitos de Google Play 2026]] |
| `Thread.sleep()` / `.get()` bloqueante en hilo principal | Callbacks / `postValue` | ANR directo |
| `AndroidViewModel` (por el `Context`) | `ViewModel` + dependencias inyectadas | Acopla el VM al framework y lo vuelve intesteable |

## Cómo se verifica

- Revisión en cada PR contra el [[Gate de Autoverificación]].
- Android Lint con `abortOnError = true` atrapa buena parte de forma automática.
- Ver [[Estrategia de Pruebas Android]] para el resto.

---

## Relaciones

- [[Android 16 y 17 - Cambios de Comportamiento]] — el porqué de `onBackPressed` y edge-to-edge
- [[Gate de Autoverificación]]
- [[Convenciones Java]]
- [[Estándar de Ingeniería Android]]

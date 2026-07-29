---
title: "Presupuestos de Rendimiento en Gama Baja"
tags:
  - referencia
  - rendimiento
  - gama-baja
date: 2026-07-29
lifecycle: verified
---

# Presupuestos de Rendimiento en Gama Baja

> [!abstract] Principio
> "Que sea rápido" no es verificable. "TTID ≤ 1500 ms en un dispositivo de 2 GB de RAM" sí lo es. Sin número, no hay optimización: hay opinión.

## Dispositivo de referencia (obligatorio para validar)

**2 GB de RAM · Android 9–11 · almacenamiento eMMC · red 3G.**

No se valida solo en emulador de escritorio ni en gama alta: ahí todo parece rápido y el problema aparece en el restaurante real.

## Presupuestos numéricos

| Métrica | Objetivo |
|---|---|
| Cold start (TTID) en gama baja | ≤ **1500 ms** |
| Warm start | ≤ 600 ms |
| Frames con jank (p95) | < 1% ; ningún frame > 16.6 ms sostenido |
| Tamaño de descarga (AAB, gama baja) | ≤ 15 MB |
| Memoria (PSS en uso normal) | ≤ 150 MB |
| Primer dato útil con 3G | ≤ 2 s (desde caché local: inmediato) |
| Tasa de fallos percibidos (Play Vitals) | < 1.09% |
| Tasa de ANR percibidos (Play Vitals) | < 0.47% |

Si un cambio excede un presupuesto, **el trabajo no está terminado**.

---

## Palancas, ordenadas por retorno

### 1. Arranque (la de mayor impacto)

- **Baseline Profile + Startup Profile** (plugin `androidx.baselineprofile` + módulo generador). Es la mejora de arranque con mejor relación esfuerzo/resultado: típicamente **20–50%**. Debe cubrir al menos arranque, login, pantalla principal y scroll de la lista principal.
- **R8 en release siempre**: `isMinifyEnabled = true`, `isShrinkResources = true`, full mode. Reglas `-keep` mínimas y cada una con comentario que la justifique.
- **Nada pesado en `Application.onCreate()`**: cero red, cero BD, cero SDKs de terceros bloqueantes. Usar `androidx.startup` e inicialización perezosa (`dagger.Lazy`, `Provider`).
- Evitar reflexión y anotaciones en runtime: rompen R8 y frenan el arranque.

### 2. Tamaño

- **AAB con splits** por densidad/ABI/idioma (ver [[Requisitos de Google Play 2026]]).
- `resConfigs` limitado a los idiomas soportados.
- Imágenes en **WebP**, drawables vectoriales, sin PNG grandes.
- `debugImplementation` para LeakCanary y herramientas de depuración — **jamás** `implementation`.

### 3. UI fluida

- Jerarquías **planas** con ConstraintLayout; `ViewStub` para contenido diferido; `<merge>` en layouts incluidos. Anidamiento ≤ 3 niveles.
- `RecyclerView`: `ListAdapter` + `DiffUtil` (**nunca** `notifyDataSetChanged()`), `setHasFixedSize(true)` cuando aplique, `RecycledViewPool` compartido en listas anidadas. Nunca una lista dentro de un `ScrollView`.
- **Cero `inflate` y cero parseo en `onBindViewHolder`**: el binding solo asigna datos ya formateados por el ViewModel.
- Sin overdraw: quitar fondos redundantes.
- Glide dimensionado al `ImageView` (`override()`), con placeholder y caché de disco.
- Animaciones ≤ 300 ms; sin animaciones costosas dentro de listas.

### 4. Red (ver también [[Offline-First con Room y Outbox]])

- Timeouts: connect 15 s, read 30 s, write 30 s, callTimeout 45 s. **Nunca infinitos.**
- Caché HTTP de OkHttp (~10 MB) con `max-stale` de respaldo cuando no hay red.
- Pedir solo lo que se usa: `?select=id,nombre,precio`, paginación ≤ 50 filas.
- Imágenes **comprimidas y redimensionadas en el dispositivo** antes de subir (máx. 1600 px lado mayor, WebP/JPEG q≈80).
- Respetar **Ahorro de datos** (`ConnectivityManager.getRestrictBackgroundStatus`): sin Wi-Fi, no hacer prefetch ni bajar multimedia pesada.
- Nunca bloquear la primera pantalla esperando la red: renderizar caché local y refrescar.

---

## Medición

- Módulo **Macrobenchmark** con `StartupTimingMetric` y `FrameTimingMetric` para los flujos críticos, corriendo en CI sobre un dispositivo/emulador fijo.
- Perfetto / Android Studio Profiler para investigar regresiones.
- **Play Console Vitals** como fuente de verdad en campo.
- Todo PR que toque arranque, listas o sincronización **reporta el número antes/después**.

> [!note] Estado en este proyecto
> Nada de esto está implementado todavía (sin R8 configurado, sin Baseline Profile, sin módulo de benchmark). Registrado como **P-008** en [[Deuda Técnica - Pendientes]]. No es urgente con una sola pantalla, pero sí antes de publicar.

---

## Relaciones

- [[Requisitos de Google Play 2026]]
- [[Offline-First con Room y Outbox]]
- [[Toolchain Android 2026 - AGP, Gradle y JDK]]
- [[Estándar de Ingeniería Android]]
- [[Deuda Técnica - Pendientes]] — P-008

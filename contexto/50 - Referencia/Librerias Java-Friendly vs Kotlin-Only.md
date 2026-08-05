---
title: "Librerías Java-Friendly vs Kotlin-Only"
tags:
  - referencia
  - java
  - librerias
date: 2026-07-29
lifecycle: verified
---

# Librerías Java-Friendly vs Kotlin-Only

> [!info] Fuente
> [Retrofit CHANGELOG](https://github.com/square/retrofit/blob/trunk/CHANGELOG.md), [Hilt — Gradle setup](https://dagger.dev/hilt/gradle-setup.html), documentación oficial de Jetpack. Verificado el 2026-07-29.

> [!danger] Por qué esta nota existe
> La documentación oficial de Android está escrita para **Kotlin + corrutinas + Compose**. Un agente que copia esos ejemplos a un proyecto Java produce código que **no compila**. Esta nota traduce el stack recomendado al mundo Java.

---

## ❌ Kotlin-only — NO usar en este proyecto

| Librería | Por qué no |
|---|---|
| **Jetpack Compose** | Requiere el compilador de Kotlin. No existe API Java. |
| **Navigation 3** | Kotlin-only, diseñado para Compose. |
| **Corrutinas / `Flow`** | Usables desde Java solo con bridges incómodos (`BuildersKt`, `Continuation` a mano). No vale la pena. |
| **DataStore (API Kotlin)** | La API idiomática es `Flow`. Para Java existe la **variante RxJava3** (`datastore-preferences-rxjava3`). |
| **Coil** | Kotlin-first. Usar **Glide**. |

## ✅ Java-friendly — el stack de este proyecto

| Área | Elección | Nota de compatibilidad Java |
|---|---|---|
| UI | **Views + XML + ViewBinding** | ViewBinding genera clases Java-friendly; `findViewById` queda prohibido |
| Navegación | **Navigation Component (Fragment) + Safe Args** | Tiene API Java completa; single-Activity |
| Estado | **`ViewModel` + `LiveData`** | API Java nativa. `LiveData` es el equivalente Java de `StateFlow` |
| Asincronía | **Guava `ListenableFuture`** + `ExecutorService` inyectado. Alternativa: **RxJava 3** | Room y Retrofit tienen adaptadores oficiales para ambos |
| DI | **Hilt** (última: 2.59.2) con `annotationProcessor` | ✅ Soporte Java oficial. **Hilt 2.57.1+ requiere Java 17** |
| BD local | **Room** | API Java completa; DAOs pueden devolver `LiveData` y `ListenableFuture` |
| Red | **Retrofit** + convertidor Moshi/Gson | Ver nota sobre Retrofit 3 abajo |
| Imágenes | **Glide** | API Java madura |
| Trabajo diferido | **WorkManager** | `Worker` / `ListenableWorker` en Java; `HiltWorkerFactory` para inyección |
| Listas | `RecyclerView` + **`ListAdapter` + `DiffUtil`** | API Java |
| Paginación | **Paging 3** vía `PagingLiveData` | El puente Java existe pero es incómodo; evaluar caso por caso |

---

## Nota específica: Retrofit 2 vs 3

- **Retrofit 3.0.0** (mayo 2025) es la última versión. Está **escrito en Kotlin** y orientado a desarrolladores Kotlin, **pero es usable desde Java**: mantiene compatibilidad binaria hacia adelante con 2.x.
- Retrofit 3.0.0 depende de **OkHttp 4.12** (no OkHttp 5). OkHttp 4 y 5 son binariamente compatibles, así que se puede subir OkHttp por separado.
- ~~Al usar Retrofit 3 se arrastra una **dependencia transitiva de Kotlin** (por OkHttp). No obliga a escribir Kotlin, pero suma peso al APK.~~ **Refutado — ver el callout de abajo.**

> [!danger] Corregido el 2026-08-04 — Kotlin ya está en el APK, quiera o no
> Se inspeccionó `debugRuntimeClasspath` del proyecto: **`kotlin-stdlib 2.2.10` ya está en el
> classpath de runtime**, con 47 referencias. Lo traen `androidx.activity`, `appcompat`,
> `core`, `annotation` y `lifecycle` — es decir, **toda AndroidX moderna está escrita en
> Kotlin** y su `-jvm` depende del stdlib.
>
> Consecuencias para las decisiones que se estaban tomando con este criterio:
>
> - **P-007 (Retrofit 3):** el argumento del tamaño **no aplica**. Decidir por adaptadores y
>   cancelación, no por peso.
> - **Navigation Component (P-015):** tampoco "agrega Kotlin". Ver
>   [[Plan Fase 0c - Deuda P1 y P2]] §3.1.
> - Lo que **sigue** valiendo es el criterio de **ergonomía**: `Flow`, `suspend` y Compose son
>   inutilizables cómodamente desde Java, y eso no cambia porque el stdlib esté presente. La
>   tabla de abajo se mantiene **por API, no por peso**.
>
> Regla que queda: *"¿es usable desde Java sin bridges?"* — no *"¿arrastra Kotlin?"*.
- Adaptadores disponibles para Java: `CompletableFuture` (Java 8), **Guava `ListenableFuture`**, RxJava.

> [!note] Estado en este proyecto
> Hoy se usa **Retrofit 2.11.0** con llamadas `.execute()` síncronas dentro de un `ExecutorService`. Funciona y es simple, pero no usa adaptadores. Al agregar el segundo repositorio conviene decidir: seguir con `execute()` + Executors, o adoptar `ListenableFuture`. Ver **P-007** en [[Deuda Técnica - Pendientes]].

## Nota específica: Supabase

El SDK oficial (`supabase-kt`) es **Kotlin Multiplatform con funciones `suspend`** — inutilizable cómodamente desde Java. Por eso este proyecto consume **PostgREST / Auth / Storage por REST con Retrofit**. Ver [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]] y [[Supabase Auth REST - Login Android]].

---

## Relaciones

- [[Toolchain Android 2026 - AGP, Gradle y JDK]]
- [[Convenciones Java]]
- [[Asincronia en Java para Android]]
- [[Estándar de Ingeniería Android]]
- [[ADR-004 - Java + Views en vez de Kotlin + Compose]]

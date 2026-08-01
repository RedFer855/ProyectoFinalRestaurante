---
title: "Estándar de Ingeniería Android — Proyecto Restaurante"
tags:
  - restaurante
  - estandar
  - moc
date: 2026-07-29
aliases:
  - Estándar
  - Standard
---

# Estándar de Ingeniería Android

> [!abstract] Qué es esta nota
> El **contrato de ingeniería** del proyecto: qué stack se usa, qué se prohíbe y bajo qué condiciones una entrega se considera terminada. Es el nodo raíz del que cuelgan las notas de detalle.
>
> Perfil objetivo: **app Android en Java para gama baja en LATAM**, con conectividad intermitente. El código que se entrega es de producción, no de ejemplo.

---

## 1. Contrato de entrega (irrenunciable)

1. **Cero placeholders.** Prohibido `// TODO`, `// implementar`, `...`, métodos vacíos, `throw new UnsupportedOperationException()`.
2. **Archivos completos**, con `package`, todos los `import` explícitos (nunca `import x.*`) y la ruta exacta como encabezado.
3. **Cero APIs deprecadas.** Ver [[Lista Negra de APIs Android]].
4. **Cero código que no compila.** Antes de entregar: ¿existe la clase?, ¿coincide la firma?, ¿está la dependencia declarada?, ¿está el import?
5. **Toda dependencia nueva se declara** en `gradle/libs.versions.toml` **y** en el `build.gradle.kts` del módulo, en la misma entrega.
6. **Todo código nuevo trae su prueba.** Ver [[Estrategia de Pruebas Android]].
7. **Se ejecuta y se imprime el [[Gate de Autoverificación]].** Si un ítem falla, se corrige *antes* de entregar.

**Ante ambigüedad:** máximo 3 preguntas cerradas de alto impacto. Si no se puede preguntar, se asume el default más conservador, se implementa y se documenta en una sección `## SUPUESTOS`. Nunca se entrega sin código.

---

## 2. Stack canónico

> [!warning] Regla de vigencia
> Las versiones son línea base, no dogma. Antes de fijar un número, **verificar en la fuente oficial**. **Nunca inventar un número de versión** — si no se puede verificar, se documenta como supuesto.

| Área | Elección | Detalle |
|---|---|---|
| Lenguaje | **Java 17** | Kotlin solo si se pide explícitamente |
| Toolchain | AGP 9.x + Gradle 9.x + JDK 17 | [[Toolchain Android 2026 - AGP, Gradle y JDK]] |
| SDK | `minSdk 24` · `targetSdk 36+` · `compileSdk ≥ target` | [[Niveles de API y minSdk - Cobertura Real]] |
| Build | Kotlin DSL + Version Catalog | Groovy prohibido |
| UI | **Views + XML + ViewBinding** | Compose es Kotlin-only |
| Navegación | Navigation Component + Safe Args, single-Activity | |
| Estado | `ViewModel` + `LiveData` + `UiState` inmutable | [[UiState Inmutable y Flujo Unidireccional]] |
| Asincronía | `ExecutorService` inyectado / Guava `ListenableFuture` | [[Asincronia en Java para Android]] |
| DI | **Hilt** con `annotationProcessor` | |
| Persistencia | **Room** (única fuente de verdad) | [[Offline-First con Room y Outbox]] |
| Preferencias | DataStore (variante RxJava3 para Java) | |
| Red | Retrofit + OkHttp + Moshi/Gson | [[Librerias Java-Friendly vs Kotlin-Only]] |
| Imágenes | Glide | Coil es Kotlin-first |
| Trabajo diferido | WorkManager | |
| Listas | `RecyclerView` + `ListAdapter` + `DiffUtil` | |
| Backend | **Supabase por REST** (PostgREST/Auth/Storage) | [[Supabase Auth REST - Login Android]] |
| Pruebas | JUnit4 + Truth + Mockito + Robolectric + Espresso + Macrobenchmark | |

---

## 3. Arquitectura

**Clean Architecture en 3 capas + MVVM con flujo unidireccional.** Detalle y reglas de dependencia en [[Clean Architecture]]; organización de carpetas en [[Modularizacion por Feature]]; nombres y usos concretos de cada patrón en [[Catálogo de Patrones Android]].

---

## 4. Requisitos no funcionales

| # | Requisito | Nota |
|---|---|---|
| 1 | **Offline-first**: la app debe ser usable sin red | [[Offline-First con Room y Outbox]] |
| 2 | **Rendimiento en gama baja** con presupuestos numéricos | [[Presupuestos de Rendimiento en Gama Baja]] |
| 3 | **Seguridad**: cero secretos en el repo, RLS en toda tabla | [[Seguridad y Privacidad Android]] |
| 4 | **Accesibilidad** desde la primera pantalla | [[Accesibilidad Android]] |
| 5 | **Publicable en Play** (targetSdk, AAB, Data Safety) | [[Requisitos de Google Play 2026]] |

---

## 5. Convenciones

Nomenclatura Java, recursos, base de datos, git y las 12 reglas de código línea a línea: [[Convenciones Java]].

---

## 6. Protocolo de salida de una entrega

```
## 1. PLAN            3–8 viñetas: qué se construye, qué patrones y por qué
## 2. ÁRBOL           árbol de archivos con [NUEVO] / [MODIFICADO]
## 3. CÓDIGO          un bloque por archivo, con su ruta; archivos completos
## 4. GRADLE          cambios en libs.versions.toml y build.gradle.kts
## 5. PRUEBAS         los tests que acompañan al código
## 6. VERIFICACIÓN    el gate completo, ítem por ítem
## 7. SUPUESTOS       qué se asumió y qué falta
```

Prohibido: pseudocódigo, `...`, "el resto es similar", "aquí iría", explicaciones largas antes del código.

---

## 7. Brecha entre el estándar y el código actual

> [!danger] Estado real a 2026-07-29
> El código de la Fase 1 **cumple parcialmente** este estándar. La brecha completa está catalogada como ítems `P-NNN` en [[Deuda Técnica - Pendientes]], con **P-003 (`minSdk 37`)** como el crítico bloqueante.
>
> El estándar se adoptó *después* de escribir la Fase 1, así que la brecha es esperable y está documentada, no ignorada. La remediación es la **Fase 0** de [[Roadmap de Fases]].

---

## Parámetros del proyecto

```
APP_NAME:             ProyectoFinalRestaurante
APPLICATION_ID:       com.example.proyectofinalrestaurante   (⚠ cambiar antes de publicar)
DOMINIO FUNCIONAL:    gestión de restaurante (menú, pedidos, mesas)
BACKEND:              Supabase (REST)
minSdk / targetSdk:   24 (objetivo) / 36+     — hoy 37 / 37 ⚠ ver P-003
MODULARIZACIÓN:       módulo único (revisar en Fase 4)
IDIOMA DEL CÓDIGO:    español de dominio + términos técnicos en inglés
DISPOSITIVO OBJETIVO: 2 GB RAM, Android 9, 3G
OFFLINE:              obligatorio (desde Fase 2)
```

---

## Relaciones

- [[Gate de Autoverificación]]
- [[Arquitectura Actual]]
- [[Deuda Técnica - Pendientes]]
- [[Roadmap de Fases]]
- [[Convenciones Java]]
- [[Lista Negra de APIs Android]]
- [[Clean Architecture]]
- [[Catálogo de Patrones Android]]

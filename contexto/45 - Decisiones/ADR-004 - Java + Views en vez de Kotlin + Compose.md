---
title: "ADR-004 — Java + Views/XML en vez de Kotlin + Compose"
tags:
  - adr
  - decision
date: 2026-07-29
estado: aceptado
---

# ADR-004 — Java + Views/XML en vez de Kotlin + Compose

## Contexto

El proyecto se escribe en **Java**, por decisión del equipo. Esto no es una preferencia menor: condiciona todo el stack de UI, porque buena parte del ecosistema Android moderno es **Kotlin-only**:

- **Jetpack Compose** requiere el compilador de Kotlin. No tiene API Java.
- **Navigation 3** es Kotlin-only, diseñado para Compose.
- **Corrutinas y `Flow`** son usables desde Java solo con bridges incómodos.

Además, toda la documentación oficial de Android está escrita para Kotlin + corrutinas + Compose. Un agente que copia esos ejemplos a un proyecto Java produce **código que no compila** — es la fuente #1 de error en este contexto.

## Decisión

Stack de UI: **Views + XML + ViewBinding**, con:
- **Navigation Component (Fragment) + Safe Args**, arquitectura single-Activity.
- `ViewModel` + `LiveData` (equivalente Java de `StateFlow`).
- Asincronía con `ExecutorService` inyectado o Guava `ListenableFuture` — ver [[Asincronia en Java para Android]].

**Compose y Navigation 3 quedan explícitamente fuera del proyecto**, y se documenta el porqué para que ningún agente los introduzca por costumbre.

## Alternativas consideradas

| Opción | Pro | Contra | ¿Elegida? |
|---|---|---|---|
| Migrar todo a Kotlin + Compose | Stack moderno, documentación oficial directa, menos boilerplate | Contradice la decisión de lenguaje del equipo; reescritura completa; curva de aprendizaje en medio del proyecto | ❌ |
| Java + Compose vía interop | Conservar Java | **No existe**: Compose no tiene API Java. Técnicamente imposible | ❌ |
| Módulos mixtos (Java para lógica, Kotlin para UI) | Lo mejor de cada mundo | Dos lenguajes en un proyecto chico; complejidad de build; el equipo debe dominar ambos | ❌ |
| **Java + Views + XML + ViewBinding** | Stack maduro, API Java completa, todo el equipo lo domina | Más boilerplate que Compose; Views es "modo mantenimiento" a largo plazo | ✅ |

## Consecuencias

- **Se gana:** todo el stack tiene API Java de primera clase; cero riesgo de código que no compila por copiar ejemplos de Compose; el equipo trabaja en el lenguaje que domina.
- **Se sacrifica:** más código por pantalla que en Compose; a largo plazo, Views recibe menos features nuevas.
- **Consecuencia operativa:** cualquier ejemplo de la documentación oficial hay que **traducirlo** al mundo Java antes de usarlo. Ese mapeo está en [[Librerias Java-Friendly vs Kotlin-Only]] — es lectura obligatoria antes de agregar una dependencia.
- **Nota de peso:** algunas librerías Java-friendly arrastran igual una dependencia transitiva de Kotlin (ej. Retrofit 3 vía OkHttp 4.12). No obliga a escribir Kotlin, pero suma peso al APK — relevante para [[Presupuestos de Rendimiento en Gama Baja]].
- **Revisión:** si en el futuro el equipo adopta Kotlin, este ADR se marca *reemplazado* y se planifica una migración por módulos, no de golpe.

---

## Relaciones

- [[Librerias Java-Friendly vs Kotlin-Only]]
- [[MVVM en Android (ViewModel + LiveData)]]
- [[Asincronia en Java para Android]]
- [[Convenciones Java]]
- [[ADR-001 - Arquitectura por capas (ui-domain-data) en Android con Java]]
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]]
- [[Estándar de Ingeniería Android]]

---
title: "ADR-001 — Arquitectura por capas (ui/domain/data) en Android con Java"
tags:
  - adr
  - decision
date: 2026-07-29
estado: aceptado
---

# ADR-001 — Arquitectura por capas (ui/domain/data) en Android con Java

## Contexto

El proyecto arranca desde el esqueleto por defecto de Android Studio (un módulo `app`, todo en un solo paquete). Se busca replicar el mismo espíritu de Clean Architecture usado en el proyecto Bimbo (`CapaUI`/`CapaAplicacion`/`CapaDatos`/`CapaDominio`) pero adaptado a un proyecto Android chico, en Java + XML Views.

## Decisión

Un único módulo Gradle `app`, separado por **paquetes** (no módulos Gradle separados): `ui`, `domain`, `data`, `core`. `domain` no depende de nada; `data` implementa las interfaces de `domain`; `ui` solo conoce `domain`.

## Alternativas consideradas

| Opción | Pro | Contra | ¿Elegida? |
|---|---|---|---|
| Multi-módulo Gradle (`:domain`, `:data`, `:ui` como módulos separados) | Aislamiento fuerte, build incremental más rápido a gran escala | Sobre-ingeniería para un proyecto de fase 1 con una sola pantalla; complica el setup de Gradle | ❌ |
| Todo en un solo paquete (default de Android Studio) | Cero fricción para empezar | Sin separación de responsabilidades; difícil de testear; no replica el patrón de Bimbo | ❌ |
| Paquetes por capa dentro de un único módulo `app` | Separación clara de responsabilidades, testeable, fricción mínima de setup | Menos aislamiento que módulos Gradle reales (nada impide técnicamente que `domain` importe algo de `data` — es disciplina, no un límite duro del compilador) | ✅ |

## Consecuencias

- Se gana: testeable, patrón familiar (mismo nombre conceptual que Bimbo), fácil de entender para cualquier agente que ya conozca el proyecto Bimbo.
- Se sacrifica: el límite entre capas no lo impone el compilador (a diferencia de proyectos `.csproj` separados) — depende de disciplina + revisión. Si el proyecto crece mucho, reevaluar migrar a módulos Gradle reales.
- Deuda registrada: ninguna todavía.

---

## Relaciones

- [[Arquitectura Actual]]
- [[Clean Architecture]]

---
title: Roadmap de Fases — Proyecto Restaurante
tags:
  - restaurante
  - roadmap
  - fases
date: 2026-07-29
---

# Roadmap de Fases — Proyecto Restaurante

> [!info] Propuesta editable
> Este orden es una propuesta razonable, no una decisión cerrada — ajustalo a medida que el proyecto avanza. Lo único que conviene mantener es el **patrón de ramas**.

## Patrón de ramas

El desarrollo vive en ramas `feat/faseN-<descripción>` que se mergean a `master` cuando la fase cierra (mismo patrón usado en el proyecto Bimbo).

- Antes de asumir cuál es "la fase actual", verificar con `git branch --show-current` y comparar contra `origin/master` — no dar por sentado el estado.
- Al cerrar una fase: mergear a `master` y actualizar el estado en [[Conocimiento Principal]] y [[Arquitectura Actual]].
- Antes de seguir trabajando en una rama de fase abierta, traer los commits nuevos de `master` para no perder fixes hechos ahí mientras la fase estaba abierta.

---

## Fase 0 — Remediación contra el estándar ⚠️ **prioritaria**

> [!danger] Por qué existe esta fase
> El [[Estándar de Ingeniería Android]] se adoptó **después** de escribir la Fase 1. La auditoría encontró 16 ítems de brecha, incluyendo uno que hace la app **indistribuible**. Ver [[Deuda Técnica - Pendientes]].

**Rama sugerida:** `fix/fase0-estandar`

| Prioridad | Ítem | Qué se corrige |
|---|---|---|
| 1 🔴 | **P-003** | `minSdk 37 → 24` + desugaring. **Sin esto la app no instala en ningún teléfono real.** |
| 2 🔴 | **P-004** | Edge-to-edge e insets en `LoginActivity` |
| 3 🟡 | **P-006** | Java 11 → 17 (junto con P-003) |
| 4 🟡 | **P-005** | Inyectar el `Executor` + primer test real del ViewModel |
| 5 🟡 | **P-010** | Accesibilidad del login |
| 6 🟢 | **P-011**, **P-012**, **P-018** | IDs, color hardcodeado, nombre de la llave, `applicationId` |

Cierre de la fase: el [[Gate de Autoverificación]] aplicado a la Fase 1 pasa sin ❌.

---

## Fases de producto

| Fase | Rama | Contenido | Estado |
|---|---|---|---|
| 1 | `feat/fase1-login` | Login contra Supabase Auth (REST/Retrofit), arquitectura por capas base | 🟡 Funcional, con deuda catalogada |
| **0** | `fix/fase0-estandar` | **Remediación de la brecha contra el estándar** | ⬜ **Siguiente** |
| 2 | `feat/fase2-menu` | CRUD de platillos/menú — **primer módulo con Room y offline-first** | ⬜ No iniciado |
| 3 | `feat/fase3-pedidos` | Creación y seguimiento de pedidos | ⬜ No iniciado |
| 4 | `feat/fase4-mesas` | Gestión de mesas (libre/ocupada/reservada) | ⬜ No iniciado |
| 5 | `feat/fase5-usuarios-roles` | Roles (mesero, cocina, admin) y permisos | ⬜ No iniciado |
| 6 | `feat/fase6-reportes` | Reportes de ventas/consumo | ⬜ No iniciado |

---

## Decisiones que no se pueden postergar

Estas tienen **ventana de oportunidad**: hacerlas tarde cuesta 10× más.

| Decisión | Última oportunidad barata | Ítem |
|---|---|---|
| **Offline-first** (Room + outbox) | Fase 2 — antes de escribir el segundo módulo contra la red | P-014 |
| **Single-Activity + Navigation Component** | Antes de Fase 3 — con pocas pantallas que convertir | P-015 |
| **Feature-first vs layer-first** | Fase 2 — al crear el segundo feature | P-017 |
| **`applicationId` real** | Antes de publicar — después de publicar es irreversible | P-018 |
| **Multi-módulo** | Antes de Fase 3, si el proyecto va a llegar a 5 features | — |

---

## Relaciones

- [[Estándar de Ingeniería Android]]
- [[Deuda Técnica - Pendientes]]
- [[Gate de Autoverificación]]
- [[Arquitectura Actual]]
- [[Offline-First con Room y Outbox]]
- [[Modularizacion por Feature]]
- [[Conocimiento Principal]]

---
title: Plan de Fase 2 — Menú
tags:
  - restaurante
  - plan
  - fase2
  - menu
date: 2026-07-31
lifecycle: draft
---

# Plan de Fase 2 — Menú

> [!info] Rama
> `feat/fase2-menu`, creada el 2026-07-31 desde `feat/fase1-login` (commit `dc1528e`).
> **No** sale de `master`: `master` está en el bootstrap (`886033e`) y no tiene ni el
> login funcional ni Empleados. Ver [[Roadmap de Fases]].

El Menú es el **primer módulo que nace después** de tener el patrón completo (dominio →
data → ViewModel → UI) validado por [[Plan Fase 1d - Modulo Empleados Funcional]]. Es
además el módulo donde [[ADR-005 - Offline-first obligatorio desde la Fase 2]] obliga a
introducir Room, y donde hay que decidir dos cosas que el [[Roadmap de Fases]] marca con
ventana de oportunidad (**P-014** y **P-017**).

---

## Por qué se parte en sub-fases

Meter "CRUD + imágenes + Room + outbox + reorganización feature-first" en una sola
entrega produce un cambio imposible de revisar y con dos fuentes de fallo mezcladas
(¿falla el CRUD o falla la sincronización?). Se parte así:

| Sub-fase | Contenido | Estado |
|---|---|---|
| **2a** | CRUD de **platillos y categorías** contra Supabase + foto en **Storage** | 🟢 **Implementada** 2026-07-31 — ver [[Módulo Menú]] |
| **2b** | **Room + outbox + `SyncWorker`**: Menú **y** Empleados pasan a offline-first (**P-014**) | 🟢 **Implementada** 2026-08-01 — ver [[Módulo Menú]] y [[Módulo Empleados]] |
| **2c** | **CRUD de Mesas** + catálogo `estado_mesa` + RPC `cambiar_estado_mesa` | 🟡 **Planificada** 2026-08-01 — ver [[Plan Fase 2c - CRUD de Mesas]] |
| **2d** | **CRUD de Clientes** + RPC `buscar_o_crear_cliente` | 🟡 **Planificada** 2026-08-01 — ver [[Plan Fase 2d - CRUD de Clientes]] |
| **2e** | Decisión **P-017** (feature-first vs layer-first) + renombrado de IDs (**P-011**) | ⬜ No planificada |

> [!note] La Fase 2 creció el 2026-08-01
> Nació como "el Menú partido en tres" y hoy incluye Mesas y Clientes, que se numeraron
> `2c`/`2d` porque la Fase 3 quedó reservada para otro contenido. El refactor P-017/P-011,
> que era el viejo `2c`, pasó a `2e` y sigue siendo el cierre de la fase. La tabla de
> equivalencias con los nombres viejos está en [[Roadmap de Fases]].

> [!warning] 2b dejó de ser "la deuda del Menú" y pasó a ser un prerrequisito
> [[Plan Fase 2c - CRUD de Mesas]] y [[Plan Fase 2d - CRUD de Clientes]] **nacen** sobre la
> infraestructura de 2b en vez de repetir el patrón contra-la-red de 2a. Eso convierte a 2b
> en bloqueante de 2c y 2d: no es opcional ni postergable sin multiplicar P-014 por tres.

---

## La deuda que 2a contrae a propósito

> [!warning] 2a se escribe **contra la red**, no contra Room
> [[Offline-First con Room y Outbox]] dice que la UI observa Room y nunca la red, y
> **P-014** advierte que retro-adaptar offline-first sobre módulos ya escritos contra la
> red "no es un refactor: es una reescritura". 2a hace exactamente lo que esa advertencia
> desaconseja. Es deliberado, y así se acota el daño:
>
> **La UI y el ViewModel de 2a nunca ven la red.** Solo conocen la interfaz
> `MenuRepository` de `domain`. En 2b esa interfaz **no cambia**: lo único que se
> reemplaza es su implementación (`SupabaseMenuRepository` → un repositorio que lee de
> Room y encola escrituras). Lo que 2b sí va a reescribir es la capa `data` del Menú y
> el tipo de retorno (`Result<List<Platillo>>` → `LiveData<List<Platillo>>`), no las
> pantallas.
>
> El costo real de partirlo es ese cambio de tipo de retorno. Se acepta a cambio de poder
> ver el módulo funcionando de punta a punta antes de introducir la complejidad de la
> sincronización.

---

## Relaciones

- [[Plan Fase 2a - CRUD de Platillos y Categorias]] — el plan ejecutable
- [[Plan Fase 1d - Modulo Empleados Funcional]] — el patrón que 2a replica
- [[Offline-First con Room y Outbox]] — lo que 2b tiene que implementar
- [[ADR-005 - Offline-first obligatorio desde la Fase 2]]
- [[Esquema de Base de Datos]]
- [[Deuda Técnica - Pendientes]] — P-011, P-014, P-017
- [[Roadmap de Fases]]

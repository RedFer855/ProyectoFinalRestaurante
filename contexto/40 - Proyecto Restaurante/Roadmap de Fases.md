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
| **2a** | `feat/fase2-menu` | CRUD de platillos y categorías + fotos en Storage | 🟢 **Implementada** 2026-07-31 (falta probarla en dispositivo) — ver [[Módulo Menú]] |
| **2b** | `feat/fase2-menu` | **Room + outbox + `SyncWorker`** — cierra **P-014**. Menú **y** Empleados pasan a offline-first | 🟢 **Implementada** 2026-08-01 (falta probarla en dispositivo) — ver [[Módulo Menú]] y [[Módulo Empleados]] |
| **2c** | `feat/fase2cd-mesas-clientes` | **CRUD de Mesas** + catálogo `estado_mesa` + RPC `cambiar_estado_mesa` | 🟢 **Implementada** 2026-08-03 — ver [[Módulo Mesas]] |
| **2d** | `feat/fase2cd-mesas-clientes` | **CRUD de Clientes** + RPC `buscar_o_crear_cliente` | 🟢 **Implementada** 2026-08-03 — ver [[Módulo Clientes]] |
| **2e** | `feat/fase2e-refactor` | Decisión **P-017** (feature-first vs layer-first) + renombrado de IDs (**P-011**) | ⬜ No planificada |
| 3 | — | **Reservada** — contenido a definir por el usuario | ⬜ Sin asignar |
| 4 | `feat/fase4-pedidos` | Creación y seguimiento de pedidos (**consume** Menú, Mesas y Clientes) | ⬜ No iniciado |
| 5 | ~~`feat/fase5-usuarios-roles`~~ | Roles y permisos | 🟢 **Adelantada** — se implementó en la Fase 1c/1d (`Permisos`, `VistaPorPermiso`, módulo Empleados) |
| 6 | `feat/fase6-reportes` | Reportes de ventas/consumo | ⬜ No iniciado |

> [!info] Renumeración del 2026-08-01
> **Mesas y Clientes son `2c` y `2d`.** Estuvieron unas horas propuestas como `3a`/`3b`; se
> renumeraron a pedido del usuario porque **la Fase 3 queda reservada** para un contenido
> distinto, todavía sin definir.
>
> Como consecuencia, **el antiguo `2c` —el refactor P-017/P-011— pasó a `2e`**. Seguía
> siendo el cierre de la Fase 2, así que quedar al final es coherente.
>
> | Nombre viejo | Nombre actual | Qué es |
> |---|---|---|
> | `2c` (hasta 2026-08-01) | **`2e`** | Refactor P-017 + P-011 |
> | `3a` (propuesto 2026-08-01) | **`2c`** | CRUD de Mesas |
> | `3b` (propuesto 2026-08-01) | **`2d`** | CRUD de Clientes |
>
> Cualquier nota anterior que diga *"2c (P-017/P-011)"* o *"Fase 3a/3b"* se lee con esta tabla.
>
> **Mesas y Clientes van delante de Pedidos**, que sigue en la fase 4. El motivo es de
> dependencias, no de gusto: un pedido referencia `id_mesa` y `id_cliente`, así que construir
> Pedidos primero obligaría a maquetar las dos cosas que aún no existen.
>
> **2b va antes que 2c/2d** para no contraer la deuda de P-014 tres veces: Mesas y Clientes
> **nacen** offline-first en vez de escribirse contra la red y reescribirse después.
> `feat/fase5-usuarios-roles` se marcó como adelantada porque su contenido ya está hecho.

---

## Decisiones que no se pueden postergar

Estas tienen **ventana de oportunidad**: hacerlas tarde cuesta 10× más.

| Decisión | Última oportunidad barata | Ítem |
|---|---|---|
| ~~**Offline-first** (Room + outbox)~~ ✅ | Cerrada en la Fase **2b** (2026-08-01). La ventana se aprovechó a tiempo: Mesas, Clientes y Pedidos **nacen** sobre la infraestructura en vez de contraer la deuda de nuevo | ~~P-014~~ |
| **Single-Activity + Navigation Component** | Antes de Fase 4 — con pocas pantallas que convertir | P-015 |
| **Feature-first vs layer-first** | Fase 2 — al crear el segundo feature. **Ya se pasó el umbral**: desde la 2a hay tres features (`login`, `empleados`, `menu`) y sigue layer-first | P-017 |
| **`applicationId` real** | Antes de publicar — después de publicar es irreversible | P-018 |
| **Multi-módulo** | Antes de Fase 4 (Pedidos), si el proyecto va a llegar a 5 features | — |

---

## Relaciones

- [[Protocolo de Ejecución de un Plan]] — lo que cualquier agente lee antes de tomar un plan
- [[Plan Fase 2b - Offline-First con Room y Outbox]]
- [[Plan Fase 2c - CRUD de Mesas]] · [[Plan Fase 2d - CRUD de Clientes]]
- [[Estándar de Ingeniería Android]]
- [[Deuda Técnica - Pendientes]]
- [[Gate de Autoverificación]]
- [[Arquitectura Actual]]
- [[Offline-First con Room y Outbox]]
- [[Modularizacion por Feature]]
- [[Conocimiento Principal]]

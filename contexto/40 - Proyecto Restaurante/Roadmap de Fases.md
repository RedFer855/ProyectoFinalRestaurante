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
| 2 | `feat/fase2-menu` | Menú, partida en **2a** (CRUD de platillos/categorías + Storage), **2b** (Room + outbox) y **2c** (P-017/P-011) | 🟢 **2a implementada** 2026-07-31 (falta probarla en dispositivo) — ver [[Módulo Menú]] |
| **2b** | `feat/fase2b-offline` | **Room + outbox + `SyncWorker`** — cierra **P-014**. El Menú pasa a offline-first | 🟡 **Planificada** 2026-08-01 — ver [[Plan Fase 2b - Offline-First con Room y Outbox]] |
| **3a** | `feat/fase3-mesas-clientes` | **CRUD de Mesas** + catálogo `estado_mesa` + RPC `cambiar_estado_mesa` | 🟡 **Planificada** 2026-08-01 — ver [[Plan Fase 3a - CRUD de Mesas]] |
| **3b** | `feat/fase3-mesas-clientes` | **CRUD de Clientes** + RPC `buscar_o_crear_cliente` | 🟡 **Planificada** 2026-08-01 — ver [[Plan Fase 3b - CRUD de Clientes]] |
| 4 | `feat/fase4-pedidos` | Creación y seguimiento de pedidos (**consume** Menú, Mesas y Clientes) | ⬜ No iniciado |
| 5 | ~~`feat/fase5-usuarios-roles`~~ | Roles y permisos | 🟢 **Adelantada** — se implementó en la Fase 1c/1d (`Permisos`, `VistaPorPermiso`, módulo Empleados) |
| 6 | `feat/fase6-reportes` | Reportes de ventas/consumo | ⬜ No iniciado |

> [!info] Reordenamiento del 2026-08-01
> **Mesas y Clientes se adelantaron delante de Pedidos**, y Pedidos pasó de fase 3 a fase 4.
> El motivo es de dependencias, no de gusto: un pedido referencia `id_mesa` y `id_cliente`,
> así que construir Pedidos primero obligaría a maquetar las dos cosas que aún no existen.
> El roadmap se declara editable desde el principio; esto es un ajuste de ese tipo.
>
> **2b va antes que 3a/3b** para no contraer la deuda de P-014 tres veces: Mesas y Clientes
> **nacen** offline-first en vez de escribirse contra la red y reescribirse después.
> `feat/fase5-usuarios-roles` se marcó como adelantada porque su contenido ya está hecho.

---

## Decisiones que no se pueden postergar

Estas tienen **ventana de oportunidad**: hacerlas tarde cuesta 10× más.

| Decisión | Última oportunidad barata | Ítem |
|---|---|---|
| **Offline-first** (Room + outbox) | Fase **2b**, ya planificada. ⚠️ **La ventana se cierra acá**: si Mesas y Clientes se escriben contra la red, la deuda se multiplica por tres | P-014 |
| **Single-Activity + Navigation Component** | Antes de Fase 4 — con pocas pantallas que convertir | P-015 |
| **Feature-first vs layer-first** | Fase 2 — al crear el segundo feature. **Ya se pasó el umbral**: desde la 2a hay tres features (`login`, `empleados`, `menu`) y sigue layer-first | P-017 |
| **`applicationId` real** | Antes de publicar — después de publicar es irreversible | P-018 |
| **Multi-módulo** | Antes de Fase 3, si el proyecto va a llegar a 5 features | — |

---

## Relaciones

- [[Protocolo de Ejecución de un Plan]] — lo que cualquier agente lee antes de tomar un plan
- [[Plan Fase 2b - Offline-First con Room y Outbox]]
- [[Plan Fase 3a - CRUD de Mesas]] · [[Plan Fase 3b - CRUD de Clientes]]
- [[Estándar de Ingeniería Android]]
- [[Deuda Técnica - Pendientes]]
- [[Gate de Autoverificación]]
- [[Arquitectura Actual]]
- [[Offline-First con Room y Outbox]]
- [[Modularizacion por Feature]]
- [[Conocimiento Principal]]

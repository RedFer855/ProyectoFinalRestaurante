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

### Continuaciones planificadas el 2026-08-04

La deuda se repriorizó completa y quedó en tres bandas, con un plan por banda:

| Rama | Contenido | Estado |
|---|---|---|
| `fix/fase0b-deuda-p0` | **P0** — P-018 (`applicationId`, riesgo irreversible), P-029 (delta que pierde filas), P-009 (sesión persistida + refresh), P-004 (verificación física) | 📋 [[Plan Fase 0b - Cierre de la deuda P0]] |
| `fix/fase0c-deuda-p1` | **P1** — pase A (errores: P-016+P-019+P-001) y pase B (UI: P-015+P-011+P-017) | 📋 [[Plan Fase 0c - Deuda P1 y P2]] |
| `fix/fase0c-deuda-p2` | **P2** — pase C (infra: P-028, P-008a, P-024) | 📋 idem |

> [!warning] El pase B compite con la Fase 3 por los archivos de `ui/`
> Los dos tocan las mismas 22 clases. La recomendación del plan es **Fase 3 primero**: el pase
> B crece linealmente con la cantidad de pantallas (+2 sobre 17 es ~12% más caro), mientras
> que postergar el tiempo real posterga lo único que se pidió como funcionalidad.

---

## Fases de producto

| Fase | Rama | Contenido | Estado |
|---|---|---|---|
| 1 | `feat/fase1-login` | Login contra Supabase Auth (REST/Retrofit), arquitectura por capas base | 🟡 Funcional, con deuda catalogada |
| **0** | `fix/fase0-estandar` | **Remediación de la brecha contra el estándar** | ⬜ **Siguiente** |
| **2a** | `feat/fase2-menu` | CRUD de platillos y categorías + fotos en Storage | 🟢 **Implementada** 2026-07-31 (falta probarla en dispositivo) — ver [[Módulo Menú]] |
| **2b** | `feat/fase2-menu` | **Room + outbox + `SyncWorker`** — cierra **P-014**. Menú **y** Empleados pasan a offline-first | 🟢 **Implementada** 2026-08-01 (falta probarla en dispositivo) — ver [[Módulo Menú]] y [[Módulo Empleados]] |
| **2c** | `feat/fase2cd-mesas-clientes` | **CRUD de Mesas** + catálogo `estado_mesa` + RPC `cambiar_estado_mesa` | 🟢 **Implementada** 2026-08-01 — Parte A y Parte B, código (73 tests) + servidor verificado. Falta la prueba en dispositivo físico. Ver [[Módulo Mesas]] |
| **2d** | `feat/fase2cd-mesas-clientes` | **CRUD de Clientes** + RPC `buscar_o_crear_cliente` | 🟢 **Implementada** 2026-08-01 — Parte A y Parte B, código (55 tests) + servidor verificado. Falta la prueba en dispositivo físico. Ver [[Módulo Clientes]] |
| **2e** | `feat/fase2e-refactor` | Decisión **P-017** (feature-first vs layer-first) + renombrado de IDs (**P-011**) | ⬜ No planificada |
| **3** | `feat/fase3-pedidos-tiempo-real` | **Tablero de Pedidos en tiempo real** + buzón de notificaciones. Realtime por *Broadcast desde la base* como **señal**, sobre la infraestructura de sync de la 2b | 🟡 **Parte A cerrada** 2026-08-05 · Parte B en curso — ver [[Plan Fase 3 - Pedidos en Tiempo Real]] |
| **3b** | `feat/fase3b-toma-pedido` | **Toma** del pedido: carrito, `detalle_pedido`, mesa y cliente. RPC transaccional con clave de idempotencia. **Cierra P-025 y P-026** | 📋 **Planificada** 2026-08-05 — ver [[Plan Fase 3b - Toma del Pedido]] |
| **3c** | `feat/fase3c-dashboard-reportes` | **Dashboard principal + Reportes** con datos reales. Primer módulo **solo-lectura** del proyecto. **Mata `DatosMaqueta`** | 📋 **Planificada** 2026-08-05 — ver [[Plan Fase 3c - Dashboard y Reportes]] |
| **3d** | — | **Complementos**: CRUD dentro del módulo Menú (misma forma que `platillo`) + selector en el carrito | ⬜ Propuesta en [[Plan Fase 3b - Toma del Pedido]] §7 |
| 5 | ~~`feat/fase5-usuarios-roles`~~ | Roles y permisos | 🟢 **Adelantada** — se implementó en la Fase 1c/1d (`Permisos`, `VistaPorPermiso`, módulo Empleados) |
| ~~6~~ | ~~`feat/fase6-reportes`~~ | Reportes de ventas/consumo | ➖ **Absorbida por la 3c** (2026-08-05) — Reportes va junto al dashboard porque comparten el patrón solo-lectura |

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
> **Mesas y Clientes van delante de Pedidos**, que en ese momento era la fase 4. El motivo es de
> dependencias, no de gusto: un pedido referencia `id_mesa` y `id_cliente`, así que construir
> Pedidos primero obligaría a maquetar las dos cosas que aún no existen.
>
> **2b va antes que 2c/2d** para no contraer la deuda de P-014 tres veces: Mesas y Clientes
> **nacen** offline-first en vez de escribirse contra la red y reescribirse después.
> `feat/fase5-usuarios-roles` se marcó como adelantada porque su contenido ya está hecho.

### Secuencia de la Fase 3 y lo que sigue (planificado 2026-08-05)

```
Fase 3   Parte A ✅ · Parte B en curso    tablero + buzón · Room v5
  ↓
Fase 3b  toma del pedido                  Room v6 · cierra P-025 y P-026 · abre P-030 · ADR-009/010/011
  ↓
[pase B de deuda: P-015 + P-011 + P-017]  ← recomendado acá, ver abajo
  ↓
Fase 3c  dashboard + reportes             Room v7 · muere DatosMaqueta · ADR-012/013
  ↓
Fase 3d  complementos                     CRUD dentro de Menú + selector en el carrito
```

> [!danger] El orden es estricto y **no se paraleliza**
> Las fases pelean por los mismos tres archivos de coordinación: `AppDatabase` (la versión),
> `Migraciones` (la cadena `DE_n_A_n+1`) y `SyncApplication.FactoryDeSync`. Dos ramas que
> suban Room a v6 cada una por su lado producen **una migración corrupta en dispositivos
> reales** — justo lo que `fallbackToDestructiveMigration()` "resuelve" borrando los datos del
> usuario, y este proyecto lo prohíbe.
>
> Además **3c depende de 3b** por contenido, no solo por archivos: sin `crear_pedido` no hay
> filas en `detalle_pedido` y **todos los reportes dan cero**. Un módulo que no se puede ver
> funcionando no se puede dar por terminado.

> [!warning] Una tentación que conviene rechazar
> Las 6 tarjetas locales del dashboard **podrían** entrar en la Fase 3 casi gratis (los
> contadores de Room ya existirían). No hacerlo: *"`DatosMaqueta` desaparece por completo"* es
> un criterio de cierre **binario y verificable con un `grep`**. Partirlo deja media maqueta
> viva durante dos fases y nadie sabe cuál mitad.

> [!question] Dónde va el pase B de deuda (P-015 + P-011 + P-017) — a decidir al llegar
> **Recomendado: entre 3b y 3c.** Es donde el crecimiento de pantallas se detiene (17 hoy → 19
> con la Fase 3 → **24 con 3b** → 24 con 3c, que no agrega ninguna), y la 3c reescribe
> `InicioFragment` y `ReportesFragment` de todos modos: hacerlo *después* del pase los escribe
> **una sola vez**, ya con ViewBinding y ya en su paquete feature-first.
>
> Alternativa aceptable: `3b → 3c → pase B`, asumiendo reescribir dos pantallas. Lo que **no**
> conviene es meterlo antes de 3b.

---

## Decisiones que no se pueden postergar

Estas tienen **ventana de oportunidad**: hacerlas tarde cuesta 10× más.

| Decisión | Última oportunidad barata | Ítem |
|---|---|---|
| ~~**Offline-first** (Room + outbox)~~ ✅ | Cerrada en la Fase **2b** (2026-08-01). La ventana se aprovechó a tiempo: Mesas, Clientes y Pedidos **nacen** sobre la infraestructura en vez de contraer la deuda de nuevo | ~~P-014~~ |
| **Single-Activity + Navigation Component** | Antes de Fase 3 — con pocas pantallas que convertir. La Fase 3 suma un Fragment y una hoja modal más: la ventana se está cerrando | P-015 |
| ~~**Feature-first vs layer-first**~~ ✅ | **Decidido el 2026-08-04**: feature-first en módulo Gradle único, ejecutado junto a P-015 y P-011 porque los tres mueven los mismos archivos. Ver [[Plan Fase 0c - Deuda P1 y P2]] §3.3 | P-017 |
| **`applicationId` real** | Antes de publicar — después de publicar es irreversible. **Clasificado P0**: son 15 min y cierra un riesgo irreversible | P-018 |
| ~~**Multi-módulo**~~ ✅ | **Descartado el 2026-08-04**: trae `api`/`implementation`, builds más lentos y Room repartido, a cambio de poco sobre feature-first en un módulo. Se revisa si aparece un segundo consumidor | — |

---

## Relaciones

- [[Protocolo de Ejecución de un Plan]] — lo que cualquier agente lee antes de tomar un plan
- [[Plan Fase 3 - Pedidos en Tiempo Real]] · [[ADR-008 - Tiempo real como señal, por Broadcast desde la base]]
- [[Plan Fase 3b - Toma del Pedido]] · [[Plan Fase 3c - Dashboard y Reportes]]
- [[Plan Fase 2b - Offline-First con Room y Outbox]]
- [[Plan Fase 2c - CRUD de Mesas]] · [[Plan Fase 2d - CRUD de Clientes]]
- [[Estándar de Ingeniería Android]]
- [[Deuda Técnica - Pendientes]]
- [[Gate de Autoverificación]]
- [[Arquitectura Actual]]
- [[Offline-First con Room y Outbox]]
- [[Modularizacion por Feature]]
- [[Conocimiento Principal]]

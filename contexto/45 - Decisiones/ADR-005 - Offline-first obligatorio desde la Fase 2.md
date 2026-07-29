---
title: "ADR-005 — Offline-first obligatorio desde la Fase 2"
tags:
  - adr
  - decision
date: 2026-07-29
estado: propuesto
---

# ADR-005 — Offline-first obligatorio desde la Fase 2

## Contexto

La app se usa en un **restaurante**, sobre dispositivos de gama baja con Wi-Fi intermitente. Si un mesero no puede registrar un pedido porque "no hay señal", la app no se adopta: el equipo vuelve al papel y el proyecto muere.

Hoy (Fase 1) no hay nada de persistencia local: el login va directo a la red y falla si no hay conexión. Como solo existe una pantalla, todavía no duele.

**El problema es la ventana de oportunidad.** Si los módulos de Menú, Pedidos y Mesas se escriben leyendo directamente de la red, meter una capa offline después **no es un refactor: es reescribir cada pantalla**, cada ViewModel y cada repositorio.

## Decisión

**Room se introduce en la Fase 2 (Menú), junto con el primer módulo de datos reales**, no después. A partir de ese momento:

1. **Room es la única fuente de verdad.** La UI observa Room; la red solo actualiza Room.
2. **Escritura optimista** con `sync_state = PENDIENTE`; la UI responde al instante.
3. **Cola de salida (outbox)** `operaciones_pendientes`, drenada por un `SyncWorker` único de WorkManager.
4. **Sync delta** con `last_sync_at`, nunca descarga completa.
5. **Resolución de conflictos declarada** (por defecto *last-write-wins* con `updated_at` del servidor).
6. **Borrado lógico** (`deleted = true`).
7. **Migraciones explícitas y probadas**; `fallbackToDestructiveMigration()` prohibido en release.
8. **Estado de sincronización visible** para el usuario en cada registro.

Detalle de implementación en [[Offline-First con Room y Outbox]].

## Alternativas consideradas

| Opción | Pro | Contra | ¿Elegida? |
|---|---|---|---|
| Solo red, con timeouts largos y reintentos | Simple, cero infraestructura local | **No resuelve el problema**: sin red no hay app. Timeouts largos empeoran la UX (spinner de 45 s) | ❌ |
| Caché HTTP de OkHttp únicamente | Casi gratis de implementar | Solo sirve para lecturas; **no permite escribir un pedido sin red** | ❌ |
| Offline-first desde ya (Fase 0/1) | Máxima corrección | No hay datos de dominio que cachear todavía; sería infraestructura sin uso | ❌ |
| **Offline-first a partir de la Fase 2** | Se paga el costo cuando hay algo real que persistir, y antes de que se replique el patrón equivocado | Retrasa unos días el arranque de la Fase 2 | ✅ |

## Consecuencias

- **Se gana:** la app funciona en el escenario real de uso. Además la UI se vuelve más simple: observa una sola fuente (Room) en vez de coordinar red + estado local.
- **Se sacrifica:** la Fase 2 arranca más lenta — hay que montar Room, WorkManager, DAOs, migraciones y la política de conflictos antes de ver la primera lista de platillos.
- **Se asume complejidad nueva:** resolución de conflictos, migraciones de esquema y estados de sincronización visibles. Todo eso hay que probarlo (`MigrationTestHelper`, fakes de DataSource) — ver [[Estrategia de Pruebas Android]].
- **Requisito previo:** Hilt (**P-002**), porque `HiltWorkerFactory` es lo que permite inyectar dependencias en un `Worker`. Y Hilt 2.57.1+ exige Java 17, lo que encadena con **P-006** y **P-003**.

> [!note] Estado
> `propuesto` — se marca `aceptado` cuando arranque la Fase 2 y se confirme el diseño de tablas. Registrado como **P-014** en [[Deuda Técnica - Pendientes]].

---

## Relaciones

- [[Offline-First con Room y Outbox]]
- [[Repository Pattern]]
- [[Roadmap de Fases]] — Fase 2
- [[Presupuestos de Rendimiento en Gama Baja]]
- [[Deuda Técnica - Pendientes]] — P-014, P-002
- [[Estándar de Ingeniería Android]]

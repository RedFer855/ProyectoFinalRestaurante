---
title: "ADR-009 — Escrituras multi-tabla por RPC transaccional con clave de idempotencia"
tags:
  - adr
  - decision
  - offline-first
  - pedidos
date: 2026-08-05
estado: aceptado
---

# ADR-009 — Escrituras multi-tabla por RPC transaccional con clave de idempotencia

## Contexto

[[Plan Fase 3b - Toma del Pedido]] necesita crear un pedido — **cabecera + N líneas**, dos
tablas — desde un outbox diseñado para operaciones de una sola fila (`Outbox.encolar`,
`payload_json` por operación, drenado FIFO). Es la primera escritura multi-tabla del proyecto.

## Decisión

**Una operación de outbox, un RPC `crear_pedido(jsonb)` transaccional, con una clave de
idempotencia generada por el dispositivo (`uuid`).** El payload lleva el carrito entero; el
servidor inserta cabecera y líneas en una sola transacción; si la clave ya existe, devuelve el
`id_pedido` existente sin insertar nada de nuevo.

## Alternativas consideradas

| Opción | Contra | ¿Elegida? |
|---|---|---|
| **N operaciones de outbox** (`CREAR_PEDIDO` + un `CREAR_DETALLE` por línea) | El drenado no es atómico: si el proceso muere en la línea 3, cocina ya ve en el tablero un pedido incompleto (el trigger `FOR EACH STATEMENT` de la Fase 3 ya emitió el broadcast al insertar la cabecera), y `detalle_pedido` no tiene `actualizado_en` — el delta no puede detectar la diferencia. Rompe además `Outbox.deFila(idLocal)`: un carrito de 8 líneas son 9 filas en la cola compartida | ❌ |
| **1 operación de outbox, N llamadas HTTP al drenar** | La cola queda coherente, pero `POST` **no es idempotente**: un timeout tras insertar la cabecera deja una huérfana *y* el reintento crea otra. `ClasificadorDeError` marca el timeout como transitorio (correcto) → hasta 3 pedidos duplicados en cocina por un pedido real | ❌ |
| **1 operación de outbox, 1 RPC idempotente** | Es la única de las tres donde ninguna falla parcial es representable: o el pedido existe completo, o no existe | ✅ |

## Implementación (Parte B, verificada 2026-08-05)

- `NuevoPedidoViewModel.confirmar()` genera la `clave_idempotencia` (`UUID`) y la mete en
  `NuevoPedido`.
- `PedidoRepositorioLocal.crear()` escribe la cabecera + las N líneas y encola **una**
  operación `CREAR_PEDIDO` en el outbox, todo en una transacción de Room.
- Al drenar, `PayloadCrearPedido` arma el `jsonb` con la clave, y `PedidoRemoto.crearPedido`
  lo manda al RPC.
- Un `raise exception` del RPC (rol inválido, carrito vacío, >50 líneas, platillo inexistente
  o inactivo) llega como **400** → `ClasificadorDeError` lo clasifica **permanente**: el
  outbox descarta la operación en vez de reintentarla 3 veces.

## Consecuencias

**Se gana:**

- Atomicidad real, sin código de reparación para estados que el diseño simplemente no permite.
- El outbox puede reintentar **con confianza**: un timeout, una respuesta perdida o un
  `SyncWorker` que muere a mitad de drenado nunca duplican el pedido.
- Precedente reutilizable: hoy `crearCliente`/`crearMesa`/`crearPlatillo` conviven con `POST`
  no idempotentes porque el riesgo se tolera (un duplicado es un fastidio visible que el admin
  borra). Un pedido duplicado es dinero y comida — este patrón es candidato a retroalimentar
  esos tres cuando alguien los toque.

**Se sacrifica:**

- Un RPC más grande y con más lógica que un `INSERT` simple — vive en Postgres, no en Android,
  así que no compite con los presupuestos de tamaño del APK.
- La clave de idempotencia agrega una columna y un índice único parcial (`uq_pedido_clave_idempotencia`)
  que hay que recordar poblar en cualquier alta futura de pedido (p. ej. si algún día hay un
  segundo camino de creación).

**Revisar esta decisión si:** aparece una segunda operación multi-tabla con una forma muy
distinta (p. ej. que necesite un id intermedio antes de terminar) — el patrón de "un jsonb, un
RPC, una clave" puede no alcanzar tal cual.

---

## Relaciones

- [[Plan Fase 3b - Toma del Pedido]] — el plan que la implementa, §2
- [[Offline-First con Room y Outbox]] — la infraestructura de outbox que este patrón extiende
- [[ADR-010 - El servidor sella el precio, el del dispositivo es una estimacion]]
- [[Deuda Técnica - Pendientes]]

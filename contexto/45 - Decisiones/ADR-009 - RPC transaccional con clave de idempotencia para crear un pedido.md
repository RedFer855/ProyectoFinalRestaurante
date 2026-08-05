---
title: "ADR-009 — RPC transaccional con clave de idempotencia para crear un pedido"
tags:
  - adr
  - decision
  - pedidos
  - offline-first
date: 2026-08-05
estado: aceptado
---

# ADR-009 — RPC transaccional con clave de idempotencia para crear un pedido

## Contexto

Tomar un pedido escribe **cabecera + N líneas** en una sola transacción (`1 PedidoEntity` +
`N DetallePedidoEntity` apuntando al mismo `id_pedido_local`). No puede partirse en N
operaciones de outbox:

- si se encola la cabecera sola y luego falla alguna línea, queda un pedido huérfano o a
  medias, y el reintento no es atómico;
- y el plan exige que el mismo `CREAR_PEDIDO` se drene **dos veces** (respuesta perdida) sin
  duplicar la cabecera en el servidor (caso de aceptación B2 del [[Plan Fase 3b - Toma del
  Pedido]]).

PostgREST solo garantiza atómico una operación a la vez, no un conjunto, así que el alta
multi-tabla tenía que ser un RPC que recibe todo en un `JSONB` y arranca en una transacción
propia del lado del servidor.

## Decisión

**Un RPC `crear_pedido` que recibe en un solo `JSONB`** la cabecera, la lista de líneas y una
**clave de idempotencia generada por el cliente** (`UUID`, `clave_idempotencia`). El servidor:

1. abre una transacción,
2. inserta la cabecera y las líneas,
3. guarda la `clave_idempotencia` en la cabecera,
4. confirma.

Si la misma `clave_idempotencia` ya existe, el RPC **devuelve el mismo `id_pedido`** en vez de
insertar de nuevo (idempotencia B2). En el cliente:

- `NuevoPedidoViewModel.confirmar()` genera el `UUID` y lo mete en el `NuevoPedido`;
- `PedidoRepositorioLocal.crear()` escribe las N filas + **una** operación `CREAR_PEDIDO` en
  el outbox en una transacción de Room;
- al drenar, `PayloadCrearPedido.serializarRpc(...)` arma el `JSONB` con la clave y
  `PedidoRemoto.crearPedido` lo manda al RPC.

Un `raise exception` del RPC (rol inválido, carrito vacío, >50 líneas, platillo inexistente o
inactivo) llega a PostgREST como **400** → el `ClasificadorDeError` (`data/outbox/`) lo clasifica
**permanente**: el outbox descarta la operación en vez de reintentarla tres veces.

## Alternativas consideradas

| Opción | Pro | Contra | ¿Elegida? |
|---|---|---|---|
| N operaciones de outbox (1 cabecera + N líneas) | Se reusa la maquinaria existente | No atómico; pedido roto si falla a mitad; complejidad de ordenación | ❌ |
| RPC con clave de idempotencia de cliente | Atómico, idempotente (B2), una fila de outbox | Un nuevo RPC; la clave hay que generarla bien en el cliente | ✅ |
| RPC sin idempotencia (confiar en el reintento) | Más simple | la respuesta perdida duplica la cabecera en el servidor | ❌ |

## Consecuencias

- **Se gana**: atómico + idempotente + tolerante a la respuesta perdida en el reintento.
- **Se sacrifica**: no se puede encadenar esta escritura con otras del mismo outbox sin pensar
  bien el orden — para **clientes/mesas offline**, el pedido depende de que esas entidades ya
  hayan resuelto su `id_servidor` (ver [[ADR-011 - El cursor del sync delta es un reloj clock_timestamp mas solapamiento]] y los casos B3–B5).
- **Deudas/patrón que retroalimenta**: el mismo patrón de "RPC transaccional con clave de
  idempotencia" debería retroalimentarse a `crearCliente`, `crearMesa` y `crearPlatillo`, que
  hoy hacen viajes single-Entity simplificados pero drenan el mismo outbox FIFO.

---

## Relaciones

- [[Plan Fase 3b - Toma del Pedido]] §2, §5.3 — el origen de la decisión
- [[Módulo Pedidos]] — el módulo que lo consume
- [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]] — por qué el cliente es opcional
- [[Deuda Técnica - Pendientes]] — cierra la deuda de escritura multi-tabla de Pedidos
- [[Arquitectura Actual]] · [[Offline-First con Room y Outbox]]
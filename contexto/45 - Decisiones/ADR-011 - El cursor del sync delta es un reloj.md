---
title: "ADR-011 — El cursor del sync delta es un reloj"
tags:
  - adr
  - decision
  - sincronizacion
  - offline-first
date: 2026-08-05
estado: aceptado
---

# ADR-011 — El cursor del sync delta es un reloj

## Contexto

**P-025**: `tocar_actualizado_en()` usaba `now()` (= `transaction_timestamp()`, la hora de
**inicio** de la transacción). Con transacciones multi-sentencia y escrituras concurrentes,
una fila puede confirmarse **después** de que otra transacción, iniciada más tarde pero más
corta, ya avanzó la marca de agua del cliente — esa fila no vuelve a entrar en ningún delta
futuro. Con [[ADR-009 - Escrituras multi-tabla por RPC transaccional con clave de idempotencia]]
(`crear_pedido` es multi-sentencia) y varios meseros concurrentes, deja de ser un caso raro.

## Decisión

1. `tocar_actualizado_en()` pasa de `now()` a `clock_timestamp()` (hora real del `UPDATE`).
2. **También el `default` de la columna**, en las 5 tablas que usan el trigger (`pedido`,
   `mesa`, `clientes`, `platillo`, `categoria`) — el trigger es `BEFORE UPDATE`; un pedido
   nuevo es un `INSERT` y toma el `default`. Arreglar solo el trigger deja sin arreglar el
   caso que motiva la fase.
3. `pedido.fecha` **no se toca**: es hora de negocio (cuándo entró el pedido), no el cursor de
   sync — se documenta la distinción para que nadie las unifique más adelante.
4. Mitigación complementaria en `SincronizadorPedidos` (Parte B): **solapamiento de la marca de
   agua**, pidiendo `actualizado_en > marca − 2s` en vez de `> marca`. Seguro porque aplicar
   cada fila ya es idempotente (upsert por `id_servidor` + LWW).

## Lo que esto NO resuelve

`clock_timestamp()` reduce la ventana de "toda la transacción" a "desde la última escritura de
esa fila hasta el commit" — **no la elimina**. El cursor del delta sigue siendo un reloj de
pared. La solución de fondo es una secuencia monótona en vez de un timestamp, que obliga a
cambiar el tipo de la marca de agua en las 5 tablas y sus 5 sincronizadores. Se registra como
**P-030** y se difiere: desproporcionado para esta fase, candidato natural cuando otra fase ya
esté tocando las 5 tablas por otro motivo.

## Alternativas consideradas

| Opción | Contra | ¿Elegida? |
|---|---|---|
| Dejar `now()` | Es exactamente el bug que se está cerrando | ❌ |
| Secuencia monótona ahora | Toca 5 tablas y 5 sincronizadores por un riesgo hoy de ventana sub-100ms; desproporcionado para el alcance de esta fase | ❌ (diferida como P-030) |
| **`clock_timestamp()` + solapamiento de 2s** | Ninguna real — es una mitigación honesta, no una prueba matemática, y se documenta como tal | ✅ |

## Consecuencias

**Se gana:** el caso que motivó la fase (pedidos concurrentes con `crear_pedido`) queda cerrado
en la práctica. Cambia también el orden observable — dos filas de la misma transacción dejan
de compartir timestamp exacto — pero **no rompe nada**: el delta ya ordena por
`(actualizado_en, id_X)` con desempate por id desde que se cerró P-029, y de hecho reduce esa
clase de bug (menos empates exactos, menos filas en la frontera de una página).

**Se sacrifica:** la garantía sigue siendo probabilística, no absoluta. Queda escrito como
P-030 para que nadie lo confunda con "resuelto del todo".

---

## Relaciones

- [[Plan Fase 3b - Toma del Pedido]] — §3
- [[ADR-009 - Escrituras multi-tabla por RPC transaccional con clave de idempotencia]]
- [[Offline-First con Room y Outbox]] — la regla del delta por marca de agua
- [[Deuda Técnica - Pendientes]] — cierra P-025, abre P-030

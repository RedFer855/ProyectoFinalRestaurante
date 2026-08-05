---
title: "ADR-011 — El cursor del sync delta es un reloj: clock_timestamp() + solapamiento"
tags:
  - adr
  - decision
  - sync
  - pedidos
date: 2026-08-05
estado: aceptado
---

# ADR-011 — El cursor del sync delta es un reloj: `clock_timestamp()` + solapamiento

## Contexto

El sync delta de [[Plan Fase 2b - Offline-First con Room y Outbox]] pide filas
`actualizado_en > marca` y guarda la marca al final de la pasada. El plan de Pedidos (§3.3)
exige que el `SincronizadorPedidos` se solape para no perder la fila de cabecera cuando el
pedido se crea por RPC con su propio `actualizado_en`.

Dos problemas de fondo confluyen:

1. **`now()` es la hora de inicio de la transacción** (P-025): una fila escrita en una
   transacción larga queda con un `actualizado_en` anterior a cuando se vuelve visible, y el
   delta siguiente la pierde.
2. **Corte de marca = reloj, no secuencia**: el cursor es un instante, y "mayor que marca"
   excluye por definición cualquier fila con `actualizado_en` exactamente en el borde.

## Decisión

**El cursor del sync delta es un reloj de `clock_timestamp()` con solapamiento, no una
secuencia monótona.**

- El trigger usa `clock_timestamp()` (hora real del momento, no inicio de transacción) — ver
  P-025 — para que el delta vea las filas en el instante en que se confirman.
- El `SincronizadorPedidos` pide `actualizado_en > marca − 2 s` en lugar de `> marca`:
  `OffsetDateTime.parse(marca).minusSeconds(2)` (ver `SincronizadorPedidos.java`). El
  solapamiento hace que una fila re-recibida **no se duplique ni pise con datos viejos**
  porque la re-aplicación es idempotente (upsert por `id_servidor` + LWW) — caso de aceptación
  B8.
- El tablero ordena por `fecha`, no por `actualizado_en`: `clock_timestamp()` cambia el orden
  observable de las filas de una misma transacción, y eso no debe leerse como un bug.

## Qué garantiza y qué no

- **Garantiza**: ninguna fila confirmada se pierde por el borde de marca (con el
  solapamiento, la ventana cubre el retraso de confirmación); el re-proceso es idempotente.
- **NO garantiza**: un orden estrictamente monótono y estable de los cambios, ni la ausencia
  de trabajo repetido (el solapamiento re-baja filas que ya se bajaron — el costo es
  aceptable con upsert idempotente).

## Alternativas consideradas

| Opción | Pro | Contra | ¿Elegida? |
|---|---|---|---|
| Reloj (`clock_timestamp()`) + solapamiento 2 s | Simple, idempotente, cubre el caso real del RPC | Re-baja filas ya vistas; orden no estrictamente monótono | ✅ |
| Secuencia monótona (sequence por cambio) | Orden estable, sin solapamiento | Requiere migración del trigger, columnas nuevas y coordinación con todas las tablas del delta; más caro hoy | ❌ (se descarta por ahora, abre P-030) |
| `now()` sin cambio | Cero trabajo | Sigue perdiendo filas de transacciones largas (P-025) | ❌ |

## Consecuencias

- **Cierra P-025** (el trigger pasa a `clock_timestamp()`).
- **Abre P-030**: migrar el delta a secuencia monótona cuando el costo lo justifique (más de
  una escritura multi-sentencia larga en el sistema).
- El pedido creado por RPC entra al delta con su `actualizado_en` real; el solapamiento evita
  perder la cabecera en el mismo corte.

---

## Relaciones

- [[Plan Fase 3b - Toma del Pedido]] §3.3 — el solapamiento, §4.5 — el orden del tablero
- [[Módulo Pedidos]] — el sincronizador que aplica el reloj + solapamiento
- [[Deuda Técnica - Pendientes]] — cierra **P-025**, abre **P-030**
- [[Offline-First con Room y Outbox]] — la infraestructura que se ajusta
- [[Arquitectura Actual]]
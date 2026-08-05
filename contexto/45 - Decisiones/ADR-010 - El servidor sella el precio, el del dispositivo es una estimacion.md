---
title: "ADR-010 — El servidor sella el precio; el del dispositivo es una estimación"
tags:
  - adr
  - decision
  - pedidos
  - seguridad
date: 2026-08-05
estado: aceptado
---

# ADR-010 — El servidor sella el precio; el del dispositivo es una estimación

## Contexto

`crear_pedido(jsonb)` ([[ADR-009 - Escrituras multi-tabla por RPC transaccional con clave de idempotencia]])
recibe el carrito completo, incluidas cantidades por línea. La app necesita mostrar un total
del carrito **sin red**, antes de que el pedido suba.

## Decisión

**El precio de cada línea lo lee el RPC de `platillo` en el momento del `INSERT`, nunca del
payload.** El campo `precio` que el cliente pudiera mandar se ignora. El cliente usa su caché
local de `platillos` (ya offline-first desde la Fase 2b) para mostrar un total **estimado**
mientras no hay red; cuando el delta de `vista_pedidos` trae el pedido de vuelta, el `total`
sellado por el servidor reemplaza a la estimación.

Si difieren — cambió el precio del platillo entre que se tomó el pedido y que subió — se emite
una `NotificacionEntity` de tipo `PRECIO_AJUSTADO` (Parte B).

## Implementación (Parte B, verificada 2026-08-05)

`LineaCarrito.subtotal()` y `Carrito.total()` muestran la estimación local mientras se arma
el pedido. El total real es el que sella el RPC y el que baja después por el delta de
`vista_pedidos`; `DetallePedidoHoja` muestra las líneas ya selladas por el servidor. No hay
pantalla de "aceptar la diferencia" en esta fase — si el precio cambió entre armar el carrito
y confirmar, el usuario ve directamente el total del servidor, sin aviso intermedio. La deuda
**P-031** ("editar pedido") tendrá que respetar la misma regla al re-sellar precios.

## Alternativas consideradas

| Opción | Contra | ¿Elegida? |
|---|---|---|
| Confiar en el precio del payload | Un APK modificado (o un bug de caché desactualizada) crea un pedido con `precio: 0`. Es la vía más directa a un fraude o a un error contable | ❌ |
| Rechazar el pedido si el precio del payload no coincide con el de `platillo` | Un pedido tomado offline con un precio viejo por horas se rechazaría entero al subir, perdiendo el trabajo del mesero por algo que no puede controlar | ❌ |
| **El servidor sella; el cliente reconcilia después con una notificación** | Ninguna: el pedido nunca se pierde por un precio desactualizado, y el servidor sigue siendo la única fuente de verdad del dinero | ✅ |

## Consecuencias

**Se gana:** el precio del pedido es siempre el que Postgres tenía al momento de crearlo — no
hay forma de manipularlo desde el cliente. La reconciliación es un caso raro (el precio
cambia mientras un pedido offline espera subir) y se resuelve avisando, no rechazando.

**Se sacrifica:** el total que la app muestra mientras arma el carrito es una estimación, no
una garantía — hay que comunicarlo bien en la UI (Parte B) para que no se lea como el precio
final si el usuario mira el carrito mucho tiempo después de cargarlo, sin haber refrescado el
caché de platillos.

---

## Relaciones

- [[Plan Fase 3b - Toma del Pedido]] — §2.3
- [[ADR-009 - Escrituras multi-tabla por RPC transaccional con clave de idempotencia]]
- [[Deuda Técnica - Pendientes]]

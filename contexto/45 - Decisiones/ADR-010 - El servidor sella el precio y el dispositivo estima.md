---
title: "ADR-010 — El servidor sella el precio; el del dispositivo es una estimación"
tags:
  - adr
  - decision
  - pedidos
date: 2026-08-05
estado: aceptado
---

# ADR-010 — El servidor sella el precio; el del dispositivo es una estimación

## Contexto

Tomar un pedido requiere un **precio por línea**. Pero el precio de un platillo puede cambiar
entre que el mesero arma el carrito y el momento en que el servidor confirma el pedido: el
catálogo `platillo.precio` se edita en el Menú (Fase 2a) y se sincroniza por delta. Si el
cliente mandara su precio estimado y el servidor lo guardara tal cual, un mesero con una
versión vieja del catálogo podría "regalar" o "inflar" un pedido.

Hay además una decisión de confianza implícita: quién es la fuente de verdad del precio al
confirmar. El dispositivo no la tiene — su copia de `platillo` puede estar desactualizada o
ser de un delta a medias.

## Decisión

**El servidor sella el precio.** En el RPC `crear_pedido`:

- el cliente manda por línea `id_platillo` y `cantidad` (y su estimación de `precio` solo
  para el carrito en pantalla);
- el servidor **recalcula** `precio * cantidad` desde `platillo.precio` vigente y escribe ese
  valor en `detalle_pedido`;
- el `precio` del dispositivo nunca se persiste como precio cobrado: es una estimación local
  para mostrar el total mientras se arma el pedido.

En el cliente, `LineaCarrito.subtotal()` y `Carrito.total()` muestran la **estimación**; el
total real es el que baje el servidor al reaplicar el delta (o el que devuelva el RPC). El
`DetallePedidoHoja` (E9) muestra las líneas que el servidor selló.

## Qué se muestra al usuario cuando difieren

Cuando el precio sellado del servidor difiere del estimado del dispositivo, el tablero muestra
el **precio del servidor** (fuente de verdad) y no se pide confirmación al mesero: el
descuento/incremento se ve como una diferencia en el total, documentada en [[Módulo Pedidos]].
No hay pantalla de "aceptar la diferencia" en esta fase; se registra la regla para que la UI de
edición (deuda "editar pedido") la respete.

## Alternativas consideradas

| Opción | Pro | Contra | ¿Elegida? |
|---|---|---|---|
| El servidor sella el precio | Fuente de verdad única; el mesero no puede regalar | El cliente puede mostrar un total distinto al real | ✅ |
| El cliente manda el precio y el servidor lo guarda | Total consistente en pantalla | Permite precios viejos/corruptos; la edición del catálogo no se respeta | ❌ |
| Pedir confirmación cuando difiere | Transparencia al mesero | UX pesada para el caso común (casi nunca difiere); más código | ❌ |

## Consecuencias

- **Se gana**: el total cobrado nunca es un precio viejo del catálogo del mesero.
- **Se sacrifica**: en el instante del alta, el total mostrado puede no coincidir con el del
  servidor hasta que vuelve el delta.
- **Seguimiento**: la deuda "editar pedido" debe respetar la misma regla al re-sellar precios.

---

## Relaciones

- [[Plan Fase 3b - Toma del Pedido]] §1.1 (contrato `lineas[].precio`), §5.3
- [[Módulo Pedidos]] — el módulo que lo consume
- [[ADR-009 - RPC transaccional con clave de idempotencia para crear un pedido]] — el RPC que sella
- [[Deuda Técnica - Pendientes]] — la deuda "editar pedido" que debe respetar la regla
- [[Arquitectura Actual]]
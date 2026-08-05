---
title: "ADR-012 — Reportes se agregan en el servidor; Room guarda una instantánea fechada, no la verdad"
tags:
  - adr
  - decision
  - reportes
  - offline-first
date: 2026-08-05
estado: aceptado
---

# ADR-012 — Reportes se agregan en el servidor; Room guarda una instantánea fechada, no la verdad

## Contexto

[[Plan Fase 3c - Dashboard y Reportes]] necesita ventas/pedidos/ticket promedio/top-platillos/
desempeño por mesero, filtrado por Hoy/Semana/Mes. El tablero de la Fase 3 solo retiene **48h**
de pedidos en Room; "ventas del mes" son 30 días que el dispositivo **nunca tuvo**.

## Decisión

**Un solo RPC `reporte_ventas(p_rango text)`** que devuelve `jsonb` con las 5 métricas en una
sola instantánea atómica. El rango lo calcula el **servidor**, en `America/Tegucigalpa`
(`date_trunc` sobre la hora local, no UTC ni el reloj del dispositivo). Los pedidos
**Cancelados** (`id_estado_pedido = 5`) no son ventas. El Android que consuma esto (Parte B)
cachea la respuesta como una instantánea fechada por `generado_en`, no como datos que la app
"posee".

## Por qué no rompe [[ADR-005 - Offline-first obligatorio desde la Fase 2]]

Esa regla dice que Room es la única fuente de verdad **para los datos que la app escribe**. Un
reporte no es un dato que la app escribe: es una lectura derivada sobre una ventana que el
dispositivo nunca tuvo. No hay conflicto que resolver, no hay LWW. Lo que sí violaría
offline-first sería lo contrario — bajar 30 días de pedidos a Room para sumar en el cliente,
contra R6 de la Fase 3 y los presupuestos de gama baja.

## Alternativas consideradas

| Opción | Contra | ¿Elegida? |
|---|---|---|
| 4 RPCs sueltos (ventas, top, desempeño, ticket) | 4 viajes sobre una conexión mala, 4 modos de falla, **4 estados parciales posibles** en la instantánea local (top-5 del mes junto a ventas de hoy) | ❌ |
| Bajar pedidos/detalle a Room y sumar en el cliente | Contradice R6 de la Fase 3 y el presupuesto de retención (48h); calcular en un teléfono gama baja algo que Postgres hace con un índice | ❌ |
| El rango lo calcula el cliente | "Hoy" dependería del reloj/zona del teléfono — dos dispositivos verían ventas distintas del mismo día real | ❌ |
| **1 RPC, servidor calcula el rango, instantánea fechada** | Ninguna real | ✅ |

## Consecuencias

**Se gana:** una instantánea atómica y coherente; un solo lugar donde equivocarse con la zona
horaria y con "los cancelados no cuentan"; sin red, la app muestra la última instantánea **con
su fecha** en vez de mentir con un valor a medias o `L 0.00`.

**Se sacrifica:** el reporte nunca es "en vivo" — tiene la edad de la última vez que se pidió.
Aceptable: nadie espera que un reporte de ventas se actualice pedido a pedido.

**Verificado:** `reporte_ventas` implementado y probado con 10/10 casos de aceptación (rango
Hoy/Semana/Mes, guard de rol, cancelados excluidos, zona horaria, top-5 con más de 5 platillos,
rendimiento con ~1000 pedidos sembrados en ~20ms). `get_advisors(security)` → 0 errores.

---

## Relaciones

- [[Plan Fase 3c - Dashboard y Reportes]] — §3, §4
- [[ADR-005 - Offline-first obligatorio desde la Fase 2]]
- [[ADR-013 - Los modulos de solo lectura no entran al SyncWorker]] — la mitad Android de esta misma decisión, pendiente de la Parte B
- [[Deuda Técnica - Pendientes]]

---
title: "ADR-006 — Clientes sin cuenta propia, captura de datos al pedido"
tags:
  - adr
  - decision
  - clientes
  - pedidos
date: 2026-07-29
estado: aceptado
---

# ADR-006 — Clientes sin cuenta propia, captura de datos al pedido

## Contexto

El enunciado del proyecto pide "administrar... clientes" sin especificar si eso implica que el cliente tiene su propia cuenta (login) o si es staff quien administra sus datos. Al diseñar el módulo de registro se evaluaron ambas lecturas (ver [[Plan de Conexión con Supabase]] y la sesión donde se planteó la pregunta).

El propio esquema de base de datos, subido el 2026-07-29 (ver [[Esquema de Base de Datos]]), ya inclinaba la respuesta: `public.usuarios` tiene `id_empleado INT NOT NULL REFERENCES empleados(id_empleado)` — toda fila de `usuarios` (la tabla con login) exige un empleado. Un cliente no es un empleado, así que **no puede** tener una fila en `usuarios` bajo el esquema actual.

## Decisión

**El cliente no inicia sesión.** Al tomar un pedido, la app muestra una pantalla/paso "¿Sos cliente?" que pide **nombre y apellido** (obligatorio) e **identidad** (opcional — `uq_clientes_identidad` permite `NULL`, pensado para venta de mostrador). Con esos datos:

1. Si viene con `identidad` y ya existe un cliente con esa identidad → se reusa `id_cliente`.
2. Si no existe, o no trae identidad → se crea una fila nueva en `public.clientes`.
3. El pedido se registra con `pedido.id_cliente` apuntando a esa fila.

No hay contraseña, no hay Supabase Auth, no hay Google Sign-In para clientes. La tabla `clientes` es un registro de datos de negocio, administrado indirectamente por quien toma el pedido (mesero/admin) — coherente con "clientes y empleados" administrados en la misma frase del enunciado.

## Alternativas consideradas

| Opción | Pro | Contra | ¿Elegida? |
|---|---|---|---|
| **A — Captura de datos sin login** (nombre+identidad al pedido) | Simple, sin OAuth, sin verificación de correo, coherente con el esquema actual (`usuarios.id_empleado NOT NULL`) y con el alcance real de un proyecto de curso | El cliente no tiene "su" cuenta ni historial propio entre dispositivos | ✅ |
| B — Registro público con correo/Google (self-service) | Permite historial de pedidos por cliente, "app para el cliente" a futuro | Exige nuevo rol/tabla de auth para clientes, políticas RLS de auto-registro, configuración externa de Google Cloud Console (fuera del alcance de un agente), y no está pedido por el enunciado ni por el Roadmap de Fases | ❌ |
| C — `clientes` como extensión 1:1 de `auth.users` (como `perfiles`) | Reusa el patrón de Auth ya construido | Mismo problema que B: el enunciado no pide que el cliente tenga cuenta, y complica el flujo de "venta de mostrador" (cliente sin correo) | ❌ |

## Consecuencias

- El módulo de **Pedidos** (Fase 4) necesita, como parte de su UI, un paso de captura/búsqueda de cliente — no una pantalla de registro separada.
- `clientes` no necesita política RLS de `INSERT` para `anon`/rol de cliente — solo el staff autenticado (`mesero`/`admin`) inserta, vía su propia sesión.
- Si más adelante se quisiera una app para el cliente final (pedir desde su teléfono, ver su historial), esto se reabre como una decisión nueva — no se descarta el escenario B para siempre, solo no aplica hoy.
- Cierra la ambigüedad que quedaba abierta desde [[Plan de Conexión con Supabase]] sobre "¿quiénes son los clientes que se registran?".

---

## Relaciones

- [[Esquema de Base de Datos]]
- [[Plan de Conexión con Supabase]]
- [[Roadmap de Fases]]
- [[Deuda Técnica - Pendientes]] — P-021
- [[Arquitectura Actual]]

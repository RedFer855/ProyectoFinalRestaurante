---
title: "Sesión 2026-08-05 — Fase 3b Toma del Pedido: parte B (E2–E9) y cierre documental (E10)"
tags:
  - sesion
  - pedidos
  - fase-3b
date: 2026-08-05
branch: feat/fase3b-toma-pedido
autor_cambios: opencode (agente) + subagentes E8/E9
---

# Sesión 2026-08-05 — Fase 3b Toma del Pedido: E2 a E9 + E10

> [!success] Resultado
> La **Parte B de la Fase 3b (Toma del Pedido)** quedó completa y pusheada en
> `feat/fase3b-toma-pedido` (commit `a6b6622`): dominio, Room v6, sincronizador de
> `CREAR_PEDIDO`, la UI completa de la toma (E8) y el detalle bajo demanda (E9). Luego se
> ejecutó el **cierre documental E10**: 3 ADRs, [[Módulo Pedidos]], cierre de P-025/026 y
> apertura de P-030/P-031. Suite: **625 tests, BUILD SUCCESSFUL**.

---

## Problema / motivo

Codificar la **Fase 3b** (tomar un pedido con carrito, mesa, cliente y tipo, y subirlo por
RPC idempotente) sobre el tablero en tiempo real de la Fase 3, cumpliendo el estándar
offline-first del repo. Luego documentar el cierre (E10).

## Cambios aplicados

### Parte B código (commit `a6b6622`)
- **E2 `domain`**: `Carrito`, `LineaCarrito`, `NuevoPedido`, `TipoPedido`, `ValidadorPedido`,
  `ReglasPedido` (máx 50 líneas, `puedePedirse`), contrato `PedidoRepository`
  (`crear(NuevoPedido)`, `observarDetalle`).
- **E3 Room v6**: `DetallePedidoEntity` + `DetallePedidoDao` + `DetallePedidoMapper`, columnas
  nuevas de `PedidoEntity`, migración `DE_5_A_6`.
- **E4**: `PayloadCrearPedido` (RPC con `clave_idempotencia`) + `CrearPedidoDto` +
  `PedidoRemoto.crearPedido`.
- **E5**: `PedidoRepositorioLocal.crear()` (escritura optimista transaccional + outbox).
- **E6**: `SincronizadorPedidos` extendido — `CREAR_PEDIDO`, resolución de ids locales,
  **solapamiento −2 s** de la marca de agua, outbox de Clientes inyectado, notificaciones
  `PEDIDO_SIN_MESA`/`PEDIDO_SIN_CLIENTE`. `PayloadOperacion` movido a `data/sync/payload/`.
- **E7**: `NuevoPedidoViewModel` + `EstadoNuevoPedido` + `NuevoPedidoViewModelFactory` +
  tests.
- **E8 (UI)**: `NuevoPedidoFragment`, `CarritoHoja`, `SelectorPlatilloHoja`,
  `SelectorMesaHoja`, `SelectorClienteHoja` + adapters + layouts + FAB → toma. Las hojas
  reciben el mismo ViewModel por `recibir(vm)`.
- **E9 (detalle)**: `DetallePedidoHoja` + `DetallePedidoViewModel` (carga bajo demanda de
  `observarDetalle(id)`) + `LineaPedidoAdapter`, + hook `onVerDetalle` en `PedidoAdapter`.

### Corrección del estándar (durante la revisión)
- `NuevoPedidoViewModel` tenía el literal hardcodeado `"Pedido #%1$d creado."`. Se movió a
  `strings.xml` (`nuevo_pedido_creado`) y `confirmar(String plantillaExito)` ahora lo recibe
  del Fragment, siguiendo el patrón de `PedidosViewModel`/`BuzonViewModel`.

### E10 (documentación, `docs(fase3b)`)
- ADRs **009** (RPC transaccional + idempotencia), **010** (el servidor sella el precio),
  **011** (cursor de sync reloj + solapamiento).
- **[[Módulo Pedidos]]** nuevo (arquitectura, flujo de toma, permisos, sync/RPC).
- Deuda técnica: cierre de **P-025** y **P-026**; alta de **P-030** (secuencia monótona del
  delta) y **P-031** ("editar pedido").

## Verificación

`./gradlew testDebugUnitTest assembleDebug` → **BUILD SUCCESSFUL**, **625 tests** (piso de
fase ≥ 570). La entrega E8 es manual, se verificará con el checklist de prueba manual que se dejó
en la sesión — pendiente de ejecutar en emulador.

## Lo que NO cambió

- **Parte A (Supabase)**: RPC `crear_pedido`, migraciones y pruebas SQL (§5.x) quedan fuera —
  el alcance de esta sesión fue solo codificación. E0/E1 pendientes para una sesión con acceso
  a la base.
- **Editar pedido** no se implementó (deuda **P-031**, explícitamente fuera del scope según
  §1.2).
- Se **re**reutilizó `NuevoPedidoViewModelFactory` (no se duplicó el composition root).
- **Fase 3c** (dashboard/reportes) no se tocó.

---

## Relaciones

- [[Plan Fase 3b - Toma del Pedido]] · [[Plan Fase 3 - Pedidos en Tiempo Real]]
- [[ADR-009 - Escrituras multi-tabla por RPC transaccional con clave de idempotencia]] ·
  [[ADR-010 - El servidor sella el precio, el del dispositivo es una estimacion]] ·
  [[ADR-011 - El cursor del sync delta es un reloj]]
- [[Módulo Pedidos]] · [[Deuda Técnica - Pendientes]] (P-025/026 resueltas, P-030/031 nuevas)
- [[Arquitectura Actual]] · [[Roadmap de Fases]]
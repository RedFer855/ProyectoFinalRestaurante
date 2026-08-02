---
title: Módulo Clientes
tags:
  - restaurante
  - modulo
  - clientes
  - offline-first
  - privacidad
date: 2026-08-01
lifecycle: verified
---

# Módulo Clientes

> [!success] Estado
> 🟢 **Funcional y local-first** (2026-08-01). Parte B (Android, 55 tests) y Parte A
> (servidor: identidad normalizada, RPC, RLS) ejecutadas y verificadas en la misma sesión.
> Ver [[Esquema de Base de Datos]] para el DDL real.

---

## Qué hace

| Historia | Offline | Quién |
|---|---|---|
| Ver la lista y buscar por nombre/identidad | ✅ | admin, mesero |
| Registrar un cliente (identidad y teléfono opcionales) | ✅ se encola | admin, mesero |
| Editar los datos | ✅ se encola | admin, mesero |
| Dar de baja / reactivar | ✅ se encola | solo admin |
| Borrar de verdad (solo sin pedidos) | ✅ localmente, sube encolado | solo admin |
| **Buscar-o-crear por identidad** | ❌ **exige conexión** | admin, mesero — sin consumidor todavía (Fase 4) |

Cocina **no** tiene el módulo — ni en `Permisos.java` ni en la RLS (`"clientes lectura admin
y mesero"` no la incluye).

---

## Arquitectura

```
ui/clientes/       ClientesFragment · ClienteAdapter · ClientesViewModel · EstadoClientes
                    ClientesViewModelFactory · FormularioClienteDialog
domain/             model/Cliente · model/NuevoCliente
                     ValidadorCliente · ReglasCliente · repository/ClienteRepository
data/local/         entity/ClienteEntity · dao/ClienteDao · mapper/ClienteMapper
data/repository/    ClienteRepositorioLocal (local-first) · ClienteRemoto (red)
data/sync/          SincronizadorClientes
```

Mismo patrón offline-first que [[Módulo Mesas]], con dos diferencias:

### `borrarCliente` es un borrado real, no solo baja lógica

Igual que `MenuRepositorioLocal.borrarCategoria`: solo tiene sentido si
`ReglasCliente.puedeBorrarse` (sin pedidos, cacheado en `cantidad_pedidos`). Verificado
contra el servidor: un cliente con un pedido asociado no se puede borrar (el trigger lo
rechaza); uno sin pedidos sí. En la UI, el ⋮ decide dinámicamente entre "Reactivar" /
"Eliminar" / "Dar de baja" según el estado del cliente — ver `ClienteAdapter`.

### `buscarOCrearCliente` no pasa por Room ni por el outbox

Por diseño (Plan Fase 2d, §5.1): el id lo genera el servidor y esta operación exige
conexión, igual que el alta de [[Módulo Empleados]]. `ClienteRepositorioLocal` llama directo
a `ClienteRemoto.buscarOCrear(...)`. Verificado contra el servidor: llamarlo dos veces con
la misma identidad (con y sin guiones) devuelve el mismo `id_cliente` las dos veces. Sigue
sin consumidor — la Fase 4 (Pedidos) decide cómo la usa (**P-026**).

### `nombres`/`apellidos`, no `nombre`/`apellido`

El DDL real de `clientes` (verificado 2026-08-01) usa plural, igual que `empleados` — el
plan asumía singular. Se corrigió del lado Android (`ClienteDto`, `CrearClienteDto`,
`ActualizarClienteDto` serializan `nombres`/`apellidos`), no la base: es exactamente el caso
que el protocolo describe como "si algo ya existe con otro nombre, gana lo que hay en la
base". `clientes.correo` también existe en la tabla real; ningún plan lo pidió y quedó
fuera de `vista_clientes` a propósito.

---

## Datos personales: qué cambia respecto a Mesas/Menú

Este módulo guarda nombre, identidad y teléfono de gente real, no un catálogo. Dos
consecuencias que ya están en el diseño:

- La identidad se muestra en el detalle/búsqueda, no en cada tarjeta de la lista a la vista
  de cualquiera que mire el teléfono del mesero.
- **Nunca viaja por la URL**: la búsqueda es local contra Room, y el único camino de red que
  toca la identidad es el cuerpo `POST` del RPC. Verificado: `anon` no puede leer `clientes`
  directo (`permission denied`, ni siquiera llega a evaluarse la RLS).

Lo que **no** se resolvió en esta sesión, y queda como deuda explícita (§5.4 del plan): la
base local no está cifrada (SQLCipher). Ver **P-027**.

---

## El servidor — Parte A ejecutada (2026-08-01)

Todo lo de [[Plan Fase 2d - CRUD de Clientes]] §2 está aplicado sobre
`mxarlisuueovxvttytcm`: `clientes.actualizado_en`, el índice único sobre la identidad
**normalizada** (reemplazando el único sobre el texto crudo), los triggers
`trg_clientes_actualizado_en` y `trg_clientes_no_borrar_con_pedidos`, el RPC
`buscar_o_crear_cliente()` y la vista `vista_clientes`. Las policies RLS que ya existían
(`clientes lectura admin y mesero`, `clientes escritura admin`, `clientes alta/edición
mesero`) coincidían exactamente con lo que el plan pedía — no hubo que tocarlas.

La tabla estaba vacía (0 filas) al momento de normalizar la identidad, así que no hubo
duplicados que resolver a mano — el caso que el plan advertía como riesgo si ya hubiera
datos cargados.

**Mismo gap de `get_advisors` que Mesas:** `revoke execute ... from anon` en
`buscar_o_crear_cliente()` no alcanzaba porque `PUBLIC` seguía teniendo el permiso; se
corrigió revocando de `PUBLIC` explícitamente. `get_advisors(security)` final: 0 errores.

### Verificación (§2.7 del plan)

Corrida dentro de transacciones revertidas, con los usuarios reales de `perfiles`:

| Caso | Resultado |
|---|---|
| Cocina lee `vista_clientes` | ✅ 0 filas (RLS) |
| Mesero lee `vista_clientes` | ✅ devuelve filas |
| `buscar_o_crear_cliente('Ana','López','0801-1990-1')` dos veces (con y sin guiones) | ✅ mismo `id_cliente` las dos veces |
| `buscar_o_crear_cliente('', 'López')` | ✅ rechazado: *"El nombre y el apellido…"* |
| Cocina llama `buscar_o_crear_cliente(...)` | ✅ rechazado: *"No tenés permiso…"* |
| Dos clientes distintos sin identidad | ✅ ambos se crean, ids distintos |
| Borrar un cliente **con** pedidos | ✅ rechazado: *"No se puede borrar un cliente que ya tiene pedidos…"* |
| Borrar un cliente **sin** pedidos (control) | ✅ se borra |
| `anon` intenta leer `clientes` directo | ✅ `permission denied` |

Tras revertir las transacciones de prueba, `clientes` y `pedido` quedaron en 0 filas —
verificado con `count(*)`.

---

## Conflictos

Last-write-wins, mismo criterio que el resto de los módulos local-first:
*"Un cambio de cliente se perdió: el servidor tenía una versión más reciente."*

---

## Deuda que deja

| Ítem | Qué falta |
|---|---|
| 🟢 **P-026** | El id de cliente offline para Pedidos (Plan Fase 2d, §5.1) sigue sin resolver: un pedido tomado sin red que referencia a un cliente que tampoco tiene `id_servidor` todavía es trabajo de la Fase 4 |
| 🟢 **P-027** | Room guarda datos personales (nombre, identidad, teléfono) sin cifrar — SQLCipher es una decisión propia, diferida a propósito |
| 🟢 **P-001** | `mensajeDeError(...)` copiado por cuarta vez en `ClienteRemoto` |

⬜ **Sin probar en un dispositivo real:** los 55 tests del módulo y las 9 pruebas de
aceptación de la Parte A verifican la lógica; falta abrir la app en un dispositivo con una
sesión de cocina para confirmar que el módulo no aparece, y probar el flujo offline
completo.

---

## Relaciones

- [[Plan Fase 2d - CRUD de Clientes]] — de dónde salió el módulo
- [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]] — la decisión que este módulo respeta
- [[Sesión 2026-08-01 - Fase 2c y 2d completas, Parte B — Mesas y Clientes]]
- [[Módulo Mesas]] — el módulo hermano, mismo estado (Parte A y B completas)
- [[Módulo Menú]] — el patrón replicado (borrado real condicionado a un contador)
- [[Módulo Empleados]] — el patrón replicado (una operación que exige conexión)
- [[Seguridad y Privacidad Android]] — datos personales en el dispositivo (P-027)
- [[Esquema de Base de Datos]] · [[Arquitectura Actual]] · [[Deuda Técnica - Pendientes]]

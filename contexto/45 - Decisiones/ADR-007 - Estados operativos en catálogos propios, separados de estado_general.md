---
title: "ADR-007 — Estados operativos en catálogos propios, separados de estado_general"
tags:
  - adr
  - decision
  - mesas
  - rls
date: 2026-08-01
estado: aceptado
---

# ADR-007 — Estados operativos en catálogos propios, separados de `estado_general`

> [!success] Estado: aceptado y ejecutado (2026-08-01)
> El catálogo `estado_mesa`, `mesa.id_estado_mesa`, la vista `vista_mesas` y el RPC
> `cambiar_estado_mesa()` ya están aplicados sobre el proyecto real (`mxarlisuueovxvttytcm`)
> y verificados con las 9 pruebas de aceptación del §2.7 del plan, simulando admin/mesero/
> cocina. Al aplicar la migración se encontró y se sacó una policy RLS preexistente
> ("mesa cambio de estado mesero") que le daba a mesero `UPDATE` directo sobre `mesa` —
> exactamente el agujero que este ADR advierte. Detalle en [[Módulo Mesas]] y
> [[Esquema de Base de Datos]].

## Contexto

[[Esquema de Base de Datos]] dejaba abierta la pregunta: *"`mesa` y `pedido` van a necesitar
estados más específicos (libre/ocupada/reservada; pendiente/en preparación/listo/entregado/
cancelado) — pendiente de decidir si se agregan a `estado_general` o se separan en catálogos
propios"*.

`estado_general` hoy solo tiene `1=Activo, 2=Inactivo`: es la baja lógica que ya usan
`empleados`, `usuarios` y `clientes`. Mesas necesita algo más: **si la mesa existe en el
salón** (baja lógica) es una pregunta distinta de **si está disponible ahora**
(libre/ocupada/reservada), y las dos preguntas las responde gente distinta.

## Decisión

**Catálogo propio `estado_mesa`, separado de `estado_general`.**

| Concepto | Columna | Valores | Quién lo cambia |
|---|---|---|---|
| ¿La mesa existe en el salón? | `id_estado` → `estado_general` | Activo / Inactivo | Solo admin (baja lógica) |
| ¿Está disponible ahora? | `id_estado_mesa` → `estado_mesa` | Libre / Ocupada / Reservada | Admin y **mesero** |

Meterlos en una sola columna hace imposible expresar "mesa fuera de servicio por reparación,
que además estaba ocupada" y, peor, obliga a que un mesero pueda escribir la misma columna
con la que se da de baja una mesa — no hay forma de que una policy RLS distinga "cambiame el
estado operativo" de "dame de baja" si es la misma columna.

**Separarlos es lo que permite que la RLS —y, más específico todavía, el RPC
`cambiar_estado_mesa()`— distingan quién puede qué.** El mesero solo tiene `UPDATE` a través
del RPC, nunca directo sobre la fila; el admin sí tiene `UPDATE` directo pero solo sobre
`estado_general` en la práctica (crear/editar/dar de baja).

## Alternativas consideradas

| Opción | Pro | Contra | ¿Elegida? |
|---|---|---|---|
| **A — Catálogo propio `estado_mesa`** | RLS/RPC pueden distinguir "cambiar estado" de "dar de baja"; extensible (agregar "En mantenimiento" es un `INSERT` en el catálogo, no una migración de columna) | Una tabla y un `JOIN` más en la vista | ✅ |
| B — Agregar valores a `estado_general` (3=Libre, 4=Ocupada, 5=Reservada) | Reusa la columna que ya existe | Mezcla dos conceptos ortogonales en una columna: un mesero con permiso de "cambiar de 3 a 4" técnicamente podría escribir cualquier valor de la misma columna, incluido el 2 (dado de baja) | ❌ |
| C — Enum de Postgres en vez de tabla catálogo | Sin `JOIN` | Agregar un valor nuevo exige `ALTER TYPE`, más rígido que un `INSERT`; los otros catálogos del proyecto (`estado_general`) ya son tablas, no enums — inconsistente | ❌ |

## Consecuencias

- `mesa` gana `id_estado_mesa int not null default 1 references estado_mesa(id_estado_mesa)`
  además de su `id_estado` existente.
- La única vía de escritura del mesero sobre `mesa` es la función `SECURITY DEFINER`
  `cambiar_estado_mesa(p_id_mesa, p_id_estado_mesa)` — nunca un `UPDATE` directo. Ver
  [[Plan Fase 2c - CRUD de Mesas]] §2.4.
- **`pedido` va a necesitar el mismo tratamiento** cuando llegue la Fase 4: un
  `estado_pedido` propio (pendiente/en preparación/listo/entregado/cancelado), separado de
  `estado_general`, por la misma razón — quien avanza un pedido (cocina) no es quien lo
  cancela (mesero/admin). Queda planteado, no decidido todavía.
- El cliente Android (`domain.model.EstadoMesa`) ya asume esta forma: enum `LIBRE(1) /
  OCUPADA(2) / RESERVADA(3)`, con `porId(int)` devolviendo `null` ante un id que no conoce
  —nunca un valor por defecto que le mienta al mesero—. Si la Parte A ejecuta esta migración
  con otros ids, hay que ajustar el enum antes de dar el módulo por cerrado.

---

## Addendum (2026-08-01): `numero_mesa` y `ubicacion` tampoco existían

Al ejecutar la Parte A, el DDL real de `mesa` resultó ser solo `id_mesa`, `capacidad`,
`id_estado` — sin `numero_mesa` ni `ubicacion`, que el plan asumía. El mismo plan instruye,
para este caso, sacar del contrato lo que no exista en vez de inventar una columna (§2.5,
usando `ubicacion` como ejemplo).

**Se decidió agregarlas de todos modos**, como columnas reales (`numero_mesa int not null
unique`, `ubicacion varchar(100) null`), en vez de aplicar esa regla al pie de la letra.
Razón: a diferencia de un detalle incidental, las historias 1 y 3 del plan
([[Plan Fase 2c - CRUD de Mesas]] §1) tratan el número de mesa como un dato de negocio real
que el admin ingresa al crear la mesa — no como un identificador técnico. Usar `id_mesa`
como "número" (la alternativa sin inventar nada) habría significado:

- Una mesa creada offline no tiene número para mostrar hasta que sincroniza (el id lo asigna
  el servidor) — una regresión real de UX en el módulo que más depende de funcionar sin red.
- Los números de mesa dejarían de poder tener huecos o repetirse tras dar de baja una mesa
  y crear otra, que es como numera un salón real.

Esto no contradice "gana la base, corregí el plan": es aditivo (`add column`, sin tocar
nada existente, cero filas afectadas más allá de un backfill de las 4 mesas que ya había) y
zero cambios en el código Android ya escrito y probado (`MesaDto`, `CrearMesaDto` seguían
funcionando tal cual). El caso de `clientes.nombres`/`apellidos` (ver
[[Esquema de Base de Datos]]) es distinto a propósito: ahí sí existía la columna, solo con
otro nombre, y ahí la corrección fue del lado Android, no de la base.

---

## Relaciones

- [[Plan Fase 2c - CRUD de Mesas]] — el plan que tomó esta decisión en su §2.1
- [[Esquema de Base de Datos]] — el hueco que este ADR responde
- [[Módulo Mesas]]
- [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]] — mismo estilo de ADR para el módulo hermano
- [[Roadmap de Fases]]

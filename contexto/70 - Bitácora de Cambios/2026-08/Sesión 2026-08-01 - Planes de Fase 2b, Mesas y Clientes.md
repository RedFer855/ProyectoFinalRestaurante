---
title: "Sesión 2026-08-01 — Planes de Fase 2b, Mesas y Clientes"
tags:
  - sesion
  - restaurante
  - plan
  - fase2b
  - fase3a
  - fase3b
  - offline
  - mesas
  - clientes
date: 2026-08-01
branch: feat/fase2-menu
autor_cambios: Claude Code
---

# Sesión 2026-08-01 — Planes de Fase 2b, Mesas y Clientes

> [!success] Resultado
> Tres planes ejecutables nuevos —**2b (offline-first)**, **3a (Mesas)** y **3b (Clientes)**—
> más una nota compartida, [[Protocolo de Ejecución de un Plan]], que centraliza las reglas
> que antes se copiaban dentro de cada plan. Cada plan está partido en **Parte A (servidor)**
> y **Parte B (código)**, con la regla explícita de qué hacer si el agente no tiene acceso a
> Supabase. **Cero código Android en esta sesión** — es deliberado.

---

## Problema / motivo

Pedido del usuario: planear la Fase 2b, el CRUD de Mesas y el de Clientes, dejando la parte
de Supabase (migraciones, funciones, RLS) escrita para que la ejecute un agente **con**
acceso, y que cada plan le recuerde al agente que tiene que guiarse por las reglas y buenas
prácticas del proyecto.

## Verificación previa: el árbol estaba en `master`

Al arrancar, el working tree estaba en `master` (`886033e`, el bootstrap), así que los
archivos de la bóveda se leían en su versión vieja. Se verificó con `git log`/`git branch`
antes de escribir nada y se cambió a `feat/fase2-menu`, donde además había **dos commits
nuevos de otra sesión**: `03d4ff5` (fix del filtro + rediseño de la tarjeta, 127 tests) y
`f955486` (cierre de los pendientes de Fase 1). Los planes se escribieron contra ese estado,
no contra el que esta sesión "recordaba".

> [!tip] Vale como recordatorio de la regla 10 del [[contexto/AGENTS|protocolo de la bóveda]]
> La bóveda es la fuente de verdad, pero **la rama importa tanto como el archivo**. Leer
> `Arquitectura Actual.md` estando en `master` devuelve un estado real… de otra rama.

## Documentación escrita

### [[Protocolo de Ejecución de un Plan]] (nueva)

Existe por la regla anti-duplicados: el bloque "cómo se trabaja en este proyecto" estaba
copiado dentro del plan de la Fase 2a y se iba a triplicar con estos tres. Ahora los planes
apuntan acá. Contiene la división Parte A/Parte B, el orden de lectura, las 10 reglas de oro,
qué significa "terminado", y las obligaciones de documentación al cerrar.

### [[Plan Fase 2b - Offline-First con Room y Outbox]] (nueva)

Cierra **P-014**. Room 2.8.4 + WorkManager 2.11.2 (versiones verificadas contra el
`maven-metadata.xml` de Google Maven el 2026-08-01; la 2.12.0 de WorkManager es beta y se
descartó). Ocho entregables, y la Parte A es mínima: el `actualizado_en` con trigger que la
Fase 2a agregó "pensando en el sync delta de 2b" resulta que alcanza — lo único que se
propone es un índice.

### [[Plan Fase 3a - CRUD de Mesas]] (nueva)

CRUD de mesas + estado operativo. La Parte A es la más grande de las tres.

### [[Plan Fase 3b - CRUD de Clientes]] (nueva)

CRUD de clientes respetando [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]],
más la operación buscar-o-crear que la Fase 4 (Pedidos) va a consumir.

## Seis decisiones de diseño que los planes toman

**2b va antes que Mesas y Clientes.** Si los dos módulos nuevos se escriben contra la red
como el Menú, después hay tres módulos para reescribir en vez de uno. P-014 dice
textualmente que retro-adaptar offline-first *"no es un refactor: es una reescritura"*; la
ventana ya se estiró una vez y estirarla dos módulos más es el error que el propio ítem
describe. **Mesas y Clientes nacen offline-first.**

**Mesas y Clientes se adelantan delante de Pedidos.** `pedido` referencia `id_mesa` e
`id_cliente`: construir Pedidos primero obligaría a maquetar las dos cosas que no existen.
Pedidos pasa a Fase 4. El roadmap se declara editable desde el bootstrap; esto es ese tipo
de ajuste. De paso se marcó `feat/fase5-usuarios-roles` como **adelantada**: su contenido ya
se hizo en la Fase 1c/1d.

**`estado_mesa` es un catálogo propio, separado de `estado_general`.** La bóveda dejaba la
pregunta abierta. Son conceptos ortogonales: "¿la mesa existe en el salón?" (baja lógica,
solo admin) contra "¿está disponible ahora?" (libre/ocupada/reservada, también el mesero).
En una sola columna no se puede expresar "mesa fuera de servicio que además estaba ocupada",
y peor: obliga a que el mesero pueda escribir la misma columna con la que se da de baja una
mesa. El plan pide un **ADR-007** para dejarlo asentado, porque `estado_pedido` va a tener
exactamente el mismo problema en la Fase 4.

**Dos RPC `SECURITY DEFINER`, y no por comodidad.** `cambiar_estado_mesa()` existe porque
**la RLS autoriza filas, no columnas**: la matriz de `Permisos`
([[Plan Fase 1c - Maqueta Visual por Roles]]) le da al mesero `CAMBIAR_ESTADO` pero no
`EDITAR`, y sin la función habría que elegir entre darle `UPDATE` libre sobre `mesa` (con un
APK modificado le cambia la capacidad a todo) o negárselo (y no puede trabajar).
`buscar_o_crear_cliente()` existe porque un `SELECT` seguido de un `INSERT` desde el cliente
tiene una ventana de carrera con dos meseros tomando pedidos a la vez. Los dos llevan
`set search_path = public` y `revoke execute from anon`.

**La identidad del cliente se normaliza.** Hoy `uq_clientes_identidad` es único sobre el
texto tal cual, así que `0801-1990-1` y `080119901` conviven como dos clientes distintos y el
buscar-o-crear falla justo cuando importa. El plan lo cambia a único sobre
`regexp_replace(identidad, '[^0-9]', '', 'g')`, con `where identidad is not null` para que
ADR-006 siga funcionando (muchos clientes de mostrador sin identidad no colisionan entre sí).

**Las vistas son el contrato, no las tablas.** Ver el hueco de abajo.

## El hueco que apareció y no se tapó con invención

> [!danger] La bóveda **no documenta las columnas de `mesa` ni de `clientes`**
> [[Esquema de Base de Datos]] las lista en el diagrama de relaciones, pero su DDL nunca se
> registró. Los planes necesitaban nombres de columna para escribir las vistas y los DTOs.
>
> **No se inventaron y se dieron por buenos.** Se hicieron tres cosas:
>
> 1. Los nombres usados (`numero_mesa`, `capacidad`, `ubicacion`, `nombre`, `apellido`,
>    `telefono`) están marcados **explícitamente como suposición** en ambos planes.
> 2. El **paso 0 obligatorio** de la Parte A de cada plan es listar el DDL real y escribirlo
>    en [[Esquema de Base de Datos]]. Se agregó ahí una sección de advertencia.
> 3. **El cliente Android programa contra la vista, no contra la tabla.** Los planes fijan
>    los alias exactos que `vista_mesas` y `vista_clientes` deben exponer, así que si las
>    columnas internas se llaman distinto, se ajusta la vista **una vez** y la Parte B no se
>    entera. Es el mismo patrón que ya usa el Menú con `vista_platillos`.
>
> Regla escrita en los dos planes: si lo real difiere de lo supuesto, **gana la base** y se
> corrige el plan.

## Notas actualizadas

- [[Roadmap de Fases]] — 2b, 3a y 3b agregadas; Pedidos a Fase 4; fase 5 marcada como
  adelantada; la ventana de P-014 marcada como "se cierra acá".
- [[Plan de Fase 2 - Menu]] — 2a a 🟢 implementada, 2b a 🟡 planificada, y el aviso de que 2b
  dejó de ser "la deuda del Menú" para ser prerrequisito de la Fase 3.
- [[Esquema de Base de Datos]] — el hueco de `mesa`/`clientes` y la tabla de cambios
  planificados para la Fase 3 (todavía **no aplicados**).
- [[Conocimiento Principal]] — estado, próximos pasos y navegación.

## Verificación

No hay código que compilar en esta sesión. Lo que sí se verificó:

- **Versiones reales**, no inventadas: Room `2.8.4` y WorkManager `2.11.2` contra el
  `maven-metadata.xml` de Google Maven, con la beta de WorkManager descartada a propósito.
- **Todos los `[[wikilinks]]` nuevos resuelven** a un archivo existente, salvo los
  deliberadamente futuros (`Módulo Mesas`, `Módulo Clientes`, `ADR-007 …`), que marcan algo
  por escribir — comportamiento esperado según el protocolo de la bóveda.
- El repositorio quedó en `feat/fase2-menu`, sin tocar código: `git status` solo muestra
  cambios en `contexto/`.

## Lo que NO se hizo

- **Cero código Android.** Los planes son para ejecutarse en sesiones propias.
- **Cero SQL.** Esta sesión no tenía acceso a Supabase; toda la Parte A está **escrita, no
  aplicada**.
- **No se escribió el ADR-007** (`estado_mesa` como catálogo propio): lo escribe quien
  ejecute la Parte A de la Fase 3a, que es quien va a confirmar contra la base si la decisión
  sobrevive al esquema real.
- **No se creó ninguna rama nueva.** `feat/fase2b-offline` y `feat/fase3-mesas-clientes`
  están **propuestas** en el roadmap; las crea quien ejecute cada fase.
- **P-004** (verificar edge-to-edge en un teléfono físico) sigue siendo el único pendiente
  para mergear `feat/fase1-login` a `master`.

---

## Relaciones

- [[Protocolo de Ejecución de un Plan]] — la nota compartida que nació acá
- [[Plan Fase 2b - Offline-First con Room y Outbox]]
- [[Plan Fase 3a - CRUD de Mesas]] · [[Plan Fase 3b - CRUD de Clientes]]
- [[Roadmap de Fases]] · [[Esquema de Base de Datos]] · [[Plan de Fase 2 - Menu]]
- [[Offline-First con Room y Outbox]] — el patrón que 2b implementa
- [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]]
- [[Deuda Técnica - Pendientes]] — P-014 (se cierra en 2b), P-001, P-009
- [[Módulo Menú]] — el patrón que los tres planes replican
- [[Sesión 2026-08-01 - Rediseño de la tarjeta de platillo y filtro que escondía lo guardado]]

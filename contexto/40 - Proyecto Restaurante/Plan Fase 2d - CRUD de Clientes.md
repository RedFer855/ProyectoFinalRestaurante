---
title: Plan Fase 2d — CRUD de Clientes
tags:
  - restaurante
  - plan
  - fase2d
  - clientes
  - rls
  - privacidad
date: 2026-08-01
lifecycle: draft
---

# Plan Fase 2d — CRUD de Clientes

> [!danger] Leé primero [[Protocolo de Ejecución de un Plan]]
> Ahí está el contrato completo: la división Parte A / Parte B, el orden de lectura, las
> reglas de oro del código y qué significa "terminado". **No es opcional.**

> [!warning] Depende de la Fase 2b
> Clientes **nace offline-first**, sobre la infraestructura de
> [[Plan Fase 2b - Offline-First con Room y Outbox]]. Si 2b no está terminada, este plan no
> se empieza.

> [!info] Este módulo es distinto de los anteriores: son **datos personales**
> Nombre, identidad y teléfono de gente real. No es un catálogo de platillos. Eso cambia la
> RLS, cambia quién puede leer, y agrega obligaciones que [[Seguridad y Privacidad Android]]
> ya documenta. Está tratado en §2.6 y §5.4, y no es opcional.

---

## 0. Reparto del trabajo

| Parte | Contenido | Quién |
|---|---|---|
| **A — Servidor** | Columnas nuevas, vista, triggers, RPC `buscar_o_crear_cliente` y policies RLS. Ver §2 | Agente **con** acceso a Supabase |
| **B — Código Android** | `domain`, `data` (Room + remoto), ViewModel, UI, pruebas. Ver §3 en adelante | Cualquier agente |

**Sin acceso a Supabase: hacés solo la Parte B**, contra el contrato de vista de §2.5.

---

## 1. Qué se construye, y qué **no**

> [!important] [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]] sigue vigente
> **El cliente no inicia sesión.** No hay registro público, no hay Supabase Auth para
> clientes, no hay Google Sign-In. `clientes` es un registro de datos de negocio que
> administra el staff. Este plan **no reabre** esa decisión.

| # | Historia | Rol |
|---|---|---|
| 1 | Ver la lista de clientes y buscar por nombre o identidad | admin, mesero |
| 2 | Registrar un cliente (nombre, apellido, identidad opcional, teléfono opcional) | admin, mesero |
| 3 | Editar los datos de un cliente | admin, mesero |
| 4 | Dar de baja / reactivar un cliente (nunca borrarlo) | solo admin |
| 5 | **Buscar-o-crear** por identidad — la operación que va a usar Pedidos | admin, mesero |
| 6 | Ver cuántos pedidos tiene un cliente | admin, mesero |

**Cocina no ve este módulo.** No necesita los datos personales de nadie para preparar un
plato, y la matriz de [[Plan Fase 1c - Maqueta Visual por Roles|Permisos]] ya no le da `CLIENTES` — la RLS lo respalda del lado del servidor.

La historia 5 es la que existe **para la Fase 4 (Pedidos)**: ADR-006 dice que al tomar un
pedido se pide nombre e identidad, y si ya existe un cliente con esa identidad se reusa. Este
plan construye esa operación ahora, para que Pedidos la consuma en vez de reimplementarla.

`ui/clientes/` hoy es maqueta y lee de `DatosMaqueta`. Al terminar, `DatosMaqueta.Cliente` y
`DatosMaqueta.clientes()` **desaparecen**.

---

## 2. PARTE A — Servidor (solo con acceso a Supabase)

> [!danger] Paso 0 obligatorio: la bóveda **no** documenta las columnas de `clientes`
> Igual que con `mesa`. Antes de migrar: listá el DDL real de `public.clientes`,
> **escribilo en [[Esquema de Base de Datos]]**, y recién entonces adaptá lo que sigue.
> Lo único que la bóveda sí registra es `uq_clientes_identidad UNIQUE (identidad)`, y que
> los `NULL` no colisionan entre sí en Postgres — pensado para venta de mostrador.
>
> Este plan describe el **estado deseado**. Todo va con `IF NOT EXISTS`. Si algo existe con
> otro nombre, **gana la base**: corregí el plan.

### 2.1 Columnas

```sql
alter table public.clientes
    add column if not exists actualizado_en timestamptz not null default now();
```

Y, si no existen ya: `id_estado int not null default 1` → `estado_general` (baja lógica) y
`telefono varchar(20) null`.

`actualizado_en` + su trigger `tocar_actualizado_en()` es lo que habilita el sync delta de
2b. Sin eso, este módulo no puede ser offline-first.

### 2.2 Normalización de la identidad — decidila ahora, no después

`uq_clientes_identidad` es único sobre `identidad` **tal cual se escribe**. Eso significa que
`0801-1990-12345` y `0801199012345` conviven como dos clientes distintos, y el buscar-o-crear
va a fallar en producir la coincidencia justo cuando más importa.

**Decisión de este plan:** único sobre la identidad **normalizada** (sin guiones ni espacios),
igual que 2a hizo con los nombres de platillo:

```sql
drop index if exists uq_clientes_identidad;
create unique index if not exists uq_clientes_identidad_norm
    on public.clientes (regexp_replace(identidad, '[^0-9]', '', 'g'))
    where identidad is not null;
```

El `where identidad is not null` mantiene lo que ADR-006 necesita: **muchos clientes sin
identidad** (venta de mostrador) no colisionan entre sí.

> [!warning] Si ya hay datos cargados, esto puede fallar
> Si existen dos filas cuya identidad normaliza al mismo valor, el índice no se crea.
> Buscá los duplicados **antes** de migrar y resolvelos con el usuario. **No borres filas
> por tu cuenta.**

### 2.3 Triggers

| Trigger | Qué hace |
|---|---|
| `trg_clientes_actualizado_en` | `BEFORE UPDATE` → `actualizado_en = now()` |
| `trg_clientes_no_borrar_con_pedidos` | `BEFORE DELETE` → falla si el cliente tiene pedidos: *"No se puede borrar un cliente que ya tiene pedidos. Dalo de baja en su lugar."* |

Sin pedidos, el `DELETE` se permite: un cliente cargado por error y sin historial no tiene por
qué quedar para siempre. Es la misma lógica que `trg_categoria_no_borrar_con_platillos`.

Válvula de escape con `auth.uid() is null`, y **revocá `EXECUTE` a `anon` y `authenticated`
sobre las funciones de trigger** en la misma migración.

### 2.4 La función que Pedidos va a necesitar — `buscar_o_crear_cliente`

Implementa el paso 1-2-3 de ADR-006 en **una sola** llamada atómica:

```sql
create or replace function public.buscar_o_crear_cliente(
    p_nombre    varchar,
    p_apellido  varchar,
    p_identidad varchar default null,
    p_telefono  varchar default null
) returns int
language plpgsql
security definer
set search_path = public
as $$
declare
    v_id_cliente int;
    v_norm       varchar;
begin
    if rol_actual() not in ('admin', 'mesero') then
        raise exception 'No tenés permiso para registrar clientes.';
    end if;
    if coalesce(btrim(p_nombre), '') = '' or coalesce(btrim(p_apellido), '') = '' then
        raise exception 'El nombre y el apellido del cliente son obligatorios.';
    end if;

    v_norm := nullif(regexp_replace(coalesce(p_identidad, ''), '[^0-9]', '', 'g'), '');

    if v_norm is not null then
        select id_cliente into v_id_cliente
          from public.clientes
         where regexp_replace(identidad, '[^0-9]', '', 'g') = v_norm
         limit 1;

        if found then
            return v_id_cliente;   -- se reusa, no se pisan sus datos
        end if;
    end if;

    insert into public.clientes (nombre, apellido, identidad, telefono)
    values (btrim(p_nombre), btrim(p_apellido), nullif(btrim(p_identidad), ''), nullif(btrim(p_telefono), ''))
    returning id_cliente into v_id_cliente;

    return v_id_cliente;
end;
$$;

revoke execute on function public.buscar_o_crear_cliente(varchar, varchar, varchar, varchar) from anon;
grant  execute on function public.buscar_o_crear_cliente(varchar, varchar, varchar, varchar) to authenticated;
```

Tres cosas deliberadas:

- **Si el cliente ya existe, se devuelve su id y no se tocan sus datos.** Un mesero apurado
  que escribe mal un apellido no debe poder pisar el registro bueno de otro cliente.
- **Es atómica**: un `SELECT` seguido de un `INSERT` desde el cliente tiene una ventana de
  carrera con dos meseros tomando pedidos a la vez. Acá no.
- **`security definer` + `search_path` fijo**, y `revoke … from anon`, por lo mismo que en
  [[Plan Fase 2c - CRUD de Mesas]].

### 2.5 Vista de lectura — **el contrato que la Parte B programa**

```sql
create or replace view public.vista_clientes
with (security_invoker = on) as
select c.id_cliente,
       c.nombre,
       c.apellido,
       c.identidad,
       c.telefono,
       c.id_estado,
       (c.id_estado = 1) as activo,
       (select count(*) from public.pedido p where p.id_cliente = c.id_cliente) as cantidad_pedidos,
       c.actualizado_en
  from public.clientes c;
```

> [!warning] `nombre`, `apellido` y `telefono` son una **suposición**
> No están documentados. El agente de la Parte A los ajusta a la tabla real **y deja la
> vista exponiendo exactamente estos alias**, para que la Parte B no dependa de los nombres
> internos. Si `clientes` guarda el nombre completo en una sola columna, la vista lo parte o
> el plan se corrige — pero se decide **una vez**, acá, no en cada pantalla.

`cantidad_pedidos` es lo que le dice al cliente Android si un cliente se puede borrar sin
preguntárselo al servidor, igual que `cantidad_platillos` en `vista_categorias`.

### 2.6 RLS — acá el criterio es más estricto que en los otros módulos

| Operación | Quién |
|---|---|
| `SELECT` sobre `clientes` | **solo `admin` y `mesero`** — cocina **no** |
| `INSERT` / `UPDATE` | `admin` y `mesero` |
| `DELETE` | solo `admin` (y el trigger lo bloquea si hay pedidos) |

Verificá además que siga revocado el `SELECT` al rol `anon` sobre `clientes` — ya se hizo en
la migración inicial (*"datos personales que no deben ser ni siquiera descubribles antes de
iniciar sesión"*, [[Esquema de Base de Datos]]), pero es exactamente el tipo de cosa que una
migración posterior rompe sin querer.

### 2.7 Verificación de la Parte A

Dentro de una transacción **revertida**, simulando cada rol:

| Caso | Esperado |
|---|---|
| Cocina lee `vista_clientes` | 🚫 0 filas (RLS) |
| Mesero lee `vista_clientes` | ✅ devuelve filas |
| `buscar_o_crear_cliente('Ana','López','0801-1990-1')` dos veces | ✅ **el mismo `id_cliente`** las dos veces |
| Ídem pero la segunda con `'080119901'` (sin guiones) | ✅ **el mismo `id_cliente`** |
| `buscar_o_crear_cliente('', 'López')` | 🚫 *"El nombre y el apellido…"* |
| Cocina llama `buscar_o_crear_cliente(...)` | 🚫 *"No tenés permiso…"* |
| Dos clientes distintos **sin** identidad | ✅ ambos se crean (venta de mostrador) |
| Borrar un cliente con pedidos | 🚫 *"No se puede borrar un cliente que ya tiene pedidos…"* |
| `anon` intenta leer `clientes` | 🚫 |

`get_advisors(security)` → **0 errores**.

---

## 3. PARTE B — Contrato HTTP

| Operación | Verbo y ruta |
|---|---|
| Listar (sync delta) | `GET rest/v1/vista_clientes?select=*&actualizado_en=gt.{marca}&order=actualizado_en.asc&limit=50` |
| Crear | `POST rest/v1/clientes` + `Prefer: return=representation` |
| Editar | `PATCH rest/v1/clientes?id_cliente=eq.{id}` |
| Baja / alta lógica | `PATCH rest/v1/clientes?id_cliente=eq.{id}` con `{"id_estado": 1\|2}` |
| Borrar (solo sin pedidos) | `DELETE rest/v1/clientes?id_cliente=eq.{id}` |
| **Buscar o crear** | `POST rest/v1/rpc/buscar_o_crear_cliente` → devuelve un `int` |

---

## 4. Entregables

| # | Entregable | Contenido |
|---|---|---|
| **E1** | `domain` | `model/Cliente.java`, `model/NuevoCliente.java`, `ValidadorCliente`, `ReglasCliente`, `repository/ClienteRepository.java` |
| **E2** | `data` remoto | `SupabaseClienteApi`, `ClienteDto`, `CrearClienteDto`, `ActualizarClienteDto`, `BuscarOCrearClienteDto` |
| **E3** | `data` local | `ClienteEntity`, `ClienteDao`, mapeo, tipos de outbox (`CREAR_CLIENTE`, `ACTUALIZAR_CLIENTE`, `CAMBIAR_ESTADO_CLIENTE`, `BORRAR_CLIENTE`) |
| **E4** | `SupabaseClienteRepository` | Lecturas por `LiveData` desde Room; escrituras locales + encolado |
| **E5** | ViewModel | `ClientesViewModel`, `EstadoClientes`, Factory. Búsqueda en el ViewModel |
| **E6** | UI | `ClientesFragment`, `ClienteAdapter`, `FormularioClienteDialog`, layouts, strings |
| **E7** | Pruebas | `ValidadorClienteTest` (incluida la normalización de identidad), `ReglasClienteTest`, `SupabaseClienteRepositoryTest`, `ClientesViewModelTest` |

### Reglas de dominio

- **`ValidadorCliente`**: nombre y apellido obligatorios; identidad **opcional**; si viene,
  que tenga al menos 13 dígitos tras normalizar (formato hondureño) — pero **no la rechaces
  por el formato del guion**, normalizá y seguí.
- **`ReglasCliente.normalizarIdentidad(String)`** espeja exactamente el
  `regexp_replace(identidad, '[^0-9]', '', 'g')` del servidor. Es la pieza que permite
  detectar el duplicado antes de gastar un viaje de red, y es Java puro y trivial de testear.
- **`ReglasCliente.puedeBorrarse(Cliente)`** → `cantidadPedidos == 0`, igual que
  `ReglasMenu.puedeBorrarse(Categoria)`.

---

## 5. Trampas concretas

### 5.1 El buscar-o-crear **no** puede ser offline

Es la excepción a la regla de esta fase, y hay que aceptarla explícitamente: el id que
devuelve lo genera el servidor, y sin red no hay id. Dos salidas:

| Opción | Cuándo |
|---|---|
| **A** — Crear el cliente **local** con `id_local` y encolar, como todo lo demás | El flujo normal del CRUD de este módulo |
| **B** — Llamar al RPC y exigir red | Solo si Pedidos necesita el id del servidor **en ese instante** |

**Este plan implementa A**, y deja el RPC expuesto en el repositorio para que **Pedidos**
decida en la Fase 4. La razón: el CRUD de clientes lo usa el admin en la oficina; el que
necesita funcionar sin red es el flujo de pedido, y ese flujo todavía no existe.

> [!note] Esto le deja una decisión abierta a la Fase 4
> Un pedido tomado sin red referencia a un cliente que tampoco tiene id de servidor todavía.
> Resolverlo es trabajo de Pedidos (probablemente encolando ambas operaciones juntas y
> resolviendo el id en el drenado). **Registralo como deuda `P-NNN`** al terminar esta fase
> para que Fase 4 no se lo encuentre de sorpresa.

### 5.2 La identidad se muestra, pero no se expone de más

No la pongas en la lista principal si con el nombre alcanza para identificar la fila. Es un
número de documento: aparece en el detalle y en la búsqueda, no en cada tarjeta a la vista
de cualquiera que mire el teléfono del mesero. Ver [[Seguridad y Privacidad Android]].

### 5.3 Nunca mandes la identidad por la URL

`?identidad=eq.0801...` la deja en logs de servidor, en historiales y en cualquier proxy. La
búsqueda por identidad va **por el RPC** (cuerpo `POST`) o se resuelve **local contra Room**,
que es lo que este plan usa para el CRUD.

### 5.4 Room ahora guarda datos personales en el dispositivo

Un teléfono perdido es una base de clientes perdida. Para esta fase alcanza con **no
guardar más de lo necesario** (nada de notas libres, nada de correo si no se usa) y con
dejar registrado el tema. Cifrar la base local (SQLCipher) es una decisión propia:
**registrala como `P-NNN`** en [[Deuda Técnica - Pendientes]] en vez de improvisarla acá.

### 5.5 El filtro que esconde lo guardado

Mismo caso que el Menú y Mesas. Si hay filtro por estado (activos/inactivos) y se da de baja
un cliente, el cliente desaparece. Aplicá el criterio de
`descartarFiltroQueEsconde(...)`.

---

## 6. Qué NO hacer

| No hagas | Por qué |
|---|---|
| Login, registro público o Google Sign-In para clientes | [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]] lo decidió y sigue vigente |
| Darle acceso a cocina | No necesita datos personales para cocinar |
| Empezar antes de que 2b esté terminada | Reescritura garantizada (**P-014**) |
| Meter la creación del pedido acá | Es la Fase 4 |
| Borrar un cliente con pedidos | El servidor lo rechaza; se da de baja |

---

## 7. Definición de terminado

- [ ] `./gradlew testDebugUnitTest assembleDebug` → **BUILD SUCCESSFUL**, con más tests que al empezar.
- [ ] `DatosMaqueta.Cliente` y `DatosMaqueta.clientes()` eliminados.
- [ ] Un usuario de cocina **no ve** el módulo y el servidor le niega la lectura.
- [ ] Registrar dos veces la misma identidad con y sin guiones reusa el mismo cliente.
- [ ] Cero strings, colores o dimens hardcodeados.
- [ ] El [[Gate de Autoverificación]] impreso ítem por ítem, sin ❌.
- [ ] Deuda registrada: el id de cliente offline para Pedidos (§5.1) y el cifrado de la base local (§5.4).
- [ ] Nota `Módulo Clientes.md` con el formato de [[Módulo Menú]].
- [ ] Nota de sesión, [[Arquitectura Actual]], [[Conocimiento Principal]], [[Esquema de Base de Datos]] y [[Roadmap de Fases]] actualizados.

Fuera del alcance del agente, **lo verifica el usuario**: probarlo en un dispositivo con una
sesión de cocina, para confirmar que el módulo no aparece.

---

## Relaciones

- [[Protocolo de Ejecución de un Plan]] — **léelo primero**
- [[Plan Fase 2b - Offline-First con Room y Outbox]] — **prerrequisito**
- [[Plan Fase 2c - CRUD de Mesas]] — el otro catálogo que Pedidos necesita
- [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]] — la decisión que este plan respeta
- [[Seguridad y Privacidad Android]] — datos personales en el dispositivo
- [[Esquema de Base de Datos]] — hay que documentar ahí el DDL real de `clientes`
- [[Plan Fase 1c - Maqueta Visual por Roles]] · [[Módulo Menú]] · [[Guía de Diseño Visual]]
- [[Deuda Técnica - Pendientes]] — P-001, P-014
- [[Roadmap de Fases]]

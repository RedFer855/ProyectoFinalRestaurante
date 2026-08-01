---
title: Plan Fase 3a — CRUD de Mesas
tags:
  - restaurante
  - plan
  - fase3a
  - mesas
  - rls
date: 2026-08-01
lifecycle: draft
---

# Plan Fase 3a — CRUD de Mesas

> [!danger] Leé primero [[Protocolo de Ejecución de un Plan]]
> Ahí está el contrato completo: la división Parte A / Parte B, el orden de lectura, las
> reglas de oro del código y qué significa "terminado". **No es opcional.**

> [!warning] Depende de la Fase 2b
> Mesas **nace offline-first**: lee de Room y encola escrituras, sobre la infraestructura de
> [[Plan Fase 2b - Offline-First con Room y Outbox]]. Si 2b no está terminada, **este plan
> no se empieza** — escribirlo contra la red significa reescribirlo después, que es
> exactamente lo que advierte **P-014**.
>
> Y es el módulo donde más importa: un mesero marcando una mesa como ocupada en el salón,
> con el Wi-Fi del fondo del local, es el caso de uso que justificó todo el offline-first.

---

## 0. Reparto del trabajo

| Parte | Contenido | Quién |
|---|---|---|
| **A — Servidor** | Catálogo `estado_mesa`, columnas nuevas, vista, triggers, RPC y policies RLS. Ver §2 | Agente **con** acceso a Supabase |
| **B — Código Android** | `domain`, `data` (Room + remoto), ViewModel, UI, pruebas. Ver §3 en adelante | Cualquier agente |

**Sin acceso a Supabase: hacés solo la Parte B**, contra el contrato de la vista que fija
§2.5. Si al programar descubrís que la vista real no coincide, **parás y lo reportás** — no
adaptes el cliente a un esquema que inventaste.

---

## 1. Qué se construye

| # | Historia | Rol |
|---|---|---|
| 1 | Ver el salón: todas las mesas con su número, capacidad y estado | admin, mesero, cocina |
| 2 | Cambiar el estado de una mesa (libre ↔ ocupada ↔ reservada) | admin, **mesero** |
| 3 | Crear una mesa (número, capacidad, ubicación) | solo admin |
| 4 | Editar una mesa | solo admin |
| 5 | Dar de baja / reactivar una mesa (nunca borrarla) | solo admin |
| 6 | Filtrar por estado y buscar por número | los tres |

`ui/mesas/` hoy es maqueta (Fase 1c) y lee de `DatosMaqueta`. Al terminar, `DatosMaqueta.Mesa`
y `DatosMaqueta.mesas()` **desaparecen**, igual que pasó con `Empleado` en 1d y con
`Platillo`/`Categoria` en 2a. El resto de `DatosMaqueta` se queda.

---

## 2. PARTE A — Servidor (solo con acceso a Supabase)

> [!danger] Paso 0 obligatorio: la bóveda **no** documenta las columnas de `mesa`
> [[Esquema de Base de Datos]] lista la tabla y sus relaciones, pero **nunca se registró su
> DDL**. Antes de escribir una sola migración:
>
> 1. Listá las columnas, tipos, constraints y policies reales de `public.mesa`.
> 2. **Escribilas en [[Esquema de Base de Datos]]**, en una sección propia.
> 3. Recién entonces adaptá lo que sigue. Este plan describe el **estado deseado**, no
>    asume el estado actual — por eso todo va con `IF NOT EXISTS`.
>
> Si algo de acá ya existe con otro nombre, **gana lo que hay en la base**: corregí el plan,
> no la base.

### 2.1 La decisión de fondo: dos estados, no uno

[[Esquema de Base de Datos]] deja abierta esta pregunta: *"`mesa` y `pedido` van a necesitar
estados más específicos (libre/ocupada/reservada) — pendiente de decidir si se agregan a
`estado_general` o se separan en catálogos propios"*.

**Decisión de este plan: catálogo propio `estado_mesa`, y `estado_general` se queda para la
baja lógica.** Son dos conceptos ortogonales:

| Concepto | Columna | Valores | Quién lo cambia |
|---|---|---|---|
| ¿La mesa existe en el salón? | `id_estado` → `estado_general` | Activo / Inactivo | Solo admin (baja lógica) |
| ¿Está disponible ahora? | `id_estado_mesa` → `estado_mesa` | Libre / Ocupada / Reservada | Admin y **mesero** |

Meterlos en una sola columna hace imposible expresar "mesa fuera de servicio por reparación,
que además estaba ocupada" y, peor, obliga a que un mesero pueda escribir la misma columna
con la que se da de baja una mesa. **Separarlos es lo que permite que la RLS distinga quién
puede qué.**

> [!note] Esto merece un ADR
> Es una decisión con alternativa razonable y consecuencias para `pedido` (que va a
> necesitar su propio `estado_pedido` por el mismo motivo). El agente que ejecute la Parte A
> escribe **`ADR-007 - Estados operativos en catálogos propios, separados de estado_general`**
> en `45 - Decisiones/`.

### 2.2 Catálogo y columnas

```sql
create table if not exists public.estado_mesa (
    id_estado_mesa int generated always as identity primary key,
    descripcion    varchar(50) not null,
    constraint uq_estado_mesa_descripcion unique (descripcion)
);

insert into public.estado_mesa (descripcion)
select v from (values ('Libre'), ('Ocupada'), ('Reservada')) as t(v)
where not exists (select 1 from public.estado_mesa where descripcion = t.v);

alter table public.mesa
    add column if not exists id_estado_mesa int not null default 1
        references public.estado_mesa(id_estado_mesa),
    add column if not exists actualizado_en timestamptz not null default now();
```

`actualizado_en` **no es opcional**: es lo que hace posible el sync delta de la Fase 2b.
Mismo trigger `tocar_actualizado_en()` que ya usan `platillo` y `categoria`.

Si `mesa` no tiene ya `id_estado` → `estado_general`, agregalo igual (`default 1`), y si no
tiene un único sobre el número de mesa, agregalo: dos mesas "4" en el mismo salón es un error
de captura, no un caso de uso.

### 2.3 Triggers

| Trigger | Qué hace |
|---|---|
| `trg_mesa_actualizado_en` | `BEFORE UPDATE` → `actualizado_en = now()`. **El cliente no manda ese campo** |
| `trg_mesa_no_borrar` | `BEFORE DELETE` → **siempre falla**: *"Las mesas no se borran, se dan de baja. Borrar una rompería el historial de pedidos."* |

`pedido.id_mesa` referencia a `mesa`, así que un `DELETE` rompería pedidos ya cerrados.
Misma **válvula de escape** que los triggers de 2a: si `auth.uid()` es `null` no aplican,
para poder reparar la base desde el SQL Editor.

Y la regla que dejó la Fase 2a: **toda función de trigger creada en `public` queda expuesta
como RPC por PostgREST** → revocale `EXECUTE` a `anon` y `authenticated` en la misma migración.

### 2.4 La función que el proyecto necesita — `cambiar_estado_mesa`

> [!important] Este es el punto donde RLS sola no alcanza
> la matriz de [[Plan Fase 1c - Maqueta Visual por Roles|Permisos]] le da al mesero `MESAS: VER, CAMBIAR_ESTADO` — puede cambiar el estado, pero
> **no** crear, editar ni dar de baja una mesa. Una policy de RLS autoriza o niega **la fila
> entera**: no puede decir "este rol puede escribir solo esta columna".
>
> Con `UPDATE` libre para el mesero, un APK modificado le cambia la capacidad y el número a
> cualquier mesa. Con `UPDATE` negado, el mesero no puede hacer su trabajo.

La salida es una función `SECURITY DEFINER` acotada, que es la única vía de escritura del
mesero:

```sql
create or replace function public.cambiar_estado_mesa(
    p_id_mesa int,
    p_id_estado_mesa int
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if rol_actual() not in ('admin', 'mesero') then
        raise exception 'No tenés permiso para cambiar el estado de una mesa.';
    end if;
    if not exists (select 1 from public.estado_mesa where id_estado_mesa = p_id_estado_mesa) then
        raise exception 'Ese estado de mesa no existe.';
    end if;

    update public.mesa
       set id_estado_mesa = p_id_estado_mesa
     where id_mesa = p_id_mesa
       and id_estado = 1;   -- una mesa dada de baja no cambia de estado operativo

    if not found then
        raise exception 'La mesa no existe o está dada de baja.';
    end if;
end;
$$;

revoke execute on function public.cambiar_estado_mesa(int, int) from anon;
grant  execute on function public.cambiar_estado_mesa(int, int) to authenticated;
```

Puntos que **no** son adorno:

- **`security definer` + `set search_path = public`** — sin fijar el `search_path`, una
  función `SECURITY DEFINER` es un vector de escalada de privilegios clásico.
- **`rol_actual()`** ya existe y exige `activo = true`: un empleado desactivado no pasa.
- **Los mensajes están en español y son para el usuario**, igual que los de los triggers de
  2a. El cliente los muestra tal cual.
- **`revoke … from anon`**: sin eso, cualquiera con la llave publishable del APK cambia
  estados de mesas sin iniciar sesión.

### 2.5 Vista de lectura — **el contrato que la Parte B programa**

```sql
create or replace view public.vista_mesas
with (security_invoker = on) as
select m.id_mesa,
       m.numero_mesa,
       m.capacidad,
       m.ubicacion,
       m.id_estado_mesa,
       em.descripcion as estado_mesa,
       m.id_estado,
       (m.id_estado = 1) as activo,
       m.actualizado_en
  from public.mesa m
  join public.estado_mesa em on em.id_estado_mesa = m.id_estado_mesa;
```

> [!warning] Los nombres `numero_mesa`, `capacidad` y `ubicacion` son una **suposición**
> Salen del uso, no de la bóveda: nadie registró el DDL de `mesa`. El agente de la Parte A
> los ajusta a lo que la tabla realmente tenga **y deja la vista exponiendo exactamente estos
> alias**, para que la Parte B no dependa de cómo se llamen las columnas por dentro.
>
> Si alguna no existe (p. ej. `ubicacion`), **sacala de la vista y del plan** en vez de
> inventar una columna nueva en la tabla.

`security_invoker = on` como en `vista_platillos`: la vista respeta la RLS de quien consulta.

### 2.6 RLS

| Tabla | `SELECT` | `INSERT` / `UPDATE` / `DELETE` |
|---|---|---|
| `mesa` | cualquier rol con sesión activa | **solo `admin`** |
| `estado_mesa` | cualquier rol con sesión activa | nadie desde la app (catálogo fijo) |

El mesero **no** tiene `UPDATE` sobre `mesa`: escribe únicamente por
`cambiar_estado_mesa()`. Esa es toda la razón de que la función exista.

### 2.7 Verificación de la Parte A

Dentro de una transacción **revertida**, simulando cada rol:

| Caso | Esperado |
|---|---|
| Mesero lee `vista_mesas` | ✅ devuelve filas |
| Mesero hace `UPDATE mesa SET capacidad = 99` | 🚫 0 filas (RLS) |
| Mesero llama `cambiar_estado_mesa(1, 2)` | ✅ la mesa queda Ocupada |
| Cocina llama `cambiar_estado_mesa(1, 2)` | 🚫 *"No tenés permiso…"* |
| `cambiar_estado_mesa(1, 99)` | 🚫 *"Ese estado de mesa no existe."* |
| `cambiar_estado_mesa` sobre una mesa dada de baja | 🚫 *"La mesa no existe o está dada de baja."* |
| Admin borra una mesa | 🚫 *"Las mesas no se borran, se dan de baja…"* |
| Admin edita → `actualizado_en` avanza solo | ✅ |
| Insertar dos mesas con el mismo número | 🚫 violación del único |

`get_advisors(security)` → **0 errores**. Confirmar después del `ROLLBACK` que los datos
quedaron intactos.

### 2.8 Datos de prueba

Sembrar unas 8–10 mesas activas con capacidades variadas (2, 4, 6) y las tres en estados
distintos, para que la Parte B tenga contra qué programar sin inventar nada.

---

## 3. PARTE B — Contrato HTTP

| Operación | Verbo y ruta |
|---|---|
| Listar (sync delta) | `GET rest/v1/vista_mesas?select=*&actualizado_en=gt.{marca}&order=actualizado_en.asc&limit=50` |
| Crear | `POST rest/v1/mesa` + `Prefer: return=representation` |
| Editar | `PATCH rest/v1/mesa?id_mesa=eq.{id}` |
| Baja / alta lógica | `PATCH rest/v1/mesa?id_mesa=eq.{id}` con `{"id_estado": 1\|2}` |
| **Cambiar estado operativo** | `POST rest/v1/rpc/cambiar_estado_mesa` con `{"p_id_mesa":…, "p_id_estado_mesa":…}` |

> [!danger] El filtro del `PATCH` es parámetro **obligatorio** de la interfaz Retrofit
> Un `PATCH` sin filtro en PostgREST actualiza **todas** las filas de la tabla. Declaralo
> `@Query(...) String idIgualA` sin valor por defecto, como ya hacen `SupabaseEmpleadoApi` y
> `SupabaseMenuApi`.

---

## 4. Entregables

| # | Entregable | Contenido |
|---|---|---|
| **E1** | `domain` | `model/Mesa.java`, `model/NuevaMesa.java`, `EstadoMesa` (enum con su id), `ValidadorMesa`, `ReglasMesa`, `repository/MesaRepository.java` |
| **E2** | `data` remoto | `SupabaseMesaApi`, `MesaDto`, `CrearMesaDto`, `ActualizarMesaDto`, `CambiarEstadoMesaDto` |
| **E3** | `data` local | `MesaEntity`, `MesaDao`, mapeo, y los tipos de operación nuevos del outbox (`CREAR_MESA`, `ACTUALIZAR_MESA`, `CAMBIAR_ESTADO_MESA`, `CAMBIAR_BAJA_MESA`) |
| **E4** | `SupabaseMesaRepository` | Lecturas por `LiveData` desde Room; escrituras locales + encolado, sobre la base de 2b |
| **E5** | ViewModel | `MesasViewModel`, `EstadoMesas`, Factory. Filtro por estado y búsqueda **en el ViewModel** |
| **E6** | UI | `MesasFragment` (grilla, no lista: un salón se lee mejor en cuadrícula), `MesaAdapter` con color por estado, `FormularioMesaDialog`, layouts, strings |
| **E7** | Pruebas | `ValidadorMesaTest`, `ReglasMesaTest`, `SupabaseMesaRepositoryTest` (con `FakeCall`), `MesasViewModelTest` |

### Detalles de diseño que no son negociables

- **`ReglasMesa`** espeja lo que el servidor impone: quién puede cambiar el estado
  (`Permisos`), que una mesa dada de baja no cambia de estado operativo, y que no se borra.
  Que la app sea **más** estricta que el servidor es seguro; al revés es el problema.
- **El color por estado sale de la paleta** (`values/colors.xml` + `values-night/`), nunca
  hardcodeado. Ver [[Guía de Diseño Visual]].
- **El estado no se comunica solo por color.** Un daltónico ve dos mesas iguales. Va color
  **más** etiqueta de texto. Ver [[Accesibilidad Android]].
- **Cambiar el estado es la acción principal**, no una opción escondida en un ⋮: es lo que
  el mesero hace cincuenta veces por turno. Un toque en la tarjeta abre el selector de
  estado; el ⋮ (solo admin) queda para editar y dar de baja.

---

## 5. Trampas concretas

### 5.1 El filtro que esconde lo que acabás de hacer

Ya pasó en el Menú y **este módulo lo tiene garantizado**: con el filtro "Libre" activo, el
mesero marca una mesa como Ocupada y la mesa desaparece de la pantalla. El servidor guardó,
el usuario cree que falló, y vuelve a tocar.

Aplicá el mismo criterio que `MenuViewModel.descartarFiltroQueEsconde(...)`: **un filtro que
esconde el resultado de la acción recién hecha es un filtro obsoleto**. Ver
[[Sesión 2026-08-01 - Rediseño de la tarjeta de platillo y filtro que escondía lo guardado]].

### 5.2 El RPC no devuelve la fila

`cambiar_estado_mesa` devuelve `void`. Con Room como fuente de verdad eso no es problema —la
escritura local ya ocurrió—, pero **no esperes un cuerpo de respuesta**: el tipo es
`Call<Void>` y un 204 es éxito.

### 5.3 Los errores del RPC sí se le muestran al usuario

Vienen en `{"message": "..."}` de PostgREST y los escribimos nosotros en español. Reusá el
`mensajeDeError(...)` que ya existe en los repositorios — y si lo vas a copiar por tercera
vez, considerá cerrar **P-001** de una vez (`BaseRepository`), que es justo lo que ese ítem
espera desde la Fase 1.

### 5.4 `estado_mesa` es un catálogo: cachealo, no lo pidas siempre

Tres filas que no cambian nunca. Van a Room en la primera sincronización y se leen de ahí.
Pedirlas en cada apertura de pantalla es gastar red en 3G para nada.

---

## 6. Qué NO hacer

| No hagas | Por qué |
|---|---|
| Empezar antes de que 2b esté terminada | Reescritura garantizada (**P-014**) |
| Escribir `mesa.id_estado_mesa` con un `PATCH` directo | Para eso está el RPC; el `PATCH` lo va a rechazar la RLS para el mesero |
| Meter la lógica de pedidos ("¿qué pedido tiene esta mesa?") | Es la Fase 4. Acá la mesa solo tiene estado |
| Agregar estados nuevos al catálogo desde la app | `estado_mesa` es un catálogo fijo; cambiarlo es una migración |
| Borrar una mesa | El servidor lo rechaza y el historial de pedidos depende de ella |

---

## 7. Definición de terminado

- [ ] `./gradlew testDebugUnitTest assembleDebug` → **BUILD SUCCESSFUL**, con más tests que al empezar.
- [ ] `DatosMaqueta.Mesa` y `DatosMaqueta.mesas()` eliminados.
- [ ] Con el avión activado se puede cambiar el estado de una mesa y queda marcado como pendiente.
- [ ] Un mesero no ve el botón de crear/editar **y** el servidor se lo niega si lo intenta.
- [ ] Cero strings, colores o dimens hardcodeados.
- [ ] El [[Gate de Autoverificación]] impreso ítem por ítem, sin ❌.
- [ ] `ADR-007` escrito (si lo hizo la Parte A, enlazarlo; si no, escribirlo).
- [ ] Nota `Módulo Mesas.md` con el formato de [[Módulo Menú]].
- [ ] Nota de sesión, [[Arquitectura Actual]], [[Conocimiento Principal]], [[Esquema de Base de Datos]] y [[Roadmap de Fases]] actualizados.

Fuera del alcance del agente, **lo verifica el usuario**: probar el salón en un dispositivo,
con dos sesiones (admin y mesero) para ver que los permisos se comportan distinto.

---

## Relaciones

- [[Protocolo de Ejecución de un Plan]] — **léelo primero**
- [[Plan Fase 2b - Offline-First con Room y Outbox]] — **prerrequisito**
- [[Plan Fase 3b - CRUD de Clientes]] — el otro catálogo que Pedidos necesita
- [[Módulo Menú]] — el patrón a replicar
- [[Esquema de Base de Datos]] — hay que documentar ahí el DDL real de `mesa`
- [[Plan Fase 1c - Maqueta Visual por Roles]] — la matriz de permisos que la RLS respalda
- [[Guía de Diseño Visual]] · [[Accesibilidad Android]]
- [[Deuda Técnica - Pendientes]] — P-001, P-014
- [[Roadmap de Fases]]
- [[Sesión 2026-08-01 - Rediseño de la tarjeta de platillo y filtro que escondía lo guardado]] — la trampa del filtro

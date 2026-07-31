---
title: Plan Fase 1c — Maqueta visual con permisos por rol
tags:
  - restaurante
  - plan
  - fase1c
  - roles
  - permisos
  - ui
date: 2026-07-31
lifecycle: draft
---

# Plan Fase 1c — Maqueta visual con permisos por rol

> [!important] Cómo se ejecuta
> Un `Entregable` a la vez. Al terminar cada uno se detiene, se revisa, y recién entonces sigue el siguiente.

---

## Objetivo principal

Construir la **maqueta completa de la app** (todas las pantallas, sin funcionalidad real) de modo que **lo que ve cada usuario dependa de su rol**, y que esa misma restricción esté respaldada del lado del servidor con **RLS** — no solo escondiendo botones.

El resultado es una app navegable de punta a punta que sirve para demostrar el sistema de permisos, sin haber escrito todavía un solo CRUD real.

---

## La idea central: una pantalla por módulo, no una pantalla por rol

> [!danger] El error que este plan evita
> Lo intuitivo sería crear `MenuAdminActivity` y `MenuMeseroActivity`. Con 6 módulos × 3 roles serían **18 pantallas** que mantener, y cada cambio de diseño habría que hacerlo 3 veces. Además, agregar un cuarto rol significaría escribir 6 pantallas nuevas.

En vez de eso: **una sola pantalla por módulo**, y los permisos son **datos que la pantalla consulta**, no código duplicado.

```
              ┌─────────────────────────────┐
  rol ──────► │  Permisos.puede(rol, ...)   │ ──────► true / false
              └─────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
  ¿Ve el módulo      ¿Ve el botón        ¿Ve el menú de
   en el drawer?      "Agregar"?          editar/eliminar?
```

La misma `MenuFragment` la usan los tres roles. Lo único que cambia es que el `admin` ve el botón flotante de "Agregar platillo" y el `mesero` no — porque `Permisos.puede("mesero", MENU, CREAR)` devuelve `false`.

**Ventaja concreta:** agregar un rol nuevo (ej. `cajero`) en el futuro es **una línea en la matriz de permisos**, cero pantallas nuevas.

---

## Regla de navegación: una sola superficie

> [!danger] Prohibido: barra inferior + drawer con los mismos destinos
> Material desaconseja explícitamente que convivan una `BottomNavigationView` y un `NavigationDrawer` apuntando a los mismos lugares — el usuario deja de saber cuál es "la" navegación. **Este proyecto usa únicamente el menú hamburguesa.**

Por qué el drawer y no la barra inferior, en este caso concreto:

1. **Material limita la barra inferior a 5 ítems.** El rol `admin` tiene 7 módulos.
2. **La cantidad varía por rol** — 3 (cocina), 5 (mesero), 7 (admin). Una barra inferior con distinta cantidad de íconos según quién entra se ve descuadrada; el drawer lo absorbe sin problema.
3. **Escala a pantallas grandes.** En tablet el drawer se convierte en panel fijo o `NavigationRail`; la barra inferior no tiene equivalente. Con `targetSdk 37` la app **se redimensiona en tablets aunque no se quiera** — ver [[Android 16 y 17 - Cambios de Comportamiento]].

**Descartado deliberadamente:** una barra inferior solo para `mesero` (que usa la app de pie y con una mano) sería más rápida, pero implicaría navegación distinta según el rol — más código y más confusión para quien usa dos roles. Se mantiene el drawer parejo para los tres.

---

## Diseño visual de referencia

> [!success] Diseño aprobado — leído el 2026-07-31
> Proyecto de Claude Design `3f58f5fc-cf82-4509-9b37-978984f85107`, archivo `Restaurant App v2.dc.html`. Contiene las **10 pantallas** (login, recuperación ×2, inicio, menú, pedidos, mesas, clientes, empleados, reportes) con los formularios como modales.
>
> Su lógica de permisos **coincide con la matriz de abajo**, verificada leyendo el código del diseño:
> `canMesasClientes: isAdmin || isMesero` · `showPrepCard: isAdmin || isCocina` · `showVentasCard / showEmpleadosCard / showPlatillosCard: isAdmin` · `o.canEdit: isAdmin || isMesero` · `o.canCancel: isAdmin`.
>
> Sus colores son ahora los oficiales en [[Guía de Diseño Visual]].

---

## Matriz de permisos

Es la fuente única de verdad del cliente. El ejemplo pedido — *"solo admin puede agregar usuarios"* — es la fila `EMPLEADOS`.

| Módulo | admin | mesero | cocina |
|---|---|---|---|
| **INICIO** | ver | ver | ver |
| **MENU** (platillos, categorías) | ver · crear · editar · eliminar | ver | ver |
| **PEDIDOS** | ver · crear · editar · eliminar · cambiar estado | ver · crear · editar | ver · cambiar estado |
| **MESAS** | ver · crear · editar · eliminar | ver · cambiar estado | — |
| **CLIENTES** | ver · crear · editar · eliminar | ver · crear · editar | — |
| **EMPLEADOS** | ver · crear · editar · eliminar | — | — |
| **REPORTES** | ver | — | — |

Lectura de la matriz:
- Un módulo **sin `ver`** desaparece del menú hamburguesa por completo.
- `cocina` solo necesita ver pedidos y marcarlos como listos — no toca dinero, clientes ni empleados.
- `mesero` puede cambiar el estado de una mesa (ocupar/liberar) pero no crear ni borrar mesas.

---

## 🔴 Fuera de alcance del programador — tareas de Supabase

Estas requieren el panel/SQL de Supabase. Sin ellas la maqueta funciona igual (usa datos falsos), pero **la parte de "y además el servidor lo bloquea" no se puede demostrar**.

| # | Tarea |
|---|---|
| **S-1** | Crear la función `public.rol_actual()` que lee el rol del usuario autenticado |
| **S-2** | Crear las policies RLS de las 6 tablas sensibles según la matriz |
| **S-3** | Cargar unas pocas filas de ejemplo (2-3 platillos, 2 mesas, 1 cliente) para poder probar que un `mesero` **sí** lee platillos y **no** lee empleados |

### SQL de referencia (S-1 y S-2)

```sql
-- S-1: función que devuelve el rol del usuario autenticado.
-- SECURITY DEFINER para que pueda leer perfiles sin quedar atrapada en su propia RLS.
-- search_path fijo en '' evita el lint "function_search_path_mutable" y ataques de shadowing.
create or replace function public.rol_actual()
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select rol from public.perfiles
  where id = (select auth.uid()) and activo = true
$$;

-- S-2 (ejemplo — empleados: SOLO admin, ni leer)
create policy "empleados solo admin" on public.empleados
  for all to authenticated
  using (public.rol_actual() = 'admin')
  with check (public.rol_actual() = 'admin');

-- Ejemplo de lectura amplia + escritura restringida (platillo)
create policy "platillo lectura autenticados" on public.platillo
  for select to authenticated using (true);

create policy "platillo escritura solo admin" on public.platillo
  for all to authenticated
  using (public.rol_actual() = 'admin')
  with check (public.rol_actual() = 'admin');
```

> [!success] S-1, S-2 y S-3 aplicados (2026-07-31)
> Se creó `public.rol_actual()` y **34 policies** sobre las 15 tablas, más datos de catálogo de ejemplo. `get_advisors(security)` → **0 errores**.
>
> **Verificado simulando cada rol** contra la base (con `set local role authenticated` + `request.jwt.claims`, dentro de transacciones revertidas):
>
> | Rol | platillos | mesas | clientes | empleados |
> |---|---|---|---|---|
> | admin | 5 | 4 | 0 | **2** |
> | mesero | 5 | 4 | 0 | **0** ← la tabla tiene 2 filas |
> | cocina | 5 | **0** | **0** | **0** |
>
> El mesero ve 0 empleados **aunque la tabla tenga 2 registros**: no es que esté vacía, es que el servidor no se los entrega. Eso es lo que hace demostrable el cierre del guion.
>
> **Datos cargados (S-3):** 4 categorías, 5 platillos, 4 mesas, 2 tipos de pedido. Deliberadamente **no se inventaron personas** — `empleados` conserva solo los 2 registros reales, para que la prueba de bloqueo sea sobre datos auténticos.

> [!bug] Descuido encontrado por el linter y corregido
> La primera migración hacía `revoke execute on function public.rol_actual() from anon`, pero **Postgres concede `EXECUTE` a `PUBLIC` por defecto en toda función nueva, y `anon` es miembro de `PUBLIC`** — revocárselo solo a `anon` no servía de nada. El advisor lo detectó (`anon_security_definer_function_executable`). Se corrigió revocando a `PUBLIC` y concediendo únicamente a `authenticated`. Verificado con `has_function_privilege`: `anon` → `false`, `authenticated` → `true`.
>
> Queda como lección: **revocar un privilegio a un rol no alcanza si `PUBLIC` lo tiene.**

> [!note] Warnings que quedan (aceptados)
> - `authenticated_security_definer_function_executable` sobre `rol_actual()`: **esperado y necesario** — las expresiones de las policies se evalúan con los privilegios de quien consulta, así que `authenticated` debe poder ejecutarla. Solo devuelve el rol del propio llamador, que ya conoce.
> - `pg_graphql_*_table_exposed`: las tablas son *descubribles* en el schema de GraphQL, pero **no legibles** — la RLS es la que manda, como demuestra la tabla de arriba.
> - `auth_leaked_password_protection`: es la tarea **S-4**, requiere plan Pro.

---

## Entregables

### Entregable 1 — Modelo de permisos en `domain`

**Objetivo:** convertir la matriz de arriba en código Java puro, testeable sin Android.

**Archivos a crear:**
```
domain/Modulo.java      — enum: INICIO, MENU, PEDIDOS, MESAS, CLIENTES, EMPLEADOS, REPORTES
domain/Accion.java      — enum: VER, CREAR, EDITAR, ELIMINAR, CAMBIAR_ESTADO
domain/Permisos.java    — puede(rol, modulo, accion) + accionesDe(rol, modulo)
app/src/test/java/.../PermisosTest.java
```

**Archivo a modificar:**
- `domain/VisibilidadMenu.java` → dejar de tener su propia tabla y **derivarse de `Permisos`**: un módulo es visible si `Permisos.puede(rol, modulo, Accion.VER)`. Una sola fuente de verdad, no dos que puedan desincronizarse.

**Reglas:**
- Rol desconocido o `null` → solo `INICIO` con `VER`. Se niega por defecto, nunca se concede.
- Cero dependencias de `android.*` — es la pieza testeable del plan.

**Criterio de aceptación:** `./gradlew testDebugUnitTest` verde, con un test por celda relevante de la matriz (incluyendo los casos negativos: mesero no crea empleados, cocina no ve mesas).

**Estado:** `[x] Completado` (2026-07-31) — creados `Modulo`, `Accion`, `Permisos` y `PermisosTest` (15 tests). `VisibilidadMenu` quedó como fachada que delega en `Permisos`; su enum `Item` se reemplazó por `Modulo` (compartido), lo que obligó a un ajuste menor en `MainActivity` (el `EnumMap` de IDs y `filtrarMenu`). Build y 33 tests en verde.

> [!warning] Un test tuvo que cambiar — la premisa original no se sostuvo
> El plan decía que los 5 tests de `VisibilidadMenuTest` debían pasar **sin modificarse**. No fue posible: el test `cocinaSoloVeInicioYPedidos` afirmaba `assertFalse(esVisible(COCINA, MENU))`, mientras que **la matriz de este plan y el diseño aprobado coinciden en que cocina sí ve Menú** (para consultar qué lleva un platillo).
>
> El código de la Fase 1b era el desactualizado, no la matriz. Se renombró el test a `cocinaVeInicioPedidosYMenu` y se invirtió esa aserción. Queda como recordatorio de que "los tests existentes son la red de seguridad" solo vale cuando la regla de negocio no cambió — si cambió, el test es justamente lo que hay que actualizar, y a conciencia.

---

### Entregable 2 — Migrar `MainActivity` a Fragments

**Objetivo:** que el área de contenido pueda intercambiar módulos **sin abrir Activities nuevas**.

**Archivos a crear:**
```
ui/principal/InicioFragment.java + fragment_inicio.xml
```

**Archivos a modificar:**
- `res/layout/activity_main.xml` → reemplazar el `TextView` de placeholder por un `<androidx.fragment.app.FragmentContainerView>`
- `MainActivity.java` → `alSeleccionarItem()` hace un `FragmentTransaction` en vez de cambiar un texto
- `app/build.gradle.kts` → `androidx.fragment` si no viene ya transitivo

**Por qué Fragments y no más Activities:** es literalmente el pedido ("que reutilice la misma pantalla"), y de paso avanza sobre **P-015** (el estándar pide single-Activity). Se usa `FragmentTransaction` manual, **no** Navigation Component — este plan es sobre permisos, no sobre navegación; el nav_graph queda para su propio trabajo.

**Criterio de aceptación:** el drawer cambia de módulo sin crear Activities; el botón "atrás" y la rotación de pantalla no rompen nada.

**Estado:** `[x] Completado` (2026-07-31). Creados `InicioFragment` (saludo según la hora, con el nombre de `SesionActual`) y `PlaceholderFragment` (genérico y parametrizado — uno solo para todos los módulos pendientes, en vez de siete vacíos). En `activity_main.xml` el placeholder fijo se reemplazó por un `FragmentContainerView`. Build y 33 tests en verde.

Decisiones tomadas al implementar:
- **Sin back stack** en los cambios de módulo: son destinos de primer nivel, no una pila. "Atrás" con el menú abierto lo cierra (vía `OnBackPressedDispatcher`, no el `onBackPressed()` deprecado); con el menú cerrado sale de la app.
- **La rotación no recrea el Fragment**: el `replace` solo corre cuando `savedInstanceState == null`; el `FragmentManager` restaura el resto. El módulo activo se guarda en `onSaveInstanceState` para restaurar título e ítem marcado.

> [!bug] Bug encontrado y corregido en el camino
> La `MaterialToolbar` tenía `layout_height="?attr/actionBarSize"` (altura **fija**) y `aplicarInsets()` le agregaba el inset superior como padding. Sobre una altura fija, ese padding **recorta** el contenido: la barra se veía terracota pero **sin ícono de hamburguesa ni título**. Se cambió a `wrap_content` + `minHeight="?attr/actionBarSize"`.
>
> Es el mismo error de fondo que **P-004**, en otro lugar. Regla general: **ninguna vista que reciba padding de insets puede tener altura fija.**

---

### Entregable 3 — Componente reusable de acciones por permiso

**Objetivo:** que ningún Fragment repita `if (rol.equals("admin"))`. Ese `if` disperso es exactamente la deuda que este plan quiere evitar.

**Archivos a crear:**
```
ui/permisos/VistaPorPermiso.java   — helper: aplicar(View, rol, modulo, accion)
ui/maqueta/DatosMaqueta.java       — listas falsas, en un solo archivo, para borrar de un saque en Fase 2
```

**Cómo se usa:**
```java
VistaPorPermiso.aplicar(fabAgregar, sesion.getRol(), Modulo.EMPLEADOS, Accion.CREAR);
// si no tiene el permiso → View.GONE, sin ifs regados por la app
```

**Regla clave:** `DatosMaqueta` vive en `ui/`, **nunca en `domain/`**. Son datos de mentira para la demo; el dominio no debe enterarse de que existieron.

**Criterio de aceptación:** compila; ningún Fragment compara strings de rol a mano.

**Estado:** `[x] Completado` (2026-07-31). Build y 33 tests en verde.

- **`VistaPorPermiso`** expone `puede(modulo, accion)`, `aplicar(View, …)` y `aplicar(MenuItem, …)`. Lee el rol de `SesionActual`, con una sobrecarga que lo recibe explícito — necesaria para el selector de rol de debug (E7). Usa `GONE` y no `INVISIBLE`: un control que el rol no tiene no debe dejar un hueco que delate su existencia.
- **`DatosMaqueta`** en un solo archivo, con modelos anidados (`Platillo`, `Pedido`, `Mesa`, `Cliente`, `Empleado`…), listas de ejemplo con nombres y precios hondureños, y los contadores para las tarjetas del E6.
- **Los estados (`EstadoPedido`, `EstadoMesa`) quedaron en la maqueta, no en `domain`.** Es deliberado: el catálogo real todavía no se decidió en la base — `estado_general` solo tiene Activo/Inactivo y falta resolver si mesas y pedidos usan esa tabla o catálogos propios (ver [[Esquema de Base de Datos]]). Definirlos en el dominio ahora sería congelar una decisión abierta.

**Ajuste extra:** se sincronizó `values/colors.xml` y `values-night/colors.xml` con la paleta real del diseño (seguían con los valores tentativos `#9C4221` de la primera versión de la [[Guía de Diseño Visual]]) y se agregaron los 5 colores de estado, en ambos temas.

---

### Entregable 4 — Módulos de operación: Menú, Pedidos, Mesas

**Archivos a crear:**
```
ui/menu/MenuFragment.java        + fragment_menu.xml     + item_platillo.xml
ui/pedidos/PedidosFragment.java  + fragment_pedidos.xml  + item_pedido.xml
ui/mesas/MesasFragment.java      + fragment_mesas.xml    + item_mesa.xml
+ sus adapters de RecyclerView
```

**Contenido de cada uno:** `RecyclerView` con 4-6 filas de `DatosMaqueta`, y las acciones filtradas por permiso:

| Fragment | Acción visible solo si… |
|---|---|
| Menú | FAB "Agregar platillo" → `CREAR`; menú ⋮ editar/eliminar → `EDITAR`/`ELIMINAR` |
| Pedidos | FAB "Nuevo pedido" → `CREAR`; chip de estado tocable → `CAMBIAR_ESTADO` |
| Mesas | Grilla de mesas con color por estado; tocar para ocupar/liberar → `CAMBIAR_ESTADO` |

Estados con **color + texto**, nunca color solo (ver [[Guía de Diseño Visual]] y [[Accesibilidad Android]]).

**Criterio de aceptación:** entrando como `cocina`, Mesas no aparece en el drawer, y en Pedidos no hay FAB pero sí se puede tocar el chip de estado.

**Estado:** `[x] Completado` (2026-07-31). Build y 33 tests en verde. 3 Fragments + 3 adapters + 6 layouts + 2 iconos vectoriales + `menu_acciones.xml`.

Decisiones tomadas al implementar:
- **`ListAdapter` + `DiffUtil`** en los tres, no `RecyclerView.Adapter` pelado — es lo que pide el estándar y prepara el terreno para las listas reales de la Fase 2. Obligó a volver inmutables los estados de `Pedido` y `Mesa` (con `conEstado()` que devuelve una copia): sin eso `DiffUtil` no detecta el cambio porque compara la misma instancia consigo misma.
- **Los cambios de estado sí funcionan** (en memoria): tocar el chip de un pedido lo avanza en el flujo de cocina, tocar una mesa cicla libre → ocupada → reservada. Es lo que hace la demo convincente sin haber conectado la base. El resto de acciones muestra un `Snackbar` que aclara que es una maqueta.
- **El botón ⋮ desaparece entero** si el rol no tiene ni editar ni eliminar — no se muestra un menú vacío.
- **En Pedidos la opción se llama "Cancelar pedido", no "Eliminar"**: cancelar deja rastro contable, borrar no. Es la misma distinción que sostiene que el mesero pueda editar pero no cancelar.
- **En Mesas, el toque lo recibe la tarjeta entera**, no el chip — blanco mucho más cómodo que un chip de 60 dp.
- **Columnas de la grilla desde `@integer/columnas_mesas`**, con variante `values-sw600dp` (2 en teléfono, 4 en tablet). Es el hábito barato que evita reescribir pantallas cuando la app se redimensione en tablets, que con `targetSdk 37` pasa aunque no se quiera.

---

### Entregable 5 — Módulos de administración: Clientes, Empleados, Reportes

**Archivos a crear:**
```
ui/clientes/ClientesFragment.java    + layouts
ui/empleados/EmpleadosFragment.java  + layouts
ui/reportes/ReportesFragment.java    + layout
```

- **Clientes:** lista + buscador por identidad (visual, sin lógica) — coherente con [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]].
- **Empleados:** **el caso estrella de la demo.** Solo `admin` lo ve; los otros roles ni siquiera lo tienen en el drawer.
- **Reportes:** tarjetas con números de ejemplo (ventas del día, platillo más pedido). Solo `admin`.

**Criterio de aceptación:** logueado como `mesero`, Empleados y Reportes **no existen** en el menú; como `admin` aparecen los 7 módulos.

**Estado:** `[x] Completado` (2026-07-31). Build y 33 tests en verde. 3 Fragments + 2 adapters + 6 layouts + `menu_empleado.xml`.

Decisiones tomadas al implementar:
- **El buscador de Clientes acepta nombre o identidad en un solo campo.** Es como se usa en la práctica: al tomar un pedido se pregunta la identidad para reusar el cliente si ya existe (ADR-006). Dos campos separados sería fricción sin ganancia.
- **`EmpleadoAdapter` no filtra por permiso** — el módulo entero ya es exclusivo de admin, filtrar dentro sería redundante. Pero el **FAB sí se filtra**: es defensa en profundidad barata, y evita que el día que exista un rol con lectura de empleados aparezca sin querer el botón de crear.
- **La opción activar/desactivar alterna su título** según el estado del empleado, en vez de mostrar las dos opciones siempre.
- **Reportes usa `NestedScrollView` con filas infladas, no `RecyclerView`.** Son dos listas cortas y acotadas (5 y 2 filas) dentro de una pantalla que ya scrollea; anidar scroll dentro de scroll da más problemas de los que resuelve. La regla del estándar apunta a listas **abiertas**, que no es el caso.

**Limpieza:** con los 7 módulos ya construidos, se eliminaron `PlaceholderFragment`, su layout y sus dos strings — dejarlos habría sido código muerto.

> [!warning] Fallback corregido — falla del lado seguro
> La primera versión de `crearFragment()` devolvía `ReportesFragment` como caso por defecto. Eso significa que un ítem de menú nuevo mal conectado habría mostrado **contenido de admin a cualquier rol**. Se cambió para que el fallback sea `InicioFragment` — la pantalla que todos pueden ver. Ante la duda, el sistema debe conceder lo mínimo, no lo último de la cadena de `if`.

---

### Entregable 6 — Dashboard de Inicio con tarjetas de estado

**Objetivo:** que la primera pantalla refleje el rol **y aporte información que el menú no puede dar**.

**Archivo a modificar:** `ui/principal/InicioFragment.java`

> [!warning] Corrección sobre la versión anterior de este plan
> La primera redacción pedía "tarjetas de acceso rápido". Eso es **navegación duplicada**: botones que llevan exactamente adonde ya lleva el drawer. Detectado al revisar una maqueta generada que tenía los mismos destinos en tres lugares a la vez (tarjetas + barra inferior + drawer).

Las tarjetas muestran **estado, no puertas**. Siguen siendo tocables y llevan al módulo, pero justifican existir porque dicen algo que el drawer no dice:

| ❌ Redundante | ✅ Aporta información |
|---|---|
| Tarjeta "Pedidos" | **"4 pedidos pendientes"** |
| Tarjeta "Mesas" | **"6 de 10 mesas ocupadas"** |
| Tarjeta "Menú" | **"32 platillos activos"** |
| Tarjeta "Clientes" | **"128 clientes registrados"** |

Filtradas por rol, generadas desde `Permisos` — no una lista fija con `setVisibility`:

- **cocina** → 1 tarjeta: "4 pedidos en preparación"
- **mesero** → pedidos pendientes · mesas ocupadas · clientes registrados
- **admin** → las anteriores + platillos activos + ventas del día + empleados activos

Los números salen de `DatosMaqueta` (fijos, es una maqueta). Saludo con el nombre real de `SesionActual`.

**Criterio de aceptación:** la cantidad de tarjetas cambia sola al cambiar el rol, sin tocar el layout; ninguna tarjeta es un botón de navegación pelado sin dato.

**Estado:** `[x] Completado` (2026-07-31). Build y 33 tests en verde. Conteo real: **admin 7 tarjetas, mesero 3, cocina 1**.

> [!tip] El hallazgo del entregable: el permiso correcto ya estaba en la matriz
> El problema era cómo decidir qué tarjeta ve cada rol sin escribir excepciones a mano. La respuesta salió de preguntarse **a quién le sirve cada dato**, y resultó que cada respuesta ya es una celda de la matriz:
>
> | Tarjeta | Permiso que la habilita | Quién la ve |
> |---|---|---|
> | Pedidos pendientes | `PEDIDOS · CREAR` | admin, mesero — le importa a quien toma pedidos |
> | En preparación | `PEDIDOS · CAMBIAR_ESTADO` | admin, cocina — le importa a quien cocina |
> | Mesas ocupadas | `MESAS · VER` | admin, mesero |
> | Clientes registrados | `CLIENTES · VER` | admin, mesero |
> | Platillos activos | `MENU · CREAR` | admin — le importa a quien administra el catálogo |
> | Ventas de hoy | `REPORTES · VER` | admin |
> | Empleados activos | `EMPLEADOS · VER` | admin |
>
> Cero `if` por rol: la lista se filtra con `VistaPorPermiso.puede(...)`. Un rol nuevo solo toca la matriz.

Otras decisiones:
- **`GridLayout` y no `RecyclerView`**: la grilla vive dentro de un `NestedScrollView` y anidar scroll dentro de scroll trae más problemas de los que resuelve para un puñado de tarjetas de cantidad conocida. Mismo criterio que en Reportes.
- **Columnas desde `@integer/columnas_tarjetas_inicio`** (2 en teléfono, 3 en tablet).
- **`NavegacionModulos`**: interfaz pequeña para que el Fragment pida abrir un módulo sin conocer a `MainActivity`. `abrirModulo()` quedó como **punto único** de cambio de módulo — lo usan el menú lateral y las tarjetas, así título, ítem marcado y contenido no se desincronizan.

---

### Entregable 7 *(opcional, solo debug)* — Selector de rol para la demostración

**Objetivo:** poder mostrar los tres roles en vivo sin cerrar sesión y volver a entrar tres veces.

**Archivo a crear:** `ui/debug/SelectorRolDebug.java`

Un menú en la toolbar que cambia el rol de `SesionActual` en memoria y refresca el drawer.

> [!danger] Condición no negociable
> Va envuelto en `if (BuildConfig.DEBUG)`. En una build de release no debe existir. Y aun si alguien lo forzara: **cambiar el rol en el cliente no da acceso a nada** — las policies RLS del servidor siguen negando, porque leen el rol de la base, no del APK. Eso es precisamente lo que hace que la demo sea honesta.

**Estado:** `[x] Completado` (2026-07-31). Compila en **debug y release**; 33 tests en verde.

- `SelectorRolDebug.estaDisponible()` devuelve `BuildConfig.DEBUG`; `MainActivity.onCreateOptionsMenu()` **no infla el menú** si es falso, y `mostrar()` corta al entrar por si alguien lo llamara igual.
- Se agregó `Sesion.conRol()` — copia inmutable con otro rol, en vez de volver mutable la entidad.
- El diálogo **muestra el aviso** *"Solo cambia lo que se muestra. El servidor sigue aplicando RLS con tu rol real."* La honestidad va en la pantalla, no solo en la documentación.

> [!warning] El detalle que casi se escapa: quedarse parado en un módulo prohibido
> Cambiar el rol estando en **Empleados** dejaría a "cocina" mirando datos de empleados — exactamente el agujero que este sistema quiere evitar. `cambiarRolDebug()` comprueba si el rol nuevo puede ver el módulo actual y, si no, **redirige a Inicio**. Si puede verlo, igual recrea el Fragment para que vuelva a evaluar los permisos de sus botones (si no, un mesero recién cambiado seguiría viendo el FAB de "Agregar platillo").

> [!note] Limitación conocida del gate
> R8 todavía está desactivado (**P-008**), así que en release el código del selector sigue presente en el APK aunque sea inalcanzable. Al activar R8 la rama muerta se elimina. No es un riesgo de seguridad — cambiar el rol del cliente no otorga acceso — pero conviene saberlo.

---

## Guion de demostración

1. Entrar como **admin** → el drawer muestra los 7 módulos; en Empleados hay botón "Agregar".
2. Cambiar a **mesero** → Empleados y Reportes **desaparecen** del menú; en Menú ya no hay FAB de agregar.
3. Cambiar a **cocina** → solo quedan Inicio y Pedidos; el chip de estado sigue siendo tocable.
4. **El cierre fuerte:** desde una terminal, con el token real de un mesero, pedir la tabla de empleados por PostgREST:
   ```bash
   curl "https://mxarlisuueovxvttytcm.supabase.co/rest/v1/empleados?select=*" \
     -H "apikey: <anon key>" -H "Authorization: Bearer <token del mesero>"
   ```
   Devuelve vacío o error — **la restricción no está en la app, está en la base de datos.** Si alguien pregunta *"¿y si modifican el APK?"*, esta es la respuesta.

---

## Resumen de entregables

| # | Entregable | Depende de | ¿Supabase? | Estado |
|---|---|---|---|---|
| 1 | Modelo de permisos + tests | — | ❌ | ✅ |
| 2 | Migrar a Fragments | — | ❌ | ✅ |
| 3 | Helper de acciones + datos de maqueta | E1, E2 | ❌ | ✅ |
| 4 | Menú, Pedidos, Mesas | E3 | ❌ | ✅ |
| 5 | Clientes, Empleados, Reportes | E3 | ❌ | ✅ |
| 6 | Dashboard adaptativo | E1, E3 | ❌ | ✅ |
| 7 | Selector de rol (debug) | E1 | ❌ | ✅ |
| — | **Demostrar el bloqueo real por RLS** | — | ✅ **S-1, S-2, S-3** | ✅ |

**La Fase 1c está completa** (2026-07-31): los 7 entregables de código **y** las policies RLS del servidor. El proyecto pasó de 17 a 33 tests y de 2 pantallas a 10.

Queda pendiente solo **S-4** (Leaked Password Protection, requiere plan Pro) — opcional desde el principio.

---

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Confundir maqueta con app real | `DatosMaqueta` en un solo archivo de `ui/`, con nombre evidente, para borrarlo entero en Fase 2 |
| Creer que ocultar botones es seguridad | El guion de demo termina con la prueba por `curl`; documentado en cada entregable |
| Duplicar la matriz de permisos en cliente y servidor | Es inevitable tenerla en los dos lados (uno para UX, otro para seguridad). Se mitiga documentándola **una sola vez acá** y derivando ambas de esta tabla |
| Que el refactor de `VisibilidadMenu` rompa el drawer | Los 5 tests existentes de `VisibilidadMenuTest` deben pasar sin modificarse |
| Fragments + rotación de pantalla | Criterio de aceptación explícito en E2 |

---

## Relaciones

- [[Plan Fase 1b - Recuperación de Contraseña y Roles]]
- [[Guía de Diseño Visual]]
- [[Esquema de Base de Datos]]
- [[Seguridad y Privacidad Android]]
- [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]]
- [[Deuda Técnica - Pendientes]] — avanza sobre P-015
- [[Roadmap de Fases]]
- [[Accesibilidad Android]]

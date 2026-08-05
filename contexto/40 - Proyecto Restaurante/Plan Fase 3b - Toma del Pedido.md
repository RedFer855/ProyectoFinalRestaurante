---
title: Plan Fase 3b — Toma del Pedido
tags:
  - restaurante
  - plan
  - fase3
  - pedidos
  - offline-first
date: 2026-08-05
lifecycle: draft
---

# Plan Fase 3b — Toma del Pedido

> [!success] Parte A cerrada y verificada — 2026-08-05
> Las 3 migraciones (`fase3b_pedido_clave_idempotencia`, `fase3b_p025_clock_timestamp`,
> `fase3b_rpc_crear_pedido`, `fase3b_cerrar_insert_directo_e_indices`) están aplicadas.
> De las 13 pruebas de §5.7: **12 verificadas** con transacciones `BEGIN…ROLLBACK` (sin tocar
> datos reales); **A12** (transaccionalidad de `realtime.send`) se resolvió por **inspección
> de código** en vez de una prueba de dos sesiones — `pg_get_functiondef` muestra que es un
> `INSERT` plano en `realtime.messages`, así que corre dentro de la misma transacción del
> trigger y solo es visible tras el commit. `get_advisors(security)` → 0 errores.
> Cierra **P-025** y **P-026**, abre **P-030**. Escritos **ADR-009, ADR-010 y ADR-011**.
>
> **Parte B (Android) sigue pendiente** — no se tocó código de la app en este pase.

> [!danger] Leé primero [[Protocolo de Ejecución de un Plan]]
> Contrato completo: división Parte A / Parte B, orden de lectura, reglas de oro y qué
> significa "terminado". **No es opcional.**

> [!warning] Depende de la Fase 3 completa — no se puede empezar antes
> `PedidoEntity`, `SincronizadorPedidos`, `PedidoRemoto`, `vista_pedidos`, la partición
> `Modulo.PEDIDOS` del outbox y `NotificacionEntity` los crea
> [[Plan Fase 3 - Pedidos en Tiempo Real]]. Esta fase los **extiende**, no los inventa.
> Room entra en **v5** y sale en **v6**.

---

## 1. El encargo

La Fase 3 entrega el **tablero**: ver pedidos, filtrarlos, avanzarles el estado. Esta fase
entrega lo que falta para que haya pedidos que ver: **tomarlos desde la app**.

### 1.1 Alcance

**Entra**

| Qué | Por qué |
|---|---|
| Alta de pedido: carrito, selección de platillos, cantidades, tipo de pedido, mesa y cliente **opcionales** | Es el encargo |
| RPC transaccional `crear_pedido(jsonb)` con **clave de idempotencia** | Es la única forma de que "cabecera + N líneas" sobreviva a un outbox que reintenta (§2) |
| Lectura del detalle de un pedido existente (`DetallePedidoHoja`) | Sin esto el pedido es una caja negra: se ve el total, no qué se pidió. Cuesta ~15% del alta |
| Cierre de **P-025** y **P-026** | Son las dos precondiciones que [[Plan Fase 3 - Pedidos en Tiempo Real]] §8 registró explícitamente |
| Índices faltantes en `detalle_pedido` | No existen hoy; los necesitan `vista_pedidos` y la Fase 3c |

**No entra**

| Qué | Por qué |
|---|---|
| **Complementos** | La tabla tiene 0 filas y no hay CRUD ni código Android. Un selector sobre un catálogo vacío es UI que no hace nada, con tests que prueban que no hace nada. Ver §7 |
| **Editar un pedido ya tomado** | Es un *diff* de dos listas contra una tabla sin `actualizado_en` y sin canal de `UPDATE` en RLS: un segundo problema del tamaño del primero. Acá `EDITAR` se materializa como **cancelar y rehacer** (el admin cancela con el RPC de la Fase 3). Se registra como deuda |
| Pago, cuenta, propina, impresión de comanda | No se pidieron y cada uno arrastra al menos una tabla |
| Notas por línea ("sin cebolla") | `detalle_pedido` no tiene columna. Es migración de servidor + Room + UI por algo que nadie pidió |

> [!note] El contrato del RPC sí deja lugar a los complementos
> `p_payload.lineas[].complementos[]` existe **desde el día uno** y hoy itera vacío.
> Cambiarle la forma al payload más adelante, con la clave de idempotencia ya en uso y
> pedidos en vuelo, sería una migración. Dejarlo listo cuesta cero.

### 1.2 Historias

| # | Historia | Rol |
|---|---|---|
| 1 | Tomar un pedido eligiendo platillos y cantidades | admin, mesero |
| 2 | Asignarle una mesa | admin, mesero |
| 3 | Asignarle un cliente (o ninguno) | admin, mesero |
| 4 | Elegir el tipo (En mesa / Para llevar) | admin, mesero |
| 5 | Tomar un pedido **sin conexión** y que suba solo al volver la red | admin, mesero |
| 6 | Ver el detalle de un pedido del tablero | los tres |

La matriz de permisos **ya existe**: `PEDIDOS: {CREAR, EDITAR}` para mesero, todo para admin.
No se toca `domain/Permisos`; se consume.

---

## 2. La decisión central: la escritura multi-tabla tiene que ser atómica

Un pedido es **una cabecera y N líneas**. Es la primera escritura multi-tabla del proyecto, y
el outbox está diseñado para operaciones de una sola fila. Tres opciones:

### 2.1 Comparación

**A — N operaciones de outbox** (`CREAR_PEDIDO` + un `CREAR_DETALLE` por línea).

❌ El drenado **no es atómico**. Si el proceso muere entre la cabecera y la línea 3, el
servidor queda con un pedido de 2 líneas que **cocina ya está viendo en el tablero** — el
trigger `FOR EACH STATEMENT` emitió el broadcast al insertar la cabecera. Y no hay reparación
posible: `detalle_pedido` no tiene `actualizado_en`, así que el delta no puede detectar la
diferencia. Además rompe `Outbox.deFila(idLocal)`: un carrito de 8 líneas son 9 filas en la
cola, y la partición `(modulo, id_local)` deja de identificar una operación de negocio.

**B — 1 operación de outbox, N llamadas HTTP al drenar.**

❌ La cola queda coherente, pero el problema no se movió: sigue sin ser atómico en el cable, y
agrega uno peor. **`POST` no es idempotente.** Un timeout después de que el servidor insertó
la cabecera pero antes de que llegue la respuesta deja una cabecera huérfana *y* el reintento
crea otra. `ClasificadorDeError` marca el timeout como transitorio (correcto) y el outbox
reintenta 3 veces: **hasta 3 pedidos duplicados en la cocina** por un pedido real.

**C — 1 operación de outbox, 1 RPC `crear_pedido(jsonb)` idempotente. ✅**

El payload lleva el carrito entero, el servidor hace todo en una transacción, y una **clave de
idempotencia generada por el dispositivo** convierte el reintento en un no-op que devuelve el
mismo `id_pedido`.

### 2.2 Por qué C

Es la única de las tres donde **ninguna falla parcial es representable**: o el pedido existe
completo, o no existe. Las otras dos exigen escribir código de reparación para estados que el
diseño puede simplemente no permitir.

Y hay un beneficio que no es evidente: la clave de idempotencia es lo que permite que el
outbox reintente **con confianza**. Hoy los cuatro sincronizadores conviven con `POST` no
idempotentes (`crearCliente`, `crearMesa`, `crearPlatillo`) y el riesgo se tolera porque un
platillo duplicado es un fastidio visible que el admin borra. **Un pedido duplicado es dinero
y comida.** Esta fase introduce el patrón; aplicarlo a los otros tres se registra como deuda.

### 2.3 El precio lo sella el servidor

`crear_pedido` **lee el precio de `platillo`** al insertar, nunca del payload. Un APK
modificado que mande `precio: 0` no debe poder crear un pedido gratis.

El cliente sí necesita un precio para mostrar el total del carrito sin red: usa el de su caché
de `platillos` y lo trata como **estimación**. Cuando el delta devuelve la fila de
`vista_pedidos`, el `total` sellado por el servidor la reemplaza. Si difieren (cambió el precio
entre que se tomó el pedido y que subió), se emite una notificación `PRECIO_AJUSTADO`.
→ **ADR-010**.

### 2.4 La fecha la manda el cliente, acotada por el servidor

Un pedido tomado sin red a las 19:00 y subido a las 21:00 debe conservar las **19:00**: el FIFO
(R7 de la Fase 3) y los reportes de la 3c dependen de eso. Pero el reloj del dispositivo no es
confiable. El RPC acepta `fecha` del payload y la **acota** a `[now() - 24h, now()]`; fuera de
ese rango usa `now()`.

---

## 3. P-025 — se cierra acá, y hay que entender qué cierra

### 3.1 Por qué acá sí muerde

`tocar_actualizado_en()` usa `now()`, que en Postgres es la hora de **inicio de la
transacción**. La marca de agua del cliente sale de los datos recibidos, no de su reloj, así
que la pérdida no necesita nada raro: necesita que **otra** transacción confirme después de
que la nuestra tomó su `now()`. Con `crear_pedido` multi-sentencia y varios meseros a la vez,
eso es el caso normal:

```
T0.00  txn A (mesa 4)  BEGIN            → pedido.actualizado_en = T0.00
T0.05  txn B (mesa 7)  BEGIN…COMMIT     → visible; el cliente sincroniza y guarda marca = T0.05
T0.12  txn A COMMIT                     → la fila con T0.00 recién ahora se vuelve visible
T0.20  el cliente pide  > T0.05         → el pedido de la mesa 4 NO entra. Nunca.
```

### 3.2 El arreglo tiene dos partes, y las dos van en la misma migración

```sql
create or replace function public.tocar_actualizado_en()
returns trigger language plpgsql as $$
begin
    -- P-025: clock_timestamp() es la hora del UPDATE; now() es la del BEGIN.
    new.actualizado_en := clock_timestamp();
    return new;
end; $$;

alter table public.pedido    alter column actualizado_en set default clock_timestamp();
alter table public.mesa      alter column actualizado_en set default clock_timestamp();
alter table public.clientes  alter column actualizado_en set default clock_timestamp();
alter table public.platillo  alter column actualizado_en set default clock_timestamp();
alter table public.categoria alter column actualizado_en set default clock_timestamp();
```

> [!danger] El `default` es la mitad que se pasa por alto
> El trigger es `BEFORE UPDATE`. **Un pedido nuevo es un `INSERT`** y toma el `default now()`.
> Arreglar solo el trigger deja sin arreglar exactamente el caso que motiva esta fase.

Las otras cuatro tablas entran acá porque es la misma línea y la misma migración; dejar
`pedido` arreglado y el resto no es el tipo de asimetría que después nadie recuerda.

**`pedido.fecha` se queda con `now()`**: es hora de negocio (cuándo entró el pedido), no un
cursor de sync. La distinción se documenta para que nadie las "unifique" más adelante.

### 3.3 Qué NO arregla `clock_timestamp()`

Reduce la ventana de "toda la transacción" a "desde la última escritura de esa fila hasta el
commit". Para `crear_pedido` (sub-100 ms) es despreciable, pero **la ventana sigue
existiendo**. La solución sólida es un contador monótono, que obliga a cambiar el tipo de la
marca de agua en las 5 tablas y los 5 sincronizadores — desproporcionado para esta fase.

Mitigación barata que sí entra acá: **solapamiento de la marca de agua** en
`SincronizadorPedidos` — pedir `actualizado_en > marca − 2s` en vez de `> marca`. Es seguro
porque aplicar cada fila ya es idempotente (upsert por `id_servidor` + LWW). Cuesta re-bajar
unas pocas filas por pasada y cierra la ventana práctica.

Se abre **P-030**: *"el cursor del delta es un reloj; el solapamiento es una mitigación, no una
prueba"*. Candidato natural para resolverlo: el pase de deuda P1/P2, que ya toca varias tablas.

> [!warning] Qué cambia `clock_timestamp()` en el resto del sistema
> Dos filas de la misma transacción dejan de compartir timestamp. **No rompe el orden** — el
> delta ya ordena por `(actualizado_en, id_X)` y pagina por `offset` desde que se cerró P-029.
> De hecho *reduce* la clase de bug de P-029: menos empates exactos, menos filas en la
> frontera de la página. Y `clock_timestamp()` es `volatile`: no se puede usar en índices ni
> en columnas generadas — acá solo va en el trigger y en el `default`, así que no aplica.

---

## 4. P-026 — se disuelve, no se resuelve

### 4.1 Tres hechos que borran el problema

1. **`pedido.id_cliente` es NULL-able**, y por [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]] el cliente no tiene cuenta: se le capturan datos *si se quiere*. Un pedido "En mesa" normalmente no lleva cliente.
2. **El módulo Clientes ya es offline-first** (`CREAR_CLIENTE` en el outbox, `ClienteEntity.idServidor`): existe un camino completo para dar de alta un cliente sin red.
3. **`buscar_o_crear_cliente` es el único RPC que no puede ser offline** — y es el único que Pedidos no necesita usar.

> **Pedidos no consume `buscar_o_crear_cliente`.** El selector de cliente del carrito reutiliza
> la lista y el alta del módulo Clientes. El RPC queda sin consumidor y **se marca deprecado**.

Eso convierte P-026 de "problema de diseño abierto" a **"no aplica"** — la mejor clase de
resolución: la que borra el caso en vez de manejarlo.

### 4.2 Lo que sí hay que diseñar: resolver ids locales al drenar

Queda el caso legítimo: un cliente creado sin red, con `idServidor == null`, referenciado por
un pedido. El payload guarda **`cliente_id_local`** en vez de `id_cliente`, y
`SincronizadorPedidos` resuelve al drenar leyendo `clienteDao.porIdLocal(...)`.

El orden no necesita infraestructura nueva: `SyncWorker` recorre la lista de `Sincronizador`
**en orden**, así que en `SyncApplication.FactoryDeSync` va `SincronizadorClientes` **antes**
que `SincronizadorPedidos`. Una línea — y un test que la fija, para que nadie reordene la lista
sin enterarse.

**Política de degradación:**

| Caso | Qué hace `SincronizadorPedidos` |
|---|---|
| `cliente_id_local` todavía sin `idServidor` (el CREAR no drenó aún) | **Transitorio** — no consume intento, reintenta en la próxima pasada |
| El `CREAR_CLIENTE` se descartó por error permanente | Sube el pedido con `id_cliente = NULL` + notificación `PEDIDO_SIN_CLIENTE` |
| `mesa_id_local` sin `idServidor` | Igual: sube con `id_mesa = NULL` + notificación |
| Un **platillo** sin `idServidor` | **Permanente** — el pedido pasa a `ERROR`. Un pedido sin líneas válidas no tiene sentido |

> La regla de fondo: **un pedido que no sube es peor para el restaurante que un pedido sin
> nombre de cliente.** Se degrada el dato accesorio, nunca la transacción.

Y la prevención gana a la reparación: `ReglasPedido.puedePedirse(platillo)` devuelve `false`
para platillos con `idServidor == null` y el selector los oculta. El caso queda **inalcanzable
desde la UI**; el chequeo del sincronizador es un cinturón de seguridad, no el mecanismo.

---

## 5. PARTE A — Servidor

### 5.1 Clave de idempotencia

```sql
alter table public.pedido
    add column if not exists clave_idempotencia uuid;

create unique index if not exists uq_pedido_clave_idempotencia
    on public.pedido (clave_idempotencia) where clave_idempotencia is not null;
```

Nullable + índice **parcial**: las filas creadas desde el SQL Editor o por seeds no necesitan
clave, y no hay backfill (la tabla está vacía).

### 5.2 P-025

Ver §3.2 — el `CREATE OR REPLACE` del trigger más los cinco `ALTER … SET DEFAULT`.

### 5.3 El RPC `crear_pedido(jsonb)`

Forma del payload:

```json
{
  "clave_idempotencia": "uuid-generado-en-el-dispositivo",
  "fecha": "2026-08-05T19:04:11-06:00",
  "id_tipo_pedido": 1,
  "id_mesa": 4,
  "id_cliente": null,
  "lineas": [
    { "id_platillo": 7, "cantidad": 2, "complementos": [] }
  ]
}
```

Comportamiento exigido, en orden:

1. `security definer`, `set search_path = ''`, y **guard de rol**: `rol_actual()` no nulo y en
   `('admin','mesero')`. Si no → `raise exception` con mensaje para el usuario.
2. **Si `clave_idempotencia` ya existe** → devolver el `id_pedido` existente **sin insertar
   nada**. Es el corazón del diseño; se prueba explícitamente.
3. Resolver `id_usuario` desde `auth.uid()` vía `public.usuarios.id_auth_user`.
4. Acotar `fecha` a `[now() - interval '24 hours', now()]`; fuera de rango → `now()`.
5. Rechazar carrito vacío y carrito con más de **50 líneas** (ver Riesgos).
6. Insertar la cabecera con `id_estado_pedido = 1` (Pendiente) e `id_estado = 1`.
7. Insertar cada línea leyendo `precio` **de `platillo`**, no del payload (§2.3). Platillo
   inexistente o inactivo → `raise exception`.
8. Devolver el `id_pedido`.

Todo en una sola función, así que **toda la operación es una transacción**: o entra completa o
no entra.

```sql
revoke execute on function public.crear_pedido(jsonb) from public, anon;
grant  execute on function public.crear_pedido(jsonb) to authenticated;
```

### 5.4 Cerrar el `INSERT` directo

Hoy la RLS de `pedido` permite `INSERT` a admin y mesero. Con el RPC como única vía, esa policy
sobra y es un agujero: por ahí se puede crear un pedido sin líneas, sin clave de idempotencia y
con la fecha que se quiera. **Se elimina la policy de `INSERT` de `pedido` y de
`detalle_pedido`** — igual que se hizo con `UPDATE` en la Fase 3, donde solo entra por
`avanzar_estado_pedido`.

### 5.5 Índices que faltan

```sql
create index if not exists ix_detalle_pedido_id_pedido  on public.detalle_pedido (id_pedido);
create index if not exists ix_detalle_pedido_id_platillo on public.detalle_pedido (id_platillo);
```

El primero lo necesitan `vista_pedidos` (que agrupa por pedido) y la lectura del detalle; el
segundo, el "top de platillos más pedidos" de la Fase 3c.

### 5.6 Lectura del detalle

Vista `vista_detalle_pedido` con `security_invoker = on`, que una `detalle_pedido` con
`platillo` para traer el nombre — sin eso la hoja de detalle mostraría ids.

### 5.7 Pruebas de aceptación de la Parte A

Cada una dentro de `BEGIN … ROLLBACK`, simulando el rol con los usuarios reales de `perfiles`:

| # | Caso | Esperado | Verificado |
|---|---|---|---|
| A1 | `crear_pedido` como **mesero** con 2 líneas | 1 cabecera + 2 líneas, estado Pendiente | ✅ 2026-08-05 |
| A2 | **La misma clave de idempotencia dos veces** | Devuelve el mismo `id_pedido`; sigue habiendo 1 cabecera y 2 líneas | ✅ 2026-08-05 |
| A3 | `crear_pedido` como **cocina** | Excepción de rol | ✅ 2026-08-05 |
| A4 | Payload con `precio` manipulado | El precio guardado es el de `platillo`, no el del payload | ✅ 2026-08-05 |
| A5 | Carrito vacío | Excepción | ✅ 2026-08-05 |
| A6 | Carrito con 51 líneas | Excepción | ✅ 2026-08-05 |
| A7 | `fecha` de hace 3 días | Se acota a `now()` | ✅ 2026-08-05 |
| A8 | `fecha` de hace 2 horas | Se respeta | ✅ 2026-08-05 |
| A9 | Platillo inactivo | Excepción, y **no queda cabecera huérfana** | ✅ 2026-08-05 |
| A10 | `INSERT` directo en `pedido` como mesero | 0 filas (policy eliminada) | ✅ 2026-08-05 — bloqueado por RLS |
| A11 | `clock_timestamp()`: dos `UPDATE` en una transacción | `actualizado_en` distintos y crecientes | ✅ 2026-08-05 |
| A12 | **¿`realtime.send` es transaccional?** | Ver Riesgos | ✅ **Por inspección de código**, no por prueba de dos sesiones: `pg_get_functiondef('realtime.send')` muestra un `INSERT` plano en `realtime.messages` dentro de un bloque `EXCEPTION WHEN OTHERS` que solo swallow-ea errores del propio insert. Al ser un `INSERT` de tabla normal (no `pg_notify`), corre **dentro** de la transacción del trigger y solo es visible al *stream* de replicación de Realtime tras el `COMMIT` — la misma garantía transaccional que cualquier fila. Nota aparte: si el `INSERT` a `realtime.messages` fallara, el `WARNING` no aborta ni el trigger ni la transacción del pedido — el broadcast puede fallar en silencio sin bloquear la escritura, propiedad deseable pero a tener presente |
| A13 | `get_advisors(security)` | 0 errores | ✅ 2026-08-05 — 0 nivel `ERROR` (solo `WARN` preexistentes: exposición GraphQL y `SECURITY DEFINER` ya presentes desde Mesas/Clientes/Fase 3, `crear_pedido` se suma al mismo patrón esperado) |

---

## 6. PARTE B — Android

### 6.1 `domain`

| Clase | Notas |
|---|---|
| `model/LineaCarrito` | Inmutable: `idLocalPlatillo`, `idServidorPlatillo`, `nombre`, `precioEstimado`, `cantidad`. `subtotal()` **derivado** |
| `model/Carrito` | **Inmutable**: `con(platillo)`, `sinPlatillo(id)`, `conCantidad(id, n)` devuelven un `Carrito` nuevo. `total()`, `cantidadItems()`, `estaVacio()` derivados. **Fusiona** líneas del mismo platillo en vez de duplicarlas |
| `model/NuevoPedido` | `carrito`, `idLocalMesa?`, `idLocalCliente?`, `tipoPedido`, `claveIdempotencia` |
| `model/TipoPedido` | Enum con `porId()`, mismo molde que `EstadoMesa` |
| `ValidadorPedido` | Devuelve `Set<ErrorPedido>` — carrito vacío, tope de líneas, cantidad ≤ 0 |
| `ReglasPedido` (extender) | `puedeTomarPedido(rol)`, `puedePedirse(platillo)` |
| `repository/PedidoRepository` (extender) | `Result<Long> crear(NuevoPedido)`, `LiveData<List<LineaPedido>> observarDetalle(idLocal)` |

### 6.2 `data`

Room **v6**: `DetallePedidoEntity` (`id_local` PK, `id_pedido_local`, `id_platillo_local`,
`cantidad`, `precio`, `id_servidor` nullable), `DetallePedidoDao`, mapper,
`Migraciones.DE_5_A_6` + su test de `MigrationTestHelper`. `PedidoEntity` suma
`clave_idempotencia`.

`PayloadCrearPedido` en `data/sync/` — es el tercer tipo de payload, y el que justifica extraer
`PayloadOperacion` a un paquete `data/sync/payload/`.

`PedidoRepositorioLocal.crear()`: **una transacción de Room** que escribe la cabecera
`PENDIENTE` + las N líneas, encola **una** operación `CREAR_PEDIDO`, y llama `sincronizar()`.
Molde: `MesaRepositorioLocal`.

`SincronizadorPedidos` (extender): caso `CREAR_PEDIDO`, resolución de ids locales (§4.2),
solapamiento de la marca de agua (§3.3).

### 6.3 `ui`

Sobre lo que la Fase 3 deja: **FAB en el tablero** (visible con `PEDIDOS/CREAR`) →
`NuevoPedidoFragment` con `CarritoHoja`, `SelectorPlatilloHoja`, `SelectorMesaHoja`,
`SelectorClienteHoja`, más `DetallePedidoHoja` colgando de la tarjeta del tablero.
`NuevoPedidoViewModel` + `EstadoNuevoPedido` (estado único inmutable, con el `Carrito` adentro).

Los selectores de mesa y cliente **leen de Room** (los módulos 2c/2d ya existen): no hay red en
el camino de tomar un pedido.

---

## 7. Complementos — por qué no entran, y qué se propone

**No entran en 3b, y no hace falta una fase de CRUD antes.**

- La tabla tiene **0 filas**, sin pantalla de administración ni código Android.
- Un CRUD completo del molde de Mesas (entity + dao + mapper + remoto + sincronizador + 3 tipos
  de outbox + fragment/viewmodel/estado/factory/adapter) son **~60-85 tests**. Meterlo en la
  misma rama que la escritura más difícil del proyecto es exactamente lo que la Fase 3 §1.2 se
  negó a hacer, con la misma razón.
- Pero el **contrato queda cerrado hoy** (`lineas[].complementos[]`, §1.1).

**Propuesta: Fase 3d — Complementos.** No un módulo nuevo, sino **extender el módulo Menú**:
`complemento` tiene `nombre`/`descripcion`/`precio`/`id_estado`, es decir la misma forma que
`platillo`. Entra como segunda pestaña de `MenuFragment`, en la partición `Modulo.MENU` del
outbox y dentro de `SincronizadorMenu`. Eso lo baja de "módulo nuevo" a **~35 tests**.

---

## 8. Entregables

| # | Entregable | Parte | Tests |
|---|---|---|---|
| **E0** | ✅ Verificado en la base: índices reales de `detalle_pedido` (solo la PK, faltaban los dos de §5.5), columnas de `complemento` (0 filas, confirmado), y **A12** por inspección de código | A | — |
| **E1** | ✅ Parte A completa (§5.1–§5.6) en 4 migraciones nombradas + las 13 pruebas de §5.7 | A | 13 SQL |
| **E2** | `domain`: `Carrito`, `LineaCarrito`, `NuevoPedido`, `TipoPedido`, `ErrorPedido`, `ValidadorPedido`, `ReglasPedido`, contrato de `PedidoRepository` | B | ~26 |
| **E3** | Room v6: `DetallePedidoEntity`, DAO, mapper, columnas nuevas de `PedidoEntity`, `DE_5_A_6` + test de migración | B | ~16 |
| **E4** | `PayloadCrearPedido` + DTOs + `PedidoRemoto.crearPedido` / `detalleDe` | B | ~14 |
| **E5** | `PedidoRepositorioLocal.crear()` — escritura optimista transaccional + encolado | B | ~12 |
| **E6** | `SincronizadorPedidos`: `CREAR_PEDIDO`, resolución de ids locales, solapamiento de marca, orden de sincronizadores | B | ~20 |
| **E7** | `NuevoPedidoViewModel` + `EstadoNuevoPedido` + factory | B | ~18 |
| **E8** | UI: `NuevoPedidoFragment`, `CarritoHoja`, los tres selectores, adapters, FAB | B | — (manual) |
| **E9** | `DetallePedidoHoja` + carga del detalle bajo demanda | B | ~8 |
| **E10** | Documentación: [[Módulo Pedidos]] (sección de toma), **ADR-009/010/011**, cierre de P-025 y P-026, alta de **P-030** y de la deuda de "editar pedido" | — | — |

**Piso de la suite:** al cerrar la Fase 3 son ≥ 450. Al cerrar esta fase: **≥ 570**.
`./gradlew testDebugUnitTest assembleDebug` en BUILD SUCCESSFUL + el
[[Gate de Autoverificación]] impreso ítem por ítem.

### ADR (escritos al cerrar la Parte A)

| ADR | Decisión |
|---|---|
| ✅ [[ADR-009 - Escrituras multi-tabla por RPC transaccional con clave de idempotencia]] | Por qué no N operaciones de outbox, por qué la clave es del cliente, y por qué el patrón debería retroalimentarse a `crearCliente`/`crearMesa`/`crearPlatillo` |
| ✅ [[ADR-010 - El servidor sella el precio, el del dispositivo es una estimacion]] | Y qué se le muestra al usuario cuando difieren |
| ✅ [[ADR-011 - El cursor del sync delta es un reloj]] | `clock_timestamp()` + solapamiento, qué garantiza y qué no, y por qué se difiere la secuencia monótona. Cierra P-025, abre P-030 |

---

## 9. Pruebas de aceptación de la Parte B

| # | Caso | Esperado |
|---|---|---|
| B1 | Confirmar un carrito **sin red** | 1 `PedidoEntity` PENDIENTE + N `DetallePedidoEntity` en una transacción, 1 fila en el outbox, la UI lo muestra al instante |
| B2 | El mismo `CREAR_PEDIDO` drenado dos veces (respuesta perdida) | 1 sola cabecera en el servidor |
| B3 | Pedido con cliente creado offline; drena Clientes primero | Sube con el `id_cliente` real |
| B4 | Igual, pero el `CREAR_CLIENTE` fue descartado | Sube con `id_cliente` null + notificación |
| B5 | Igual, pero el `CREAR_CLIENTE` no drenó todavía | Transitorio, **no** consume intento |
| B6 | Agregar dos veces el mismo platillo | 1 línea con `cantidad = 2`, no 2 líneas |
| B7 | `Carrito` — cualquier operación | Devuelve instancia nueva; la original no muta |
| B8 | Delta con la marca solapada 2 s | Las filas re-recibidas no duplican ni pisan con datos viejos |

> [!note] Lo que el agente **no** puede verificar
> Que el flujo completo de tomar un pedido se **vea y se sienta bien** en un teléfono. Es
> prueba del usuario y se dice explícitamente en la entrega.

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **`realtime.send` resultara no transaccional** → un receptor pide el delta antes del commit y el pedido se pierde | **Prueba A12 antes de escribir código Android.** Si no lo es, la salida es emitir la señal desde el propio RPC al final, o `AFTER INSERT … DEFERRABLE` |
| El payload del carrito crece sin límite (mesa de 20 personas) | Tope de **50 líneas** en `ValidadorPedido` y en el RPC. Sin tope, `payload_json` es un blob sin techo en una tabla que se drena FIFO |
| `clock_timestamp()` cambia el orden observable y alguien lo lee como bug del tablero | El tablero ordena por `fecha`, no por `actualizado_en`. Documentar la distinción en [[Módulo Pedidos]] |
| Una excepción del RPC se clasifica como transitoria y el pedido reintenta 3 veces | PostgREST devuelve **400** para `raise exception` → permanente en `ClasificadorDeError`. **Verificarlo con un test, no asumirlo** |
| Presión de **P-015**: esta fase suma 2 Fragments y 3 hojas (17 → 24 pantallas) | Ver la recomendación de [[Roadmap de Fases]]: el pase B de deuda va **después** de esta fase |

---

## Relaciones

- [[Protocolo de Ejecución de un Plan]] — se lee antes que esto
- [[Plan Fase 3 - Pedidos en Tiempo Real]] — la fase que esta extiende
- [[Plan Fase 3c - Dashboard y Reportes]] — la que sigue, y que **depende** de esta
- [[Offline-First con Room y Outbox]] — la infraestructura que se reutiliza
- [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]] — por qué el cliente es opcional
- [[ADR-007 - Estados operativos en catálogos propios, separados de estado_general]]
- [[ADR-009 - Escrituras multi-tabla por RPC transaccional con clave de idempotencia]]
- [[ADR-010 - El servidor sella el precio, el del dispositivo es una estimacion]]
- [[ADR-011 - El cursor del sync delta es un reloj]]
- [[Deuda Técnica - Pendientes]] — cierra P-025 y P-026, abre P-030
- [[Esquema de Base de Datos]] · [[Roadmap de Fases]] · [[Gate de Autoverificación]]
- [[Módulo Menú]] — el módulo de referencia · [[Módulo Clientes]] · [[Módulo Mesas]]
- [[Módulo Pedidos]] — **todavía no existe**: lo crea E10 de este plan

---
title: "Offline-First con Room y Outbox"
tags:
  - patron
  - offline
  - sincronizacion
  - room
date: 2026-07-29
lifecycle: draft
---

# Offline-First con Room y Outbox

> [!abstract] Principio
> El requisito "que funcione con poco internet" **no se resuelve con timeouts más largos: se resuelve con arquitectura.** La app debe ser plenamente usable sin red, y sincronizar cuando pueda.

## Por qué es requisito #1 en este proyecto

Un restaurante en Honduras con Wi-Fi intermitente no puede depender de la red para tomar un pedido. Si el mesero no puede registrar una orden porque "no hay señal", la app no sirve — vuelven al papel.

---

## Las 8 reglas

### 1. Room es la única fuente de verdad

La UI **observa Room**, nunca la red. La red solo **actualiza** Room.

```
UI ←── LiveData ←── Room ←── (actualiza) ←── Red
```

Nunca: `UI ←── Red`.

### 2. Escritura optimista

La operación del usuario se escribe local **de inmediato** con `sync_state = PENDIENTE`. La UI responde al instante. `WorkManager` sincroniza después.

El mesero toca "Agregar al pedido" y el ítem aparece **ya**, haya red o no.

### 3. Cola de salida (outbox)

```sql
operaciones_pendientes(
    id INTEGER PRIMARY KEY,
    tipo TEXT,            -- CREAR_PEDIDO, ACTUALIZAR_MESA, …
    payload_json TEXT,
    intentos INTEGER,
    ultimo_error TEXT,
    created_at INTEGER
)
```

Un **`SyncWorker` único** (`ExistingWorkPolicy.KEEP`) la drena con `Constraints.NETWORK_CONNECTED` y backoff exponencial. Único = nunca dos workers compitiendo por la misma cola.

> [!danger] El trabajo único es un recurso compartido: no lo retengas si no podés trabajar
> Aprendido a los golpes el **2026-08-04** (ver [[Sesión 2026-08-04 - La carga inicial del Menú y el trabajo único envenenado]]).
>
> Con `KEEP`, mientras exista un trabajo vivo con ese nombre, **todo `enqueueUniqueWork` posterior se descarta en silencio**. Un worker que devuelve `Result.retry()` queda `ENQUEUED` con backoff — vivo. Entonces:
>
> **Un worker que no puede hacer nada debe terminar con `success()`, nunca con `retry()`.** Si falta el token, el usuario todavía no inició sesión: no hay trabajo posible, la pasada terminó. Devolver `retry()` ahí envenena el slot y hace que se descarten los pedidos que *sí* van a tener sesión — incluido el "sync-on-launch" de cada pantalla y el pull-to-refresh. La app queda muda hasta que el backoff se digne a disparar (15 s, 45 s, 1:45, 3:45…).
>
> No se pierde nada al no reintentar: **las operaciones viven en la tabla, el disparador no**. Encolar es barato y hay muchos caminos que lo recrean.
>
> Corolario: **encolá solo cuando el trabajo sea posible.** Si el disparador depende de la sesión, chequeala antes de encolar.

> [!warning] `ExistingWorkPolicy.REPLACE` está prohibido mientras el outbox no tenga clave de idempotencia
> Suena a la solución obvia para "el trabajo anterior quedó pegado", pero **`REPLACE` cancela el worker en ejecución**. Si lo mata entre el `POST` y el `marcarExito`, la operación sigue en la cola y se vuelve a postear: registro duplicado. Choca de frente con la regla de la tabla de abajo ("POST no idempotente: nunca reintentar sin *idempotency key*"). Y si el pull-to-refresh usa ese camino, cada gesto del usuario es una oportunidad de duplicar.

### 4. Sync delta, no full

El cliente guarda `last_sync_at` y pide **solo los cambios**:

```
GET /rest/v1/productos?select=id,nombre,precio&updated_at=gt.{last_sync_at}
```

**Nunca descargar la tabla completa.** En 3G, bajar 500 productos cada vez que se abre la app es la diferencia entre 2 segundos y 40.

> [!danger] Paginar el delta por marca de agua pierde filas
> Encontrado el 2026-08-04. La forma intuitiva —traer una página, avanzar la marca al máximo visto, pedir `gt.<esa marca>`— **tiene un agujero**: si hay más filas que el tamaño de página **compartiendo el mismo `updated_at`**, la siguiente consulta las excluye por definición, y esas filas no se bajan **nunca**.
>
> No es un caso raro: es exactamente lo que produce sembrar un catálogo con un `INSERT` masivo, donde todas las filas quedan con el mismo timestamp. O sea que se rompe justo en la instalación desde cero.
>
> **Dentro de una pasada, la marca queda fija y lo que avanza es el `offset`**; la marca máxima se guarda **al final**. Y el `order` necesita un desempate estable (`updated_at.asc,id.asc`): sin orden total, el `offset` puede repetir o saltear filas entre pedidos.
>
> Guardar la marca al final significa que un corte a mitad de camino re-baja desde la marca vieja. Es el precio correcto: aplicar filas es idempotente (se busca por `id_servidor`), perder filas no se arregla solo.
>
> Riesgo hermano del mismo bucle: si el progreso depende de que la marca avance, una página llena cuyas filas traigan `updated_at` nulo lo deja girando para siempre. Con `offset` el progreso está garantizado, y conviene igual un tope de páginas como cinturón.

> [!tip] Aplicá cada página en una sola transacción
> Insertar fila por fila son N transacciones SQLite con su `fsync` **y** N invalidaciones de tabla. Cada invalidación hace que Room vuelva a correr las consultas observadas, se remapee la lista completa y el `RecyclerView` se repinte entero. Con 50 filas eso son 50 repintados; agrupando, Room difiere las notificaciones hasta el commit y queda **una sola re-emisión por página**. En gama baja la diferencia se ve como "los ítems cargaron de a poco".
>
> Si el sincronizador recibe DAOs sueltos (para poder testearlo con fakes), no le pases la base entera: pasale una interfaz funcional de una línea que envuelva `runInTransaction`.

### 5. Resolución de conflictos declarada

Por defecto *last-write-wins* con el `updated_at` **del servidor**. Si el dominio no lo tolera (ej. stock), `ConflictResolver` explícito y documentado. Lo que no se vale es no decidir: el conflicto va a ocurrir.

### 6. Borrado lógico

`deleted = true`, nunca `DELETE` físico — si no, el borrado no se puede propagar a los otros dispositivos.

### 7. Migraciones explícitas y probadas

`MigrationTestHelper` para cada migración. **`fallbackToDestructiveMigration()` está prohibido en release**: borra los datos del usuario sin avisar. Ver [[Lista Negra de APIs Android]].

### 8. Estado visible para el usuario

La UI muestra **siempre** si el dato está pendiente de sincronizar, sincronizado, o en error. El usuario nunca queda con la duda de "¿se guardó o no?".

Un ícono de nube con tres estados en cada fila resuelve el 90% de los reclamos de soporte.

---

## Red con poco ancho de banda

| Regla | Valor |
|---|---|
| Timeouts | connect 15 s · read 30 s · write 30 s · callTimeout 45 s. **Nunca infinitos** |
| Reintentos | Backoff exponencial + jitter, máx. 3, **solo** en errores transitorios (timeout, 5xx, sin red) |
| POST no idempotente | **Nunca** reintentar sin *idempotency key* — se duplican pedidos |
| Caché HTTP | OkHttp ~10 MB + `max-stale` cuando no hay red |
| Payload | `?select=id,nombre,precio` siempre; paginación ≤ 50 filas |
| Imágenes | Comprimidas y redimensionadas **en el dispositivo** antes de subir |
| Ahorro de datos | Respetar `ConnectivityManager.getRestrictBackgroundStatus` |
| Deduplicación | Una sola llamada por recurso, aunque la pidan 3 pantallas a la vez |

---

## Estado en este proyecto

> [!success] Implementado (2026-08-01) — **P-014 cerrado**
> **Menú** y **Empleados** son local-first: la UI observa Room y el `SyncWorker` drena el
> outbox y baja el delta. Ver [[Módulo Menú]] y [[Módulo Empleados]].
>
> Infraestructura: `data/local` (Room 2.8.4, esquema v2), `data/outbox` **particionado por
> módulo** y `data/sync` con un **worker único** — la regla 3 de esta misma nota.
>
> El **login** queda fuera por definición: autenticar exige red. Lo que le falta es persistir
> la sesión, que es **P-009**, no cachearla.

> [!tip] La advertencia se cumplió, y salió barata por poco
> Esta nota decía que retro-adaptar offline-first sobre módulos ya escritos contra la red
> *"es una reescritura, no un refactor"*. Pasó exactamente eso: la Fase 2a escribió el Menú
> contra la red a propósito, y la 2b **reescribió** su capa `data` completa. Se pudo pagar
> porque eran dos módulos, no cinco. Mesas, Clientes y Pedidos nacen ya sobre esta base.

---

## Relaciones

- [[Repository Pattern]] — el repositorio es quien orquesta local + remoto
- [[Catálogo de Patrones Android]] — Strategy para `ConflictResolver` y `RetryPolicy`
- [[Presupuestos de Rendimiento en Gama Baja]]
- [[Result Pattern]]
- [[Roadmap de Fases]] — Fase 2
- [[Deuda Técnica - Pendientes]] — P-014

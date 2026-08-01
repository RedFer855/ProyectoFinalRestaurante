---
title: Plan Fase 2b — Offline-First con Room y Outbox
tags:
  - restaurante
  - plan
  - fase2b
  - offline
  - room
  - workmanager
  - sincronizacion
date: 2026-08-01
lifecycle: draft
---

# Plan Fase 2b — Offline-First con Room y Outbox

> [!danger] Leé primero [[Protocolo de Ejecución de un Plan]]
> Ahí está el contrato completo: la división Parte A / Parte B, el orden de lectura, las
> reglas de oro del código y qué significa "terminado". **No es opcional.**

> [!warning] Esta fase cierra **P-014**, la deuda más cara del proyecto
> Hoy el Menú lee y escribe **contra la red**. [[Offline-First con Room y Outbox]] dice que
> la UI observa Room y nunca la red, y P-014 advierte que retro-adaptar offline-first sobre
> módulos ya escritos contra la red *"no es un refactor: es una reescritura"*. La Fase 2a
> contrajo esa deuda a propósito y acotada; **2b es donde se paga**.

---

## 0. Reparto del trabajo

| Parte | Contenido | Quién |
|---|---|---|
| **A — Servidor** | Muy poco: el esquema ya sirve casi tal cual. Ver §2 | Agente **con** acceso a Supabase |
| **B — Código Android** | Todo lo demás: Room, outbox, `SyncWorker`, migrar el Menú | Cualquier agente |

**Sin acceso a Supabase: hacés solo la Parte B.** La Parte A de esta fase es tan chica que
la B puede empezar y terminar sin ella — lo único que se pierde es el punto §2.2, y el plan
dice explícitamente cómo trabajar sin eso.

---

## 1. Qué se construye

El objetivo no es "agregar una base local". Es que **la app sea plenamente usable sin red**:

| Historia | Hoy | Después de 2b |
|---|---|---|
| Abrir el Menú sin señal | Pantalla de error | La carta completa, al instante |
| Editar un precio sin señal | *"Sin conexión al servidor"* | Se guarda local y se sincroniza sola después |
| Abrir la app en 3G | Descarga las dos tablas enteras | Solo lo que cambió desde la última vez |
| Saber si algo se guardó | El Snackbar y nada más | Cada fila muestra si está sincronizada o pendiente |

**Alcance: solo el Menú.** La infraestructura (Room, outbox, `SyncWorker`) se construye
genérica y reutilizable, pero **el único módulo migrado en 2b es el Menú**. Empleados sigue
contra la red: es de admin, se usa con Wi-Fi de oficina y no es donde duele. Mesas y
Clientes **nacen** offline-first sobre esta base — ver [[Plan Fase 2c - CRUD de Mesas]] y
[[Plan Fase 2d - CRUD de Clientes]].

> [!important] Por qué 2b va **antes** que Mesas y Clientes
> Si Mesas y Clientes se escriben contra la red como el Menú, después hay **tres** módulos
> para reescribir en vez de uno. La ventana de oportunidad de P-014 ya se estiró una vez;
> estirarla dos módulos más es exactamente el error que el propio ítem describe.

---

## 2. PARTE A — Servidor (solo con acceso a Supabase)

> [!tip] Buena noticia: casi todo ya está
> La Fase 2a agregó `actualizado_en` con trigger `BEFORE UPDATE` a `platillo` y `categoria`
> **precisamente pensando en el sync delta de esta fase**, y el borrado ya es lógico
> (`id_estado`), que es el requisito 6 de [[Offline-First con Room y Outbox]].

### 2.1 Verificación previa (no es opcional)

> [!success] Parte A ejecutada y verificada el 2026-08-01
> Los tres puntos pasan y los índices de §2.2 están aplicados (migración
> `menu_indices_para_sync_delta`). **No queda nada de servidor pendiente en esta fase.**
> Ver [[Sesión 2026-08-01 - Indices del sync delta y puesta al dia de P-014 y P-024]].

Antes de tocar nada, confirmá contra la base real:

- [x] `platillo.actualizado_en` y `categoria.actualizado_en` existen, son `timestamptz NOT NULL`
      y tienen su trigger `BEFORE UPDATE` funcionando (un `UPDATE` los avanza solo).
- [x] `vista_platillos` y `vista_categorias` **exponen `actualizado_en`**. Si no lo exponen,
      el sync delta es imposible: agregalo a la vista.
- [x] Ambas vistas exponen `id_estado`, para que el cliente pueda replicar las bajas lógicas.

### 2.2 Lo único que hay que agregar

**Nada obligatorio.** Hay una sola mejora opcional y de bajo riesgo:

```sql
-- Índice para que el filtro del sync delta no haga seq scan cuando la tabla crezca.
create index if not exists ix_platillo_actualizado_en on public.platillo (actualizado_en);
create index if not exists ix_categoria_actualizado_en on public.categoria (actualizado_en);
```

Con 5 platillos no cambia nada; con 500 sí. Es idempotente y no afecta a nadie.

> [!success] Aplicados el 2026-08-01
> Migración `menu_indices_para_sync_delta`. Los dos índices existen.

> [!warning] Lo que **NO** hay que hacer acá
> No crear una función `ahora()` ni ningún endpoint de "hora del servidor". El cliente
> **no necesita el reloj del servidor** con el diseño de §4.3, y agregar ese endpoint es
> superficie de API nueva para resolver un problema que no existe.

### 2.3 Verificación de la Parte A

Dentro de una transacción revertida: `UPDATE` a un platillo y confirmar que
`actualizado_en` avanzó solo, y que `select … where actualizado_en > $1` devuelve esa fila y
no las demás. `get_advisors(security)` → 0 errores.

> [!danger] Esta verificación **no se puede hacer en una sola transacción**
> Es una trampa del propio criterio de aceptación, y costó descubrirla. El trigger escribe
> `now()`, que en Postgres es la hora de **inicio de la transacción**, no la del `UPDATE`.
> Si el corte también se toma con `now()` dentro de la misma transacción, los dos valores
> son **idénticos** y `actualizado_en > corte` devuelve **cero filas** — parece que el sync
> delta está roto cuando en realidad lo que está mal es el test.
>
> Verificado el 2026-08-01 **entre transacciones separadas**: un `UPDATE` en una, y el
> filtro `actualizado_en > $corte` en otra → devuelve exactamente la fila tocada. ✅
>
> Que `now()` sea la hora de inicio no es solo una molestia del test: abre una ventana real
> por la que el sync puede perderse un cambio. Quedó registrado como **P-025** en
> [[Deuda Técnica - Pendientes]].

---

## 3. PARTE B — Dependencias

Agregar a `gradle/libs.versions.toml` **y** a `app/build.gradle.kts`:

| Librería | Versión | Para qué |
|---|---|---|
| `androidx.room:room-runtime` | **2.8.4** | La base local |
| `androidx.room:room-compiler` | **2.8.4** (`annotationProcessor`) | Genera los DAOs |
| `androidx.work:work-runtime` | **2.11.2** | El `SyncWorker` |
| `androidx.room:room-testing` | **2.8.4** (`testImplementation`) | `MigrationTestHelper` |

Versiones verificadas contra el `maven-metadata.xml` de Google Maven el **2026-08-01**:
Room `release = 2.8.4`; WorkManager estable más alta = `2.11.2` (la `2.12.0-beta01` es beta y
**no se usa** — la regla de vigencia del [[Estándar de Ingeniería Android]] pide estables).

> [!note] Room sí lleva `annotationProcessor`
> A diferencia de Glide (donde se evitó a propósito), Room **no funciona** sin su procesador
> de anotaciones: los DAOs son código generado. Es un costo de build aceptado.

**KSP no se usa acá**: es para Kotlin y el proyecto es Java puro ([[ADR-004 - Java + Views en vez de Kotlin + Compose]]). Va `annotationProcessor`.

Hay que exportar el esquema, que es lo que hace testeables las migraciones:

```kotlin
android {
    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}
```

El agente que lo implemente debe **verificar esta sintaxis contra AGP 9.2.1** antes de darla
por buena; si difiere, que corrija este plan en la misma entrega. Los JSON de `app/schemas/`
**se versionan**: son la referencia contra la que se prueban las migraciones.

---

## 4. PARTE B — Arquitectura de la sincronización

### 4.1 El cambio que atraviesa todo: las lecturas pasan a `LiveData`

Es **el** cambio de contrato de esta fase, y ya estaba anticipado en
[[Plan de Fase 2 - Menu]]:

```java
// Antes (2a)
Result<List<Platillo>> listarPlatillos();

// Después (2b)
LiveData<List<Platillo>> observarPlatillos();
```

Una lectura ya **no puede fallar**: Room siempre tiene algo que devolver, aunque sea una
lista vacía. Lo que sí puede fallar es la **sincronización**, y eso se reporta aparte (§4.5).

> [!danger] `domain` no puede importar `androidx.lifecycle`
> `LiveData` es de `androidx`, y la regla 1 lo prohíbe en `domain`. Dos salidas, y hay que
> elegir una y ser consistente:
>
> | Opción | Pro | Contra |
> |---|---|---|
> | **A — `domain` define su propio `Observable<T>` mínimo**, y `data` adapta Room → ese tipo | `domain` queda 100% Java puro | Una abstracción propia más para mantener, y hay que puentearla a `LiveData` en el ViewModel igual |
> | **B — Ampliar la excepción a `androidx.lifecycle`**, como ya se hizo con `androidx.annotation` | Cero código de puente, el `LiveData` de Room llega directo al ViewModel | `domain` deja de ser Java puro estricto |
>
> **Recomendada: B**, con el mismo razonamiento que se aceptó para `androidx.annotation` en
> la Fase 2a — `lifecycle-livedata` ya es dependencia del proyecto, no arrastra runtime de
> Android en los unit tests (con `InstantTaskExecutorRule` corre en la JVM, como ya lo hacen
> `LoginViewModelTest` y `MenuViewModelTest`), y la alternativa es escribir un `Observable`
> propio cuyo único usuario sería un adaptador a `LiveData`.
>
> **Si elegís B, actualizá la regla 1 del [[Protocolo de Ejecución de un Plan]] y de
> [`AGENTS.md`](../../AGENTS.md) en la misma entrega.** Una excepción no documentada es
> deuda silenciosa.

### 4.2 Las escrituras: optimistas y encoladas

```
Usuario toca "Guardar"
   → 1. Room escribe la fila con estadoSync = PENDIENTE   ← la UI ya se actualizó
   → 2. Se encola una operación en el outbox
   → 3. Result.ok(null)  ← la operación "terminó" para el usuario
   → 4. WorkManager drena el outbox cuando haya red
```

El paso 3 devuelve `ok` **aunque no haya red**. Eso es el punto entero de la fase.

### 4.3 Sync delta con marca de agua, sin reloj compartido

El cliente guarda por tabla el `actualizado_en` **más alto que recibió**, y pide solo lo
posterior:

```
GET rest/v1/vista_platillos?select=*&actualizado_en=gt.{marca}&order=actualizado_en.asc&limit=50
```

> [!tip] Por qué la marca sale de los datos y no de un reloj
> Si el cliente usara su propia hora, el desfase del teléfono se traduce en filas perdidas
> o repetidas. Tomando `max(actualizado_en)` **de las filas recibidas**, la marca siempre
> está en la escala de tiempo del servidor y no hace falta ningún endpoint de hora.
>
> Caso borde a respetar: si dos filas comparten exactamente el mismo `actualizado_en`,
> `gt.` puede saltear una. Por eso se pagina con `order=actualizado_en.asc&limit=50` y se
> repite hasta recibir menos de 50; y la escritura en Room es **`OnConflictStrategy.REPLACE`**
> por PK, así que recibir una fila dos veces es inofensivo.

La marca vive en una tabla propia de Room:

```
sincronizacion(tabla TEXT PRIMARY KEY, marca_agua TEXT, ultimo_intento INTEGER, ultimo_error TEXT)
```

**La primera sincronización** tiene la marca vacía y baja todo — es la única vez que se baja
la tabla completa, y es correcto: no hay nada local.

### 4.4 El outbox

```
operaciones_pendientes(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tipo TEXT,             -- CREAR_PLATILLO, ACTUALIZAR_PLATILLO, CAMBIAR_ESTADO_PLATILLO,
                           -- QUITAR_IMAGEN_PLATILLO, CREAR_CATEGORIA, ...
    id_local INTEGER,      -- a qué fila local afecta
    payload_json TEXT,
    ruta_imagen_local TEXT,-- ver §5.2
    intentos INTEGER,
    ultimo_error TEXT,
    creado_en INTEGER
)
```

Un **`SyncWorker` único** con `ExistingWorkPolicy.KEEP`, `Constraints.NETWORK_CONNECTED` y
backoff exponencial. Único = nunca dos workers compitiendo por la misma cola.

Orden de drenado: **FIFO estricto por `id`**. Si una operación falla con un error
*permanente* (4xx que no sea 401/408/429), se marca como fallida y **se saca de la cola**
para que no bloquee a las demás; si falla con uno *transitorio* (sin red, 5xx, timeout), se
reintenta con backoff hasta 3 veces y el worker devuelve `Result.retry()`.

> [!danger] Un `POST` reintentado duplica filas
> `crearPlatillo` no es idempotente. Acá el proyecto tiene suerte: `uq_platillo_nombre`
> (único sobre `lower(btrim(nombre))`) hace que el segundo intento choque con un 409, que es
> **permanente** → se descarta sin duplicar. **Documentá esa dependencia**: si algún día se
> quita ese índice, aparecen platillos duplicados y nadie va a saber por qué. Para Pedidos,
> que no tiene un único natural, va a hacer falta una *idempotency key* de verdad.

### 4.5 Estado visible para el usuario (regla 8, no es opcional)

Cada fila lleva `estadoSync`: `SINCRONIZADO` · `PENDIENTE` · `ERROR`. La UI lo muestra —un
ícono chico en la tarjeta del platillo alcanza— y hay un indicador global de "sincronizando"
y de "hay N cambios sin subir".

> [!warning] Sin esto la fase está incompleta, aunque todo lo demás funcione
> El usuario que no ve la diferencia entre "guardado" y "guardado y subido" pierde la
> confianza en la app la primera vez que algo se pierde. `EstadoMenu` gana esos campos.

### 4.6 Conflictos

**Last-write-wins con el `actualizado_en` del servidor.** Si al bajar una fila el servidor
tiene un `actualizado_en` mayor que el local **y** la fila local está `PENDIENTE`, gana el
servidor y la operación local se descarta con un aviso al usuario. Está declarado a
propósito: lo que no se vale es no decidir.

---

## 5. Trampas concretas de esta entrega

### 5.1 `fallbackToDestructiveMigration()` está prohibido

Borra los datos del usuario sin avisar. Está en la [[Lista Negra de APIs Android]]. Cada
cambio de esquema lleva su `Migration` escrita **y su test con `MigrationTestHelper`**.

### 5.2 Las imágenes no van en el `payload_json`

Un JPEG de 2 MB en base64 dentro de una fila de SQLite es ~2.7 MB de texto, y SQLite tiene
un límite práctico por fila. **La imagen comprimida se guarda como archivo en
`context.getFilesDir()`** y el outbox guarda solo la ruta. Al drenar la operación con éxito,
se borra el archivo.

Esto también arregla algo de 2a: hoy `ImagenPlatillo` viaja en memoria desde el diálogo
hasta el repositorio y se pierde si el proceso muere. Con el archivo en disco, sobrevive.

### 5.3 El `SyncWorker` no puede depender de `SesionActual`

`SesionActual` guarda la sesión **en memoria** (**P-009**). Un worker puede arrancar con el
proceso recién creado, sin sesión: la operación falla con 401 y el outbox se vacía de la peor
manera.

**Mínimo para esta fase:** si no hay sesión, el worker devuelve `Result.retry()` **sin tocar
la cola** y no marca nada como fallido.

**Lo correcto** es cerrar **P-009** (persistir el token cifrado) y esta fase es el momento
natural: es la primera vez que el proyecto necesita trabajar sin que haya una pantalla
abierta. Está **fuera del alcance de 2b**, pero si el agente ve que sin eso la sincronización
en segundo plano es de juguete, que lo diga en la nota de sesión y se planifique aparte.

### 5.4 Room no puede guardar `Platillo` de `domain` tal cual

`@Entity` es de Room y `domain` no puede importarlo. La entidad va en `data.local.entity`
con su propio mapeo a/desde `domain.model.Platillo` — el mismo trabajo que ya hacen los DTOs.
**No pongas anotaciones de Room en las clases de `domain`.**

### 5.5 El `id` local vs. el `id` del servidor

Un platillo creado sin red no tiene `id_platillo` todavía. La entidad de Room necesita
**PK propia** (`id_local`) y una columna `id_servidor` nullable que se completa cuando el
`POST` responde. Todas las referencias locales usan `id_local`.

Si esto no se hace, crear un platillo offline y editarlo antes de que sincronice es
imposible: no hay a qué apuntar.

### 5.6 `MenuViewModel` deja de llamar a `cargar()`

Con `LiveData` desde Room, la carga inicial desaparece: el `observe` ya trae lo que hay. Lo
que reemplaza a "recargar" es **pedir una sincronización**, que es otra cosa y se muestra
distinto (un `SwipeRefreshLayout`, no una pantalla de "cargando").

---

## 6. Entregables

| # | Entregable | Contenido |
|---|---|---|
| **E1** | Dependencias | Room, WorkManager, `room-testing`, `room.schemaLocation` |
| **E2** | `data/local/` | `AppDatabase`, entidades (`PlatilloEntity`, `CategoriaEntity`, `OperacionPendienteEntity`, `SincronizacionEntity`), DAOs |
| **E3** | Mapeo | Entidad ↔ `domain.model`, en `data/local/mapper/` |
| **E4** | Outbox | `Outbox` (encolar/drenar), política de reintentos, clasificación permanente vs. transitorio |
| **E5** | `SyncWorker` | WorkManager, unique work `KEEP`, constraints, backoff, disparo tras cada escritura y al abrir la app |
| **E6** | `MenuRepository` v2 | Lecturas por `LiveData` desde Room, escrituras locales + encolado. **La interfaz de `domain` cambia; la UI casi no** |
| **E7** | UI de estado de sync | `estadoSync` por fila, indicador global, "N cambios sin subir", `SwipeRefreshLayout` |
| **E8** | Pruebas | DAOs (Robolectric o instrumentado), outbox y clasificación de errores (JUnit puro), `MenuViewModel` con un repositorio falso que devuelve `LiveData`, `MigrationTestHelper` |

---

## 7. Qué NO hacer en esta entrega

| No hagas | Por qué |
|---|---|
| Migrar Empleados a Room | Es de admin, con Wi-Fi de oficina. Alcance propio |
| Mesas o Clientes | Son [[Plan Fase 2c - CRUD de Mesas]] y [[Plan Fase 2d - CRUD de Clientes]], y **nacen** sobre esta base |
| Persistir/cifrar el token | **P-009**, alcance propio — pero leé §5.3 |
| Reorganizar a feature-first | **P-017**, sub-fase 2e |
| Hilt | **P-002** |
| Sincronizar imágenes bidireccionalmente | Solo subida. Bajar fotos es trabajo de Glide y su caché |

---

## 8. Definición de terminado

- [ ] `./gradlew testDebugUnitTest assembleDebug` → **BUILD SUCCESSFUL**, con más de 127 tests.
- [ ] Con el avión activado: la carta se ve completa, se puede crear y editar un platillo, y
      los cambios aparecen marcados como pendientes.
- [ ] Al volver la red, el outbox se drena solo y las filas pasan a sincronizadas.
- [ ] Ninguna clase de `domain/` importa Room, Retrofit ni `android.*`.
- [ ] `fallbackToDestructiveMigration()` no aparece en ninguna parte.
- [ ] El [[Gate de Autoverificación]] impreso ítem por ítem, sin ❌.
- [ ] **P-014** marcado como resuelto en [[Deuda Técnica - Pendientes]], con su fila de historial.
- [ ] Nota de sesión, [[Arquitectura Actual]], [[Conocimiento Principal]] y [[Módulo Menú]] actualizados.
- [ ] Nota nueva en `20 - Patrones/` o actualización de [[Offline-First con Room y Outbox]]
      con lo que **realmente** se implementó (hoy es `lifecycle: draft` y describe el ideal).

Fuera del alcance del agente, **lo verifica el usuario**: probar el modo avión en un
dispositivo real. Es la única forma de saber si esto funciona.

---

## Relaciones

- [[Protocolo de Ejecución de un Plan]] — **léelo primero**
- [[Offline-First con Room y Outbox]] — las 8 reglas que esta fase implementa
- [[Plan de Fase 2 - Menu]] — el paraguas: por qué 2a/2b/2c/2d/2e van separadas
- [[Plan Fase 2a - CRUD de Platillos y Categorias]] — lo que 2b migra
- [[Módulo Menú]] — el estado vivo del módulo
- [[Plan Fase 2c - CRUD de Mesas]] · [[Plan Fase 2d - CRUD de Clientes]] — nacen sobre esta base
- [[ADR-005 - Offline-first obligatorio desde la Fase 2]]
- [[Deuda Técnica - Pendientes]] — **P-014** (se cierra acá), P-009, P-002, P-017
- [[Lista Negra de APIs Android]] · [[Estrategia de Pruebas Android]]
- [[Presupuestos de Rendimiento en Gama Baja]]
- [[Esquema de Base de Datos]]

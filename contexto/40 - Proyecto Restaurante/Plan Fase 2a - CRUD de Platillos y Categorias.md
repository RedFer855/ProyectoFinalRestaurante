---
title: Plan Fase 2a — CRUD de Platillos y Categorías
tags:
  - restaurante
  - plan
  - fase2a
  - menu
  - storage
  - rls
date: 2026-07-31
lifecycle: draft
---

# Plan Fase 2a — CRUD de Platillos y Categorías

> [!important] Para el agente que va a ejecutar este plan
> Vos hacés **solo código Android**. Todo lo que este plan describe del lado de Supabase
> (tablas, columnas, vistas, triggers, policies RLS y el bucket de Storage) **ya está
> aplicado y verificado** — lo hizo otro agente con acceso al conector de Supabase el
> 2026-07-31. Está documentado acá para que sepas contra qué estás programando, **no**
> para que lo ejecutes.
>
> **No corras SQL. No crees migraciones. No toques `supabase/`.** Si algo del servidor
> no te cuadra o te falta una columna, **paralo y decilo** en la nota de sesión: lo
> resuelve el agente que sí tiene acceso. Improvisar el esquema desde el cliente es
> exactamente lo que este plan busca evitar.

---

## 0. Cómo se trabaja en este proyecto (leé esto antes que nada)

### 0.1 La bóveda de Obsidian

La carpeta `contexto/` **no es documentación decorativa**: es la base de conocimiento del
proyecto, versionada en git y abierta como bóveda de Obsidian. Es la fuente de verdad
sobre el estado del sistema, y está por encima de lo que vos "recuerdes" o infieras
leyendo el código.

**Orden de lectura obligatorio antes de escribir una línea:**

1. [`AGENTS.md`](../../AGENTS.md) de la raíz — reglas de oro y build.
2. [[Estándar de Ingeniería Android]] — el contrato: stack permitido, prohibiciones,
   protocolo de salida de una entrega.
3. [[Arquitectura Actual]] — el estado **real** del sistema, distinto del ideal.
4. [[Deuda Técnica - Pendientes]] — la brecha entre ambos, en ítems `P-NNN`.
5. [[contexto/CLAUDE|CLAUDE]] — convenciones de código detalladas.
6. [[Plan Fase 1d - Modulo Empleados Funcional]] — **el módulo que vas a replicar**.
7. [[contexto/AGENTS|AGENTS de la bóveda]] — cómo clasificar y guardar lo que hagas.

**Cosas de la bóveda que se aplican a vos y no son opcionales:**

- **Enlaces `[[wikilink]]`.** Los documentos se enlazan entre sí por el nombre del
  archivo sin extensión. Toda nota cierra con una sección `## Relaciones`. Un
  `[[enlace]]` a algo que todavía no existe **no es un error**: marca algo por escribir.
- **Anti-duplicados (crítico).** Antes de crear una nota, buscá si ya existe una del
  mismo tema y **actualizala** en vez de crear una nueva. Varios agentes trabajan sobre
  esta bóveda sin conocerse; duplicar es el riesgo #1.
- **Frontmatter YAML obligatorio** en toda nota: `title`, `tags`, `date` (siempre
  absoluta, `2026-08-05`, nunca "hoy"). Las notas de sesión agregan `branch:` y
  `autor_cambios:`.
- **Taxonomía de carpetas:** `10 - Arquitectura` (conceptos generales) · `20 - Patrones`
  (patrones reutilizables) · `40 - Proyecto Restaurante` (estado vivo) · `45 - Decisiones`
  (ADRs) · `50 - Referencia` (hechos externos: SDKs, bugs de librerías) ·
  `70 - Bitácora de Cambios/AAAA-MM` (notas de sesión).
- **Deuda técnica:** nunca archivos sueltos. Es un ítem `P-NNN` **dentro** de
  [[Deuda Técnica - Pendientes]], más una fila en la tabla de historial del final. El
  último asignado es **P-022**; si encontrás algo roto que no arreglás, tomá el
  siguiente libre (**P-023** en adelante) y **uno por agente**, no reserves rangos.
- **Al terminar, documentás.** No es opcional. Como mínimo: una nota de sesión en
  `contexto/70 - Bitácora de Cambios/2026-08/` con el formato
  `Sesión AAAA-MM-DD - Título descriptivo.md`, más la actualización de
  [[Arquitectura Actual]] y del estado en [[Conocimiento Principal]]. Usá las plantillas
  de `contexto/_templates/`.
- **Nunca escribas una contraseña en la bóveda.** Se versiona en git; anotarla ahí
  equivale a publicarla.

### 0.2 Las reglas de oro del código (no negociables)

Están completas en [`AGENTS.md`](../../AGENTS.md). Las que más te van a morder acá:

1. **`domain` nunca referencia `data`**, y **no importa nada** de `android.*`,
   `androidx.*`, Retrofit, Room ni Glide. La dependencia va `ui → domain ← data → core`.
2. `ui` habla **solo** con interfaces de `domain`. Nunca con un `ApiService` ni con un DTO.
3. **Un único objeto de estado inmutable por pantalla** (`EstadoMenu`), no `LiveData`
   sueltas por campo.
4. **Todo I/O fuera del hilo principal, con `Executor` inyectado por constructor** —
   crear el executor adentro del ViewModel es la deuda **P-005**, ya cerrada; no la
   reintroduzcas.
5. Todo método de red devuelve `Result`/`Result<T>`. Nunca una excepción cruda a la UI,
   nunca `catch (Exception e) {}` vacío, nunca `printStackTrace()`.
6. **Cero strings, colores o dimens hardcodeados.** Todo en recursos.
7. **Todo código nuevo trae su prueba.** Sin prueba la entrega está incompleta.
8. Cero `// TODO`, cero placeholders, cero APIs de la [[Lista Negra de APIs Android]].
9. Toda dependencia nueva se declara en `gradle/libs.versions.toml` **y** en
   `app/build.gradle.kts`, en la misma entrega. **Nunca inventes un número de versión**:
   verificalo en Maven Central o documentalo como supuesto.

### 0.3 Verificación

```bash
./gradlew testDebugUnitTest assembleDebug
```

Ambos tienen que terminar en **BUILD SUCCESSFUL**. Hoy hay **56 tests** en verde; tu
entrega tiene que dejar ese número más alto, no igual. Al final imprimís el
[[Gate de Autoverificación]] ítem por ítem.

---

## 1. Qué se construye

El módulo Menú hoy es **maqueta**: `MenuFragment` lee de `DatosMaqueta.platillos()`,
una lista `Arrays.asList` hardcodeada, y cualquier acción muestra un Snackbar que dice
"es una maqueta". Al terminar 2a el Menú tiene que ser un módulo real:

| # | Historia | Rol |
|---|---|---|
| 1 | Ver el catálogo de platillos con su foto, precio y categoría | admin, mesero, cocina |
| 2 | Filtrar por categoría y buscar por nombre | los tres |
| 3 | Crear un platillo (nombre, descripción, precio, categoría, **foto**) | solo admin |
| 4 | Editar un platillo, incluida **cambiar o quitar** la foto | solo admin |
| 5 | **Desactivar / reactivar** un platillo (nunca borrarlo) | solo admin |
| 6 | Crear, renombrar y desactivar categorías | solo admin |
| 7 | Borrar una categoría **solo si no tiene platillos** | solo admin |

Y `DatosMaqueta.Platillo`, `DatosMaqueta.Categoria`, `DatosMaqueta.platillos()` y
`DatosMaqueta.categorias()` **desaparecen** del archivo (igual que 1d borró
`DatosMaqueta.Empleado`). El resto de `DatosMaqueta` — pedidos, mesas, clientes,
reportes — **se queda**: esos módulos siguen siendo maqueta.

> [!tip] Tu referencia es Empleados, no el login
> `ui/empleados/` + `data/repository/SupabaseEmpleadoRepository.java` +
> `domain/repository/EmpleadoRepository.java` son el patrón vigente y aprobado. Copiá esa
> estructura. El login es código de Fase 1, escrito antes del estándar.

---

## 2. Lo que YA está hecho en Supabase (no lo toques)

Proyecto **Restaurante** (`mxarlisuueovxvttytcm`), esquema `public`. Aplicado y
verificado el 2026-07-31 en cuatro migraciones:
`menu_estado_imagen_y_auditoria`, `menu_vistas_y_reglas_de_borrado`,
`menu_bucket_storage_platillos`, `menu_revocar_rpc_de_funciones_de_trigger`.

### 2.1 Columnas nuevas

`platillo` tenía solo `id_platillo`, `nombre`, `descripcion`, `precio`, `id_categoria`.
Se le agregó:

| Columna | Tipo | Para qué |
|---|---|---|
| `id_estado` | `int NOT NULL DEFAULT 1` → `estado_general` | Borrado lógico. `1 = Activo`, `2 = Inactivo` |
| `ruta_imagen` | `text NULL` | **Ruta dentro del bucket**, p. ej. `a3f9…-c1.jpg`. **Nunca la URL completa** |
| `actualizado_en` | `timestamptz NOT NULL DEFAULT now()` | Lo necesita el sync delta de la Fase 2b |

`categoria` tenía solo `id_categoria` y `descripcion`. Se le agregó `id_estado` y
`actualizado_en`, iguales.

> [!warning] `ruta_imagen` guarda la ruta, no la URL
> Si guardaras `https://mxarlisuueovxvttytcm.supabase.co/storage/v1/object/public/platillos/x.jpg`,
> el día que cambie el proyecto, el dominio o el nombre del bucket, **todas las filas
> quedan apuntando a la nada**. La fila guarda `x.jpg`; la URL la arma el cliente.

### 2.2 Reglas que el servidor impone

| Objeto | Qué hace |
|---|---|
| `ck_platillo_precio_positivo` | `CHECK (precio > 0)`. Un precio 0 no es un platillo gratis, es un error de captura |
| `uq_platillo_nombre` | Índice único sobre `lower(btrim(nombre))` |
| `uq_categoria_descripcion` | Índice único sobre `lower(btrim(descripcion))` |
| `ix_platillo_categoria` | Índice del filtro por categoría |
| `trg_platillo_actualizado_en` | `BEFORE UPDATE`: pone `actualizado_en = now()`. **No mandes vos ese campo** |
| `trg_categoria_actualizado_en` | Ídem en `categoria` |
| `trg_platillo_no_borrar` | `BEFORE DELETE`: **siempre falla**. Mensaje: *"Los platillos no se borran, se desactivan. Borrar uno rompería el historial de pedidos."* |
| `trg_categoria_no_borrar_con_platillos` | `BEFORE DELETE`: falla si la categoría tiene platillos. Mensaje: *"No se puede borrar una categoría que todavía tiene platillos."* |

La unicidad es **insensible a mayúsculas y a espacios de sobra** a propósito: "Baleada",
"baleada" y `"Baleada "` son el mismo platillo para un mesero, y tres filas así vuelven
inútil el buscador.

Los dos triggers de borrado tienen la misma **válvula de escape** que
`proteger_admins()` de la Fase 1d: si no hay sesión (`auth.uid()` es `null`) no aplican,
para poder reparar la base desde el SQL Editor. Desde la app **siempre** hay sesión, así
que desde el teléfono siempre aplican.

### 2.3 Vistas para leer

`security_invoker = on` en las dos: la vista respeta la RLS de **quien consulta**, no la
de su dueño. Mismo patrón que `vista_empleados`.

**`vista_platillos`** → `id_platillo`, `nombre`, `descripcion`, `precio`, `id_categoria`,
`nombre_categoria`, `ruta_imagen`, `id_estado`, `activo` (bool), `actualizado_en`.

**`vista_categorias`** → `id_categoria`, `descripcion`, `id_estado`, `activo`,
`cantidad_platillos`, `cantidad_platillos_activos`, `actualizado_en`.

Se **lee** de las vistas y se **escribe** en las tablas `platillo` / `categoria`.
`cantidad_platillos` es lo que te dice si la categoría se puede borrar sin pedirlo al
servidor.

### 2.4 RLS — quién puede qué

Ya existían de antes y **no cambiaron**:

| Tabla | `SELECT` | `INSERT` / `UPDATE` / `DELETE` |
|---|---|---|
| `platillo` | cualquier rol con sesión activa | solo `admin` |
| `categoria` | cualquier rol con sesión activa | solo `admin` |

Se apoyan en la función `rol_actual()`, que lee `perfiles.rol` del `auth.uid()` de la
sesión **y exige `activo = true`**. Un empleado desactivado no lee ni escribe nada.

> [!danger] Esto sí es la seguridad; ocultar el botón no lo es
> `Permisos`/`VistaPorPermiso` mejoran la experiencia (no mostrar un botón que va a
> fallar), pero un APK se modifica. Quien impide que un mesero cambie un precio es la
> policy de Postgres. Programá asumiendo que el servidor va a decir que no, y mostrá
> bien ese "no".

### 2.5 Storage — bucket `platillos`

| Propiedad | Valor |
|---|---|
| `id` / `name` | `platillos` |
| `public` | **`true`** |
| `file_size_limit` | **2 MB** (2 097 152 bytes) |
| `allowed_mime_types` | `image/jpeg`, `image/png`, `image/webp` |

Policies sobre `storage.objects`: `INSERT`, `UPDATE` y `DELETE` solo si
`bucket_id = 'platillos'` **y** `rol_actual() = 'admin'`. **No hay policy de `SELECT` a
propósito**: sin ella, *listar* el contenido del bucket está bloqueado; *leer una foto
por su ruta* funciona porque el bucket es público.

> [!info] Por qué el bucket es público — decisión tomada, no descuido
> Un bucket privado obligaría a pedir una **signed URL** por cada imagen antes de
> mostrarla, y a invalidar la caché de Glide cada vez que expira: mucho código en la ruta
> más caliente de la pantalla, en teléfonos de 2 GB de RAM. La foto de una baleada es
> material de menú, no dato personal. **Escribir sigue siendo solo de admin.** Si algún
> día se guarda ahí algo sensible, esta decisión hay que revisarla.

El límite de 2 MB y los tipos permitidos los impone **el servidor**: si mandás un archivo
más grande o un GIF, la subida falla con 400 aunque la app lo haya dejado pasar. Comprimí
antes de subir (§5.3).

### 2.6 Datos que ya están cargados

Podés probar de inmediato, no hace falta sembrar nada:

- `categoria`: `1 = Entradas`, `2 = Platos fuertes`, `3 = Bebidas`, `4 = Postres`.
- `platillo`: 5 filas (Baleada sencilla, Pollo con tajadas, Sopa de caracol, Refresco de
  tamarindo, Tres leches), todas con `id_estado = 1` y **`ruta_imagen = NULL`**.
- `estado_general`: `1 = Activo`, `2 = Inactivo`.

Que las 5 arranquen sin foto es útil: el *placeholder* de "platillo sin imagen" es el
caso que vas a ver primero, así que resolvelo bien.

### 2.7 Verificación que ya se corrió (en transacción revertida)

| Caso | Resultado |
|---|---|
| Mesero lee `vista_platillos` | ✅ 5 filas |
| Mesero edita un platillo | 🚫 0 filas afectadas (RLS) |
| Cocina crea un platillo | 🚫 *violates row-level security policy* |
| Admin edita → `actualizado_en` avanza solo | ✅ |
| Admin borra un platillo | 🚫 *"Los platillos no se borran, se desactivan…"* |
| Insertar `"  baleada SENCILLA "` | 🚫 *duplicate key … uq_platillo_nombre* |
| Insertar con `precio = 0` | 🚫 *violates check constraint ck_platillo_precio_positivo* |
| Borrar `Entradas` (tiene platillos) | 🚫 *"No se puede borrar una categoría que todavía tiene platillos."* |

`get_advisors(security)` → **0 errores**.

---

## 3. Contrato HTTP exacto

Base: `BuildConfig.SUPABASE_URL`. El header `apikey` lo pone solo el interceptor de
`SupabaseClient`; el `Authorization: Bearer <access_token>` lo pone cada método, igual
que en `SupabaseEmpleadoApi`.

### 3.1 PostgREST

| Operación | Verbo y ruta |
|---|---|
| Listar platillos | `GET rest/v1/vista_platillos?select=*&order=nombre` |
| Listar categorías | `GET rest/v1/vista_categorias?select=*&order=descripcion` |
| Crear platillo | `POST rest/v1/platillo` + `Prefer: return=representation` |
| Editar platillo | `PATCH rest/v1/platillo?id_platillo=eq.{id}` |
| Activar/desactivar platillo | `PATCH rest/v1/platillo?id_platillo=eq.{id}` con `{"id_estado": 1\|2}` |
| Crear categoría | `POST rest/v1/categoria` + `Prefer: return=representation` |
| Editar categoría | `PATCH rest/v1/categoria?id_categoria=eq.{id}` |
| Borrar categoría | `DELETE rest/v1/categoria?id_categoria=eq.{id}` |

> [!danger] El filtro del `PATCH`/`DELETE` es parámetro **obligatorio** de la interfaz
> Un `PATCH` sin filtro en PostgREST actualiza **todas** las filas de la tabla, y un
> `DELETE` sin filtro las borra todas. Declaralo `@Query(...) String idIgualA` sin valor
> por defecto, como ya hace `SupabaseEmpleadoApi#actualizarEmpleado`.

**`Prefer: return=representation`** hace que el `POST` devuelva la fila creada (con su
`id_platillo` generado). Sin ese header PostgREST responde `201` con cuerpo vacío y no
tenés el id. Ojo: devuelve un **array de un elemento**, no un objeto — el tipo es
`Call<List<PlatilloDto>>`.

### 3.2 Storage

| Operación | Verbo y ruta |
|---|---|
| Subir | `POST storage/v1/object/platillos/{ruta}` · `Content-Type: image/jpeg` · body = bytes |
| Reemplazar | `PUT storage/v1/object/platillos/{ruta}` |
| Borrar | `DELETE storage/v1/object/platillos/{ruta}` |
| Leer (público, sin token) | `GET {SUPABASE_URL}/storage/v1/object/public/platillos/{ruta}` |

La URL pública se arma en `ui`, nunca en `domain`. Un solo lugar:

```java
public static String urlDePlatillo(@Nullable String rutaImagen) { … }
```

que devuelve `null` si `rutaImagen` es `null`, para que el adapter muestre el
*placeholder*.

---

## 4. Los 7 entregables

### E1 — Gradle y dependencias

Agregar a `gradle/libs.versions.toml` **y** a `app/build.gradle.kts`:

| Librería | Para qué | Nota |
|---|---|---|
| **Glide** | Cargar y cachear las fotos | El estándar la fija: *"Imágenes → Glide. Coil es Kotlin-first"* |
| **androidx.exifinterface** | Rotar la foto según su EXIF | Sin esto, las fotos de cámara salen acostadas |

> [!warning] Verificá las versiones, no las inventes
> La regla de vigencia del [[Estándar de Ingeniería Android]] es explícita. Consultá
> Maven Central y, si no podés, dejalo escrito en `## SUPUESTOS`. **No** agregues el
> `annotationProcessor` de Glide (`compiler`): con `Glide.with(...)` alcanza y evitás un
> procesador de anotaciones en el build.

**No agregues Room en esta entrega.** Es la Fase 2b.

### E2 — Arreglar el interceptor de `SupabaseClient`

> [!danger] Esto te va a romper la subida de imágenes si no lo arreglás primero
> `core/SupabaseClient.java` hace hoy:
> ```java
> Request withApiKey = original.newBuilder()
>         .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
>         .header("Content-Type", "application/json")   // ← pisa el de TODAS las requests
>         .build();
> ```
> `header(...)` **reemplaza**. Cuando subas un JPEG, el interceptor le va a poner
> `Content-Type: application/json` a un cuerpo binario y Storage lo va a rechazar.
>
> **Arreglo:** que el interceptor ponga `Content-Type` **solo si la request no trae ya un
> cuerpo con su propio tipo** (`original.body() == null || original.body().contentType() == null`).
> El `apikey` sí va siempre.

Agregar también `getMenuApi()` y `getStorageApi()` con el mismo doble-chequeo
sincronizado que los tres getters existentes.

### E3 — Capa `domain` (Java puro, sin Android)

```
domain/model/Platillo.java          ← inmutable, con getters; incluye rutaImagen y activo
domain/model/NuevoPlatillo.java     ← lo que se manda al crear (sin id)
domain/model/Categoria.java         ← incluye cantidadPlatillos y cantidadPlatillosActivos
domain/model/ImagenPlatillo.java    ← byte[] + mimeType. NADA de android.graphics acá
domain/ValidadorPlatillo.java       ← nombre no vacío, precio > 0, categoría elegida
domain/ReglasMenu.java              ← espejo en el cliente de las reglas del servidor
domain/repository/MenuRepository.java
```

`MenuRepository` — la única cara que `ui` va a ver:

```java
Result<List<Platillo>> listarPlatillos();
Result<List<Categoria>> listarCategorias();
Result<Platillo> crearPlatillo(NuevoPlatillo nuevo, @Nullable ImagenPlatillo imagen);
Result<Void> actualizarPlatillo(Platillo platillo, @Nullable ImagenPlatillo imagenNueva);
Result<Void> quitarImagen(Platillo platillo);
Result<Void> cambiarEstadoPlatillo(int idPlatillo, boolean activo);
Result<Categoria> crearCategoria(String descripcion);
Result<Void> renombrarCategoria(int idCategoria, String descripcion);
Result<Void> cambiarEstadoCategoria(int idCategoria, boolean activo);
Result<Void> borrarCategoria(int idCategoria);
```

`ReglasMenu` es el equivalente de `ReglasEmpleado`: métodos como
`puedeBorrarse(Categoria)` (→ `cantidadPlatillos == 0`), que el adapter usa para no
ofrecer una acción que el servidor va a rechazar. **Que la app sea más estricta que el
servidor es seguro; al revés es el problema.**

`ImagenPlatillo` transporta `byte[]` y no un `Bitmap`/`Uri` justamente porque `domain`
no puede importar `android.*`. Comprimir y rotar es trabajo de `ui` (§5.3).

### E4 — Capa `data`

```
data/remote/SupabaseMenuApi.java       ← PostgREST (§3.1)
data/remote/SupabaseStorageApi.java    ← Storage (§3.2)
data/remote/dto/PlatilloDto.java
data/remote/dto/CategoriaDto.java
data/remote/dto/CrearPlatilloDto.java
data/remote/dto/ActualizarPlatilloDto.java   ← con factories, ver abajo
data/remote/dto/CrearCategoriaDto.java
data/remote/dto/ActualizarCategoriaDto.java
data/repository/SupabaseMenuRepository.java
```

Copiá de `SupabaseEmpleadoRepository`:

- **El token entra por `Supplier<String>` inyectado**, no leyendo `SesionActual` directo:
  `data` no depende de dónde vive la sesión y el repositorio queda testeable.
- `catch (IOException)` → *"Sin conexión al servidor. Intentá de nuevo."*
  `catch (SecurityException)` → *"La app no tiene permiso de red…"* (es lo que causó
  **P-022**: un `SecurityException` sin atrapar mata el proceso).
- `mensajeDeError(Response, porDefecto)` que parsea `{"message": "..."}` de PostgREST.
  **Los mensajes de los triggers de §2.2 sí se le muestran al usuario**: los escribimos
  nosotros en lenguaje humano y no filtran nada interno. Es la misma excepción acotada
  que documentó la Fase 1d.

**Actualizaciones parciales.** Gson **omite los campos nulos**, así que
`ActualizarPlatilloDto` con factories (`soloEstado(...)`, `soloDatos(...)`,
`conImagen(...)`) manda solo lo que cambió y no pisa el resto. Mismo truco que
`ActualizarPerfilDto`.

> [!warning] El caso "quitar la foto" es la excepción y necesita otro camino
> Para borrar `ruta_imagen` hay que mandar **`{"ruta_imagen": null}` explícito**, y eso es
> justo lo que Gson omite. No cambies la configuración global del converter
> (`serializeNulls()` rompería todas las actualizaciones parciales). Usá un
> `@Body RequestBody` con el JSON literal en ese único método:
> ```java
> RequestBody.create(MediaType.parse("application/json"), "{\"ruta_imagen\":null}")
> ```

**Orquestación de la imagen al crear un platillo** — hay dos sistemas y pueden desincronizarse:

1. Generar la ruta: `UUID.randomUUID() + ".jpg"`.
2. `POST` a Storage.
3. Si falla → devolver `Result.fail` y **no** insertar nada.
4. `POST` a `platillo` con `ruta_imagen` = la ruta.
5. **Si el insert falla, borrar el objeto recién subido.** Es la misma compensación que
   hace la Edge Function `crear-empleado` cuando borra la cuenta de Auth si el insert
   falla. Sin esto, cada error deja basura permanente en el bucket.

Al **reemplazar** una foto: subir la nueva → `PATCH` la fila → borrar la vieja. En ese
orden: si se cae en el medio, sobra un archivo (barato) en vez de faltar la foto de un
platillo que sí existe (visible para el usuario).

> [!note] Deuda conocida y aceptada
> Si el borrado del archivo viejo falla, queda huérfano y nadie lo limpia. No hay
> recolector de basura del bucket, y no lo construyas en 2a. **Registralo como ítem
> `P-NNN` nuevo** en [[Deuda Técnica - Pendientes]].

### E5 — ViewModel

```
ui/menu/MenuViewModel.java
ui/menu/MenuViewModelFactory.java
ui/menu/EstadoMenu.java
```

`EstadoMenu` copia la forma de `EstadoEmpleados`: los **cuatro** estados reales
(cargando · con datos · vacío · error) donde **`isVacio()` se deriva**, nunca se guarda
como bandera que pueda contradecir a la lista; más `mensajeExito` como evento de un solo
disparo con `conMensaje()` / `sinMensaje()`.

`EstadoMenu` carga platillos **y** categorías (las necesita el filtro por chips y el
selector del formulario), más `filtroCategoria` y `textoBusqueda`.

- El **`ExecutorService` se inyecta por constructor**. La Factory lo crea.
- El **filtro y la búsqueda viven en el ViewModel**, no en el Fragment: así sobreviven a
  la rotación. Hoy `MenuFragment` los tiene como campos propios y se pierden al rotar.
- Tras **cada operación exitosa se relee del servidor** en vez de retocar la lista en
  memoria: lo que se ve es lo que la base aceptó, con triggers incluidos.

### E6 — UI

```
ui/menu/MenuFragment.java              [MODIFICADO] — deja de leer DatosMaqueta
ui/menu/PlatilloAdapter.java           [MODIFICADO] — usa domain.model.Platillo + Glide
ui/menu/FormularioPlatilloDialog.java  [NUEVO]
ui/menu/CategoriasDialog.java          [NUEVO]
ui/menu/UrlDeImagen.java               [NUEVO] — arma la URL pública desde la ruta
res/layout/item_platillo.xml           [MODIFICADO] — ImageView de la foto
res/layout/dialog_platillo.xml         [NUEVO]
res/layout/dialog_categorias.xml       [NUEVO]
res/layout/item_categoria.xml          [NUEVO]
res/values/strings.xml                 [MODIFICADO]
```

- **`FormularioPlatilloDialog`**: un mismo diálogo para alta y edición, igual que
  `FormularioEmpleadoDialog`. Campos: nombre, descripción, precio, categoría (dropdown) y
  foto (previsualización + "Elegir foto" + "Quitar foto").
- **`PlatilloAdapter`** sigue filtrando cada opción del ⋮ con `VistaPorPermiso`. En
  `Permisos`, `Modulo.MENU` da a `admin` `VER/CREAR/EDITAR/ELIMINAR`, y a `mesero` y
  `cocina` solo `VER`. **`Accion.ELIMINAR` ahora significa "desactivar"** — el ítem del
  menú se etiqueta "Desactivar"/"Reactivar", nunca "Eliminar", porque el servidor no
  permite borrar. Si no queda ninguna acción, el botón ⋮ **desaparece** en vez de abrir
  un menú vacío.
- Un platillo inactivo se distingue visualmente (atenuado + etiqueta), no se esconde:
  el admin tiene que poder reactivarlo.
- **Accesibilidad:** `contentDescription` en la foto y en el ⋮; el área táctil mínima
  ya está en `@dimen/altura_minima_tactil`. Ver [[Accesibilidad Android]].

### E7 — Pruebas

Sin Mockito — el proyecto usa **fakes manuales** a propósito, y ya tenés la
infraestructura: `app/src/test/java/.../data/FakeCall.java` implementa `retrofit2.Call<T>`
sin red, y los DTOs de fixture se arman con `Gson.fromJson()` en vez de agregarles
constructores solo para testear.

| Suite | Casos mínimos |
|---|---|
| `SupabaseMenuRepositoryTest` | listar sin sesión · listar OK · sin conexión · crear OK · crear con error del servidor (mensaje del trigger) · **crear con la imagen subida pero el insert fallando → se pide el `DELETE` del objeto** |
| `MenuViewModelTest` | carga inicial · estado vacío · error de red · filtro por categoría · búsqueda · el filtro sobrevive a una recarga |
| `ValidadorPlatilloTest` | nombre vacío · precio 0 y negativo · precio válido · sin categoría |
| `ReglasMenuTest` | categoría con platillos no se puede borrar · categoría vacía sí |

El caso en negrita es el importante: es el que prueba que no dejás basura en el bucket.

---

## 5. Trampas concretas de esta entrega

### 5.1 Registrar el selector de fotos en el momento correcto

Usá **`ActivityResultContracts.PickVisualMedia`** (el *photo picker* del sistema): no
pide **ningún permiso** de almacenamiento, lo cual importa porque
`READ_EXTERNAL_STORAGE` está en la [[Lista Negra de APIs Android]] y en `targetSdk 37` el
modelo de permisos de medios es otro. Viene en `androidx.activity`, que ya está.

> [!danger] `registerForActivityResult` no se puede llamar desde el `onClick`
> Tiene que llamarse **antes de que el Fragment llegue a `STARTED`** — o sea, en la
> inicialización del campo o en `onCreate()`. Llamarlo dentro del listener del botón
> lanza `IllegalStateException` y te tira la app. Como el selector vive en un
> `DialogFragment`, registralo como **campo** del `DialogFragment`.

### 5.2 Leer el `Uri` que devuelve el picker

Es un `content://`, no una ruta de archivo: se lee con
`requireContext().getContentResolver().openInputStream(uri)`. **No** intentes construir
un `File` con él.

### 5.3 Comprimir y rotar antes de subir

[[Offline-First con Room y Outbox]] lo pide explícitamente: *"Imágenes comprimidas y
redimensionadas en el dispositivo antes de subir"*. El objetivo real: 3G hondureño y
teléfonos de 2 GB.

1. **Medir sin cargar**: `BitmapFactory.Options.inJustDecodeBounds = true`.
2. **Calcular `inSampleSize`** para que el lado largo quede en ~1024 px, y decodificar de
   verdad. Cargar un JPEG de 12 MP entero en un teléfono de 2 GB es `OutOfMemoryError`.
3. **Corregir la rotación** leyendo `ExifInterface.TAG_ORIENTATION`. Sin esto, las fotos
   sacadas con la cámara aparecen acostadas.
4. **Comprimir** con `Bitmap.compress(JPEG, 80, …)` a un `ByteArrayOutputStream`.
5. **Verificar el tamaño final contra 2 MB antes de subir** y avisar con un mensaje claro
   si no entra, en vez de dejar que Storage devuelva un 400 críptico.

`Bitmap.CompressFormat.WEBP` está deprecado desde API 30 — si querés WebP usá
`WEBP_LOSSY`, que existe desde API 30 y necesita guard con `minSdk 24`. **Lo más simple y
seguro acá es JPEG.**

### 5.4 Glide y el caché de una foto que cambió

Si reemplazás la foto reusando la misma ruta, Glide sirve la vieja desde su caché. Por
eso el flujo de §E4 usa **una ruta nueva (UUID) en cada reemplazo** en vez de
sobrescribir: la URL cambia, el caché no miente y no hay que invalidar nada a mano.

### 5.5 `precio` es `numeric`, no `float`

PostgREST lo serializa como número JSON con decimales (`35.00`). Mapealo a `double` en el
DTO (el proyecto ya usa `double` para precios en la maqueta) y formatealo **siempre** con
`R.string.formato_lempiras`, nunca concatenando.

### 5.6 El token vence y no se persiste

**P-009** sigue abierto: `SesionActual` guarda la sesión **en memoria** y no hay refresh
de token. Si una llamada devuelve `401`, no lo trates como "error del servidor": el
mensaje correcto es *"Tu sesión venció. Volvé a iniciar sesión."*, igual que hace
`SupabaseEmpleadoRepository` cuando el token es `null`. **No implementes el refresh en
2a** — es P-009 y tiene su propio alcance.

---

## 6. Qué NO hacer en esta entrega

| No hagas | Por qué |
|---|---|
| Room, `SyncWorker`, outbox | Es la Fase 2b. Meterlo acá hace la entrega irrevisable |
| Reorganizar paquetes a feature-first | Es **P-017**, sub-fase 2c |
| Renombrar IDs de vistas a `camelCase` | Es **P-011**, bloqueado por P-017 — son ~15 layouts |
| Hilt | **P-002**, sigue en DI manual por Factory |
| Persistir o refrescar el token | **P-009**, alcance propio |
| Tocar `DatosMaqueta` fuera de Platillo/Categoría | Pedidos, mesas, clientes y reportes siguen siendo maqueta |
| Correr SQL o crear migraciones | No tenés acceso a Supabase; el servidor ya está listo |

---

## 7. Definición de terminado

- [ ] `./gradlew testDebugUnitTest assembleDebug` → **BUILD SUCCESSFUL**, con más de 56 tests.
- [ ] `DatosMaqueta.Platillo`, `DatosMaqueta.Categoria`, `platillos()` y `categorias()` eliminados.
- [ ] Ninguna clase de `domain/` importa `android.*`, `androidx.*`, Retrofit, Glide ni Gson.
- [ ] Cero strings hardcodeados en los archivos nuevos.
- [ ] El [[Gate de Autoverificación]] impreso ítem por ítem, sin ❌.
- [ ] Nota de sesión en `contexto/70 - Bitácora de Cambios/2026-08/`, con `branch:` y `autor_cambios:`.
- [ ] [[Arquitectura Actual]], [[Conocimiento Principal]] y este plan actualizados.
- [ ] Deuda nueva registrada como `P-NNN` (mínimo: los archivos huérfanos del bucket).
- [ ] Nota `Módulo Menú.md` en `40 - Proyecto Restaurante/`, con el formato de [[Módulo Login]].

Queda fuera del alcance del agente y lo verifica el usuario: **probar el flujo completo en
un emulador o dispositivo** (subir una foto real, verla en la lista, reemplazarla,
desactivar un platillo). No hay Android SDK ni adb en el entorno del agente.

---

## Relaciones

- [[Plan de Fase 2 - Menu]] — el paraguas: por qué 2a, 2b y 2c van separadas
- [[Plan Fase 1d - Modulo Empleados Funcional]] — **el patrón a replicar**
- [[Esquema de Base de Datos]] — el esquema completo
- [[Estándar de Ingeniería Android]] · [[Gate de Autoverificación]] · [[Lista Negra de APIs Android]]
- [[Offline-First con Room y Outbox]] — lo que 2b tiene que implementar
- [[Seguridad y Privacidad Android]] · [[Accesibilidad Android]]
- [[Deuda Técnica - Pendientes]] — P-002, P-009, P-011, P-014, P-017
- [[Plan Fase 1c - Maqueta Visual por Roles]] — de dónde sale la maqueta que se reemplaza

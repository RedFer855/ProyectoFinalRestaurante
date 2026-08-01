---
title: "Sesión 2026-07-31 — Fase 2a implementada (CRUD de Menú con fotos en Storage)"
tags:
  - sesion
  - restaurante
  - fase2
  - fase2a
  - menu
  - storage
  - glide
date: 2026-07-31
branch: feat/fase2-menu
autor_cambios: Claude Code
---

# Sesión 2026-07-31 — Fase 2a implementada (CRUD de Menú con fotos en Storage)

> [!success] Resultado
> El Menú dejó de ser maqueta. Los 7 entregables de
> [[Plan Fase 2a - CRUD de Platillos y Categorias]] están escritos: `domain`, `data`, el
> ViewModel, la UI con fotos vía Glide y las pruebas. **124 tests en verde** (eran 56 al
> empezar la fase) y `assembleDebug` limpio. Falta la prueba manual en un dispositivo, que
> no se puede hacer desde acá.

---

## Problema / motivo

La sesión anterior dejó el servidor listo y el plan escrito, pero
`feat/fase2-menu` **no tenía una sola línea de código Android**: `MenuFragment` seguía
leyendo de `DatosMaqueta.platillos()`, una lista `Arrays.asList` hardcodeada, y cualquier
acción mostraba un Snackbar que decía "es una maqueta". Esta sesión es la otra mitad: solo
código Android, sin tocar Supabase.

## Cambios aplicados

### E1 — Dependencias

`gradle/libs.versions.toml` + `app/build.gradle.kts`:

- **Glide 4.16.0** — última estable en Maven Central; la 5.0.0 sigue en `rc01`. Sin el
  `annotationProcessor`: con `Glide.with(...)` alcanza y se evita un procesador de
  anotaciones en el build.
- **androidx.exifinterface 1.4.2** — verificada contra el `maven-metadata.xml` de Google
  Maven (release 1.4.2, publicado 2025-12-03).

Room **no** entró: es la Fase 2b.

### E2 — `core/SupabaseClient.java`

El interceptor hacía `.header("Content-Type", "application/json")` en **toda** petición.
Como `header(...)` reemplaza, el cuerpo binario de una subida a Storage habría viajado
declarado como JSON y el servidor lo habría rechazado. Ahora la cabecera se pone solo si
`body() == null || body().contentType() == null`; el `apikey` sigue yendo siempre.

La lógica se extrajo a `conCabeceras(Request)`, visible dentro del paquete: es lo que
permite probarla sin levantar un servidor ni fabricar un `Interceptor.Chain` falso.
Se agregaron además `getMenuApi()` y `getStorageApi()`.

### E3 — `domain/` (Java puro)

```
model/Platillo.java        model/NuevoPlatillo.java     model/Categoria.java
model/ImagenPlatillo.java  ValidadorPlatillo.java       ReglasMenu.java
repository/MenuRepository.java
```

- `ValidadorPlatillo` devuelve un `Set<ErrorPlatillo>` (enum anidado), **no** texto: el
  dominio no ve `R`, y un validador que devuelve strings no se puede traducir.
- `ReglasMenu` espeja cuatro reglas del servidor: el borrado de categorías
  (`cantidadPlatillos == 0`), los índices únicos `lower(btrim(...))`, el límite de 2 MB y
  los tres MIME permitidos. Que la app sea **más** estricta que el servidor es seguro; al
  revés es el problema.
- `ImagenPlatillo` transporta `byte[]` y no un `Bitmap`/`Uri` porque `domain` no puede
  importar `android.*`. Clona el arreglo al entrar y al salir: sin eso, quien la construye
  podría seguir mutando los bytes y la clase dejaría de ser inmutable.

### E4 — `data/`

```
remote/SupabaseMenuApi.java     remote/SupabaseStorageApi.java
remote/dto/{Platillo,Categoria,CrearPlatillo,ActualizarPlatillo,CrearCategoria,ActualizarCategoria}Dto.java
repository/SupabaseMenuRepository.java
```

Copia el patrón de `SupabaseEmpleadoRepository`: token por `Supplier<String>` inyectado,
`IOException` → *"Sin conexión al servidor"*, `SecurityException` → *"La app no tiene
permiso de red"* (P-022), y `mensajeDeError()` que deja pasar el texto de los triggers,
porque lo redactamos nosotros en lenguaje humano.

Actualizaciones parciales con factories (`soloEstado`, `soloDatos`, `conImagen`) y campos
objeto para que Gson omita lo que no cambió. El caso *quitar la foto* usa un
`RequestBody` con el JSON literal `{"ruta_imagen":null}`, porque un null en el DTO se
omitiría — y `serializeNulls()` global rompería todas las demás actualizaciones.

### E5 — ViewModel

`EstadoMenu`, `MenuViewModel`, `MenuViewModelFactory`. Cuatro estados con `isVacio()`
derivado, `Executor` inyectado, filtro y búsqueda **en el ViewModel** (antes eran campos
del Fragment y se perdían al rotar), y relectura del servidor tras cada operación exitosa.

### E6 — UI

`MenuFragment` y `PlatilloAdapter` reescritos contra `domain`, más
`FormularioPlatilloDialog`, `CategoriasDialog`, `UrlDeImagen`, `CompresorDeImagen`, los
layouts `dialog_platillo`/`dialog_categorias`/`item_categoria`, los menús
`menu_platillo`/`menu_categoria` y el drawable `ic_platillo_sin_foto`.

`DatosMaqueta.Platillo`, `.Categoria`, `platillos()` y `categorias()` **eliminados**. El
resto de `DatosMaqueta` (pedidos, mesas, clientes, reportes) sigue en pie: esos módulos
siguen siendo maqueta.

### E7 — Pruebas

68 tests nuevos, con fakes manuales sobre el `FakeCall` que ya existía (sin Mockito):
`SupabaseClientTest` (5), `SupabaseMenuRepositoryTest` (20), `MenuViewModelTest` (14),
`ReglasMenuTest` (16), `ValidadorPlatilloTest` (8).

## Cinco decisiones que valen para lo que sigue

**No se compensa el borrado de la foto cuando falla la red.** El plan pedía "si el insert
falla, borrar el objeto subido", sin distinguir el caso. Se implementó solo para el rechazo
explícito del servidor (respuesta HTTP no exitosa): con un `IOException` no se sabe si el
insert entró, y borrar la foto de un platillo que sí se creó deja una imagen rota y visible
al usuario, mientras que el archivo huérfano es invisible y ya está cubierto por **P-023**.

**El estado del platillo se deriva de `id_estado`, no de la columna `activo`.** La tabla
`platillo` no tiene `activo` —es de la vista—, así que la respuesta de un `INSERT` la
traería siempre en `false`. `id_estado` existe en las dos.

**`crearPlatillo` devuelve el platillo con `nombreCategoria` en `null`**, por lo mismo: el
`INSERT` responde con la fila de la tabla, no de la vista. No se nota porque el ViewModel
relee después de cada operación, y está anotado en el DTO y en el modelo.

**No se implementó el `PUT` de reemplazo de Storage** que figura en la tabla §3.2 del plan.
El flujo elegido (ruta UUID nueva en cada reemplazo, §5.4) nunca sobrescribe, así que ese
método sería código muerto.

**Los errores de Storage no se le muestran al usuario**, a diferencia de los de los
triggers: vienen en inglés y con detalle interno (*"Payload too large"*).

## Desvíos del plan, con su porqué

| Desvío | Por qué |
|---|---|
| Se creó `ui/menu/CompresorDeImagen.java`, que no está en la lista de E6 | El §5.3 pide el trabajo (medir con `inJustDecodeBounds`, `inSampleSize` a ~1024 px, rotar por EXIF, JPEG 80%) pero no le asigna archivo |
| El compresor corre en un `ExecutorService` **del `DialogFragment`** | Decodificar una foto de 12 MP en el hilo principal son cientos de ms de jank en gama baja. No puede ir al ViewModel: necesita un `ContentResolver`, y la regla 4 prohíbe pasarle `Context` a un ViewModel |
| Se modificó `fragment_menu.xml`, que tampoco está en la lista | No tenía indicador de progreso (la maqueta no cargaba nada) y hacía falta un punto de entrada para las categorías |
| Se crearon `menu_platillo.xml` y `menu_categoria.xml` en vez de reusar `menu_acciones.xml` | El compartido tiene un ítem "Eliminar", justo la palabra que el plan prohíbe en este módulo |
| `CategoriasDialog` se cierra después de cada acción | Mantenerlo abierto dejaría los contadores de platillos viejos, y son exactamente lo que decide si "Borrar" se ofrece: terminaría ofreciendo acciones que el servidor rechaza |
| La nota de sesión quedó en `2026-07/` y no en `2026-08/` | El plan asumía que el trabajo caía en agosto. La fecha real es 2026-07-31 |
| Se relajó un ítem del checklist del plan | Ver abajo |

### El checklist del plan se contradecía a sí mismo

La "Definición de terminado" pedía que **ninguna** clase de `domain/` importara
`androidx.*`, pero la firma de `MenuRepository` que el propio plan escribe en §E3 lleva
`@Nullable`, y `Empleado`, `NuevoEmpleado` y `ReglasEmpleado` ya lo importaban desde la
Fase 1d. Se relajó el texto a *"ningún `androidx.*` **salvo `androidx.annotation`**"*, con
la justificación escrita en el propio plan: es un JAR de anotaciones sin runtime de
Android, y lo que la regla protege —que `domain` se pueda testear en la JVM— se sigue
cumpliendo (los 44 tests de dominio corren sin Android).

## Verificación

```bash
./gradlew testDebugUnitTest assembleDebug
```

Ambos **BUILD SUCCESSFUL**. **124 tests, 0 fallos** (56 al inicio de la fase → el gate pide
que el número suba, no que se mantenga).

| Suite | Tests |
|---|---|
| `SupabaseMenuRepositoryTest` | 20 |
| `ReglasMenuTest` | 16 |
| `MenuViewModelTest` | 14 |
| `ValidadorPlatilloTest` | 8 |
| `SupabaseClientTest` | 5 |
| Preexistentes (login, empleados, permisos, dominio) | 61 |

El caso que más importa —`crearPlatillo_imagenSubidaPeroInsertRechazado_borraElObjetoDelBucket`—
verifica que la ruta borrada del bucket es exactamente la que se subió. Y
`quitarImagen_mandaElNullExplicitoYBorraElArchivo` lee el `RequestBody` del fake para
confirmar que el JSON que sale por el cable es literalmente `{"ruta_imagen":null}`.

### Gate de autoverificación

| Ítem | Estado |
|---|---|
| Compila; sin símbolos inventados | ✅ `assembleDebug` BUILD SUCCESSFUL |
| Dependencias en el catálogo y en el `build.gradle.kts` | ✅ Glide y exifinterface en ambos |
| Sin APIs deprecadas ni de la lista negra | ✅ `PickVisualMedia` (sin permisos), JPEG y no `WEBP` deprecado |
| `domain/` sin Android | ✅ salvo `androidx.annotation` — excepción documentada arriba |
| La UI no toca `ApiService` | ✅ solo `MenuRepository` |
| I/O fuera del hilo principal, en `Executor` inyectado | ✅ en el ViewModel; ⚠️ el del compresor lo crea el `DialogFragment` (justificado arriba) |
| ViewModel sin `Context`/`View` | ✅ |
| Observers con `getViewLifecycleOwner()`; vistas liberadas | ✅ `MenuFragment.onDestroyView()` |
| Cero strings, colores o dimens hardcodeados | ✅ verificado con grep sobre todos los layouts; de paso salieron el `88dp` y el `12dp` que ya había |
| Cero secretos en el código | ✅ todo por `BuildConfig` |
| Nulabilidad anotada | ✅ |
| Errores como `Result`; sin catch vacío ni `printStackTrace()` | ✅ el único catch silencioso es el de `borrarArchivo()`, documentado y ligado a P-023 |
| Nomenclatura | ⚠️ IDs de vista en `snake_case` — **P-011**, bloqueado por P-017 |
| Room / offline-first | ➖ N/A — Fase 2b |
| Listas con `ListAdapter` + `DiffUtil` | ✅ `PlatilloAdapter` |
| Insets/edge-to-edge | ➖ N/A — el Fragment vive dentro de `MainActivity`, que ya los maneja |
| Accesibilidad | ✅ `contentDescription` en foto y ⋮; táctiles ≥ 48dp; la foto dice "Foto de \<platillo\>" |
| Pruebas con aserciones reales | ✅ 68 nuevas |
| Impacto en arranque | ✅ Glide no inicializa nada en `Application.onCreate()` |

Sin ❌. Los dos ⚠️ están registrados como deuda (P-011 preexistente, y el executor del
compresor queda explicado en esta nota).

## Lo que NO cambió

- **Cero SQL, cero migraciones, cero cambios en `supabase/`.** El servidor ya estaba listo
  de la sesión anterior y esta sesión no tenía acceso al conector.
- **Room, outbox y `SyncWorker`** — Fase 2b.
- `DatosMaqueta` fuera de Platillo/Categoría: pedidos, mesas, clientes y reportes siguen
  siendo maqueta.
- **P-009** (persistir/refrescar el token) sigue abierto: un `401` se reporta como *"Tu
  sesión venció"*, no se intenta refrescar.
- **P-002** (Hilt), **P-017** (feature-first) y **P-011** (IDs `camelCase`) siguen abiertos
  por decisión del plan.
- Los 3 pendientes de Fase 1 que solo puede cerrar el usuario. `feat/fase1-login` **no se
  mergea a `master`** todavía.

## Pendiente para el usuario

Probar el flujo completo en un emulador o dispositivo: subir una foto real, verla en la
lista, reemplazarla, quitarla, desactivar y reactivar un platillo, y crear/borrar una
categoría. **No hay Android SDK con adb ni dispositivo en el entorno del agente**, así que
esta parte queda fuera de su alcance por diseño del plan.

Durante la sesión se creó `local.properties` (ignorado por git) con `sdk.dir`,
`SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY` reales, así que el APK ya está en condiciones
de hablar con Supabase.

---

## Relaciones

- [[Plan Fase 2a - CRUD de Platillos y Categorias]] — el plan que esta sesión ejecuta
- [[Módulo Menú]] — la nota del módulo, escrita en esta sesión
- [[Sesión 2026-07-31 - Plan técnico de Fase 2a (CRUD de Menú) y preparación de Supabase]] — la otra mitad
- [[Plan Fase 1d - Modulo Empleados Funcional]] — el patrón replicado
- [[Arquitectura Actual]]
- [[Deuda Técnica - Pendientes]] — P-023, **P-024** (nuevo)
- [[Gate de Autoverificación]]
- [[Plan de Fase 2 - Menu]] — 2b y 2c siguen pendientes

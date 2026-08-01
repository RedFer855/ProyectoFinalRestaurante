---
title: Módulo Menú
tags:
  - restaurante
  - modulo
  - menu
  - fase2a
  - storage
date: 2026-07-31
---

# Módulo Menú

> [!success] Funcional desde 2026-07-31 (Fase 2a)
> CRUD real de platillos y categorías contra Supabase, con la foto del platillo en el
> bucket `platillos` de Storage. Reemplazó la maqueta de la Fase 1c. Falta **la prueba
> manual en un dispositivo**.

> [!info] Replica el patrón de Empleados, no el del login
> `ui/empleados/` + `SupabaseEmpleadoRepository` son el patrón vigente y aprobado
> ([[Plan Fase 1d - Modulo Empleados Funcional]]). El [[Módulo Login]] se escribió antes
> del [[Estándar de Ingeniería Android]] y arrastra deuda que no se replicó acá.

## Qué hace

| # | Historia | Rol |
|---|---|---|
| 1 | Ver el catálogo con foto, precio y categoría | admin, mesero, cocina |
| 2 | Filtrar por categoría y buscar por nombre o descripción | los tres |
| 3 | Crear un platillo, con foto opcional | solo admin |
| 4 | Editar un platillo, incluido cambiar o quitar la foto | solo admin |
| 5 | Desactivar / reactivar un platillo (nunca borrarlo) | solo admin |
| 6 | Crear, renombrar y desactivar categorías | solo admin |
| 7 | Borrar una categoría solo si no tiene platillos | solo admin |

Los tres roles usan **la misma pantalla**; lo que cambia es qué controles se muestran,
vía `Permisos` + `VistaPorPermiso`. Eso es experiencia de usuario, **no seguridad**: quien
impide que un mesero cambie un precio es la policy RLS de Postgres.

## Archivos clave

### `domain/`
```
model/Platillo.java        — id, nombre, descripción, precio, categoría, rutaImagen, activo
model/NuevoPlatillo.java   — lo que se manda al crear (sin id, sin estado, sin fecha)
model/Categoria.java       — incluye cantidadPlatillos y cantidadPlatillosActivos
model/ImagenPlatillo.java  — byte[] + mimeType; clona el arreglo, nada de android.graphics
ValidadorPlatillo.java     — nombre no vacío, precio > 0, categoría elegida → Set<ErrorPlatillo>
ReglasMenu.java            — espejo en el cliente de las reglas del servidor
repository/MenuRepository.java — el contrato: 10 métodos, la única cara que ve la UI
```

### `data/`
```
remote/SupabaseMenuApi.java     — PostgREST: lee de las vistas, escribe en las tablas
remote/SupabaseStorageApi.java  — subir y borrar en el bucket `platillos`
remote/dto/PlatilloDto.java · CategoriaDto.java
remote/dto/CrearPlatilloDto.java · ActualizarPlatilloDto.java
remote/dto/CrearCategoriaDto.java · ActualizarCategoriaDto.java
repository/SupabaseMenuRepository.java — implementa MenuRepository; orquesta base + Storage
```

### `ui/menu/`
```
MenuFragment.java             — observa el estado, chips de filtro, FAB, botón Categorías
PlatilloAdapter.java          — ListAdapter + DiffUtil; Glide; botones filtrados por permiso
MenuViewModel.java            — LiveData<EstadoMenu>; filtro y búsqueda viven acá
EstadoMenu.java               — estado único inmutable (cargando/datos/vacío/error)
MenuViewModelFactory.java     — DI manual (P-002)
FormularioPlatilloDialog.java — alta y edición en un solo diálogo; selector de fotos
CategoriasDialog.java         — crear, renombrar, activar/desactivar y borrar categorías
UrlDeImagen.java              — arma la URL pública desde la ruta guardada
CompresorDeImagen.java        — mide, rota por EXIF, redimensiona y comprime a JPEG
```

### Recursos
```
layout/fragment_menu.xml · item_platillo.xml · dialog_platillo.xml
layout/dialog_categorias.xml · item_categoria.xml
menu/menu_categoria.xml
drawable/ic_platillo_sin_foto.xml
```

## Contra qué habla — servidor

Aplicado y verificado el 2026-07-31, ver [[Esquema de Base de Datos]].

- Se **lee** de `vista_platillos` y `vista_categorias` (`security_invoker = on`), que ya
  resuelven el nombre de la categoría y los contadores.
- Se **escribe** en las tablas `platillo` y `categoria`. `SELECT` para cualquier rol con
  sesión; `INSERT`/`UPDATE`/`DELETE` solo para `admin`.
- Fotos en el bucket **`platillos`**: público de lectura, 2 MB, solo `image/jpeg|png|webp`,
  escritura restringida a `admin`. Sin policy de `SELECT` sobre `storage.objects`, así que
  *listar* el bucket está bloqueado aunque *leer una foto por su ruta* funcione.

### Reglas que impone el servidor y la app espeja

| Objeto del servidor | Espejo en el cliente |
|---|---|
| `CHECK (precio > 0)` | `ValidadorPlatillo` |
| `uq_platillo_nombre` sobre `lower(btrim(nombre))` | `ReglasMenu.existeOtroPlatilloLlamado` |
| `uq_categoria_descripcion` | `ReglasMenu.existeOtraCategoriaLlamada` |
| `trg_platillo_no_borrar` | el botón dice "Desactivar", nunca "Eliminar" |
| `trg_categoria_no_borrar_con_platillos` | `ReglasMenu.puedeBorrarse` oculta la opción |
| Límite de 2 MB y MIME del bucket | `ReglasMenu.cabeEnElBucket` / `tipoDeImagenPermitido` |
| `trg_*_actualizado_en` | la app **no manda** ese campo |

Que la app sea **más** estricta que el servidor es seguro; al revés es el problema.

## Las fotos — cómo funciona el circuito completo

1. **Elegir.** `ActivityResultContracts.PickVisualMedia` (el photo picker del sistema): no
   pide **ningún** permiso de almacenamiento. Se registra como **campo** del
   `DialogFragment` — hacerlo dentro del `onClick` lanza `IllegalStateException`.
2. **Leer.** El `Uri` es un `content://`: se abre con `ContentResolver.openInputStream()`.
   No se puede construir un `File` con él.
3. **Cocinar** (`CompresorDeImagen`, en un hilo de fondo): medir con `inJustDecodeBounds`,
   calcular `inSampleSize` para dejar el lado largo en ~1024 px, rotar según
   `ExifInterface.TAG_ORIENTATION` y comprimir a JPEG 80.
4. **Verificar** contra los 2 MB **antes** de subir: un 400 después de haber subido el
   archivo entero por 3G es la peor forma de enterarse.
5. **Subir** a `storage/v1/object/platillos/{uuid}.jpg`, y recién entonces tocar la fila.
6. **Mostrar**: `UrlDeImagen.urlDePlatillo(ruta)` arma la URL pública y Glide la carga.

> [!warning] `ruta_imagen` guarda la ruta, no la URL
> La fila guarda `a3f9….jpg`. Si guardara la URL completa, el día que cambie el proyecto,
> el dominio o el nombre del bucket **todas** las filas quedarían apuntando a la nada.

> [!tip] Cada reemplazo usa una ruta nueva
> Si se reusara la ruta, Glide seguiría sirviendo la foto vieja desde su caché y habría que
> invalidarla a mano. Con un UUID nuevo la URL cambia y el caché no miente.

### Orden de las operaciones, y qué pasa si algo se cae

| Operación | Orden | Si falla el paso 2 |
|---|---|---|
| Crear con foto | subir → insertar | El servidor rechaza el insert → **se borra el objeto subido** |
| Reemplazar foto | subir nueva → `PATCH` → borrar vieja | Se borra la nueva; la vieja queda intacta |
| Quitar foto | `PATCH` con `{"ruta_imagen":null}` → borrar archivo | El platillo ya se ve sin foto; el archivo queda huérfano (**P-023**) |

La compensación al crear **solo corre ante un rechazo explícito del servidor**. Con un
`IOException` no se sabe si el insert entró, y borrar la foto de un platillo que sí existe
deja una imagen rota y visible; el archivo huérfano es invisible.

## Detalles que muerden

- **`Prefer: return=representation`** en los `POST`: sin ese header PostgREST responde 201
  con cuerpo vacío y no se tiene el id generado. Devuelve un **array de un elemento**, no
  un objeto.
- **El filtro del `PATCH`/`DELETE` es parámetro obligatorio** de la interfaz Retrofit: un
  `PATCH` sin filtro en PostgREST actualiza **todas** las filas de la tabla.
- **Gson omite los nulos**, que es lo que hace posibles las actualizaciones parciales con
  factories — y lo que impide poner `ruta_imagen` en null. Ese único caso usa un
  `RequestBody` con el JSON literal; `serializeNulls()` global rompería todo lo demás.
- **El estado se deriva de `id_estado`**, no de la columna `activo`: esa solo existe en la
  vista, y el mismo DTO se usa para leer la respuesta de un `INSERT` sobre la tabla.
- **El interceptor de `SupabaseClient`** ya no fuerza `Content-Type: application/json` en
  toda petición. Si volviera a hacerlo, las subidas a Storage se romperían en silencio.
- **`precio` es `numeric`**, se mapea a `double` y se formatea siempre con
  `R.string.formato_lempiras`.
- **Un `401` es "Tu sesión venció"**, no un error de servidor: **P-009** sigue abierto, el
  token no se persiste ni se refresca.

## Pruebas

68 tests, con fakes manuales sobre `FakeCall` (el proyecto no usa Mockito a propósito).

| Suite | Cubre |
|---|---|
| `SupabaseMenuRepositoryTest` (20) | listar, crear, actualizar, quitar foto, categorías, mensajes de trigger y **la compensación del bucket** |
| `ReglasMenuTest` (16) | borrado de categorías, unicidad insensible, límites de imagen, inmutabilidad |
| `MenuViewModelTest` (14) | carga, vacío vs vacío-por-filtro, error, filtro, búsqueda, filtro que sobrevive a la recarga |
| `ValidadorPlatilloTest` (8) | nombre, precio, categoría |
| `SupabaseClientTest` (5) | las cabeceras del interceptor |

`CompresorDeImagen` **no tiene pruebas**: necesita `BitmapFactory`, que no existe en la JVM
de los unit tests. Registrado como **P-024**.

## Deuda de este módulo

| Ítem | Qué falta |
|---|---|
| 🟢 **P-024** | `CompresorDeImagen` sin pruebas (requiere Robolectric o test instrumentado) |
| 🟢 **P-023** | Nadie limpia los archivos huérfanos del bucket |
| 🔴 **P-014** | Sin offline-first: todo lee y escribe contra la red — **Fase 2b** |
| 🟢 **P-001** | `mensajeDeError()` duplicado entre este repositorio y el de Empleados |
| 🟢 **P-019** | Mensajes de error hardcodeados en el ViewModel y el repositorio |
| 🟢 **P-011** | IDs de vista en `snake_case` — bloqueado por **P-017** |
| 🟢 **P-009** | El token no se persiste ni se refresca |
| 🟢 **P-002** | DI manual por Factory |

Detalle completo en [[Deuda Técnica - Pendientes]].

---

## Rediseño de la tarjeta y el filtro obsoleto (2026-08-01)

**El bug que se veía como "no se guarda el cambio de categoría".** Editar un platillo y
moverlo de categoría **sí** funcionaba: el `PATCH` respondía `204` y la fila cambiaba. Lo
que fallaba era la pantalla — con un chip de categoría activo, el platillo recién guardado
dejaba de cumplir el filtro y **desaparecía de la lista**. Los logs de la API mostraban el
síntoma en crudo: tres `PATCH` seguidos al mismo `id_platillo`, o sea alguien reintentando
algo que ya se había guardado las tres veces.

`MenuViewModel.descartarFiltroQueEsconde(int)` suelta el filtro cuando dejaría fuera al
platillo que se acaba de crear o editar. Es el mismo criterio que `borrarCategoria` ya
aplicaba: **un filtro que esconde el resultado de la acción recién hecha es un filtro
obsoleto.**

> [!tip] La lección, que no es del Menú
> Una operación que el servidor acepta puede seguir siendo un fallo para el usuario si el
> resultado no queda a la vista. Todo módulo con filtros y con escritura tiene este
> problema latente — Pedidos y Mesas van a tenerlo igual.

**La tarjeta.** `item_platillo.xml` pasó de una fila con miniatura de 72 dp a una tarjeta
con la foto al 40 % del ancho y **al alto completo** (`layout_height=0dp` anclado arriba y
abajo, con `layout_constraintHeight_min`), nombre grande, precio en pastilla, categoría en
pastilla y la descripción a tres líneas.

| Decisión | Por qué |
|---|---|
| El precio va en una pastilla, no en un círculo | Un `<shape android:shape="oval">` recorta "L 1,250.00". Con radio grande + `minWidth` = `minHeight`, un precio corto se ve circular y uno largo se estira |
| Las acciones son botones a la vista; se eliminó `menu_platillo.xml` | Con dos opciones, un menú ⋮ cuesta un toque de más sin ganar nada |
| El botón dice "Desactivar" y su ícono es un círculo tachado, no un basurero | El diseño de referencia decía "Eliminar", pero `trg_platillo_no_borrar` rechaza el `DELETE`. Un basurero prometería una acción que el servidor niega |
| `AlElegirAccion` pasó de `onAccion(platillo, int accionId)` a dos métodos | El `int` era el id del `MenuItem` del `PopupMenu`. Sin menú, el compilador puede verificar que las dos acciones estén atendidas |

Colores solo por nombre de la paleta (`brand_primary_container`, `brand_secondary`…), así
que el modo oscuro sale de `values-night/colors.xml` sin trabajo extra.

---

## Relaciones

- [[Plan Fase 2a - CRUD de Platillos y Categorias]] — el plan que lo define
- [[Sesión 2026-07-31 - Fase 2a implementada (CRUD de Menú con fotos en Storage)]]
- [[Plan de Fase 2 - Menu]] — por qué 2a, 2b, 2c, 2d y 2e van separadas
- [[Plan Fase 1d - Modulo Empleados Funcional]] — el patrón replicado
- [[Módulo Login]] — el primer módulo, con la deuda que **no** se replicó
- [[Esquema de Base de Datos]]
- [[Arquitectura Actual]]
- [[Deuda Técnica - Pendientes]]
- [[Offline-First con Room y Outbox]] — lo que falta en 2b
- [[Repository Pattern]] · [[Result Pattern]] · [[MVVM en Android (ViewModel + LiveData)]]
- [[UiState Inmutable y Flujo Unidireccional]] · [[Accesibilidad Android]]
- [[Gate de Autoverificación]]

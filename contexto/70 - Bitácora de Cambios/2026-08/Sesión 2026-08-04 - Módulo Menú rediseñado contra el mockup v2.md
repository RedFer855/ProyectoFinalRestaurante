---
title: "Sesión 2026-08-04 — Módulo Menú rediseñado contra el mockup v2"
tags:
  - sesion
  - diseno
  - ui
  - menu
  - material3
date: 2026-08-04
branch: feat/fase2cd-mesas-clientes
autor_cambios: Claude (Sonnet 5 / Opus 5)
---

# Sesión 2026-08-04 — Módulo Menú rediseñado contra el mockup v2

## Qué se pidió

Implementar **todo el módulo Menú** con el diseño exacto de
`Restaurant App v2.dc.html` (Claude Design, proyecto
`3f58f5fc-cf82-4509-9b37-978984f85107`) — mismos tamaños, colores y tipografía —
investigando lo que hiciera falta de UI Android, con buenas prácticas y sin castigar
a los teléfonos de gama baja.

---

## Cómo se leyó el diseño

Con el MCP `DesignSync` (`get_file`), no con capturas. Importa porque los valores salen
del CSS literal y no de mirar una imagen: `#f7dfd4`, `13.5px`, `line-height:1.35`.

> [!warning] El primer intento falló y por qué
> `WebFetch` a la URL de `claude.ai/design` devuelve **403**: es contenido autenticado.
> `DesignSync.list_projects` tampoco lo mostraba, porque **solo lista proyectos de tipo
> design-system** y este es `PROJECT_TYPE_PROJECT`. La vía que sí funciona es ir directo
> con `get_project` / `list_files` / `get_file` **pasando el `projectId`** de la URL.

El archivo pesa ~98 KB en una sola línea JSON: se decodificó a HTML en el scratchpad para
poder recorrerlo por secciones.

---

## Lo que cambió entre la v1 y la v2 del mockup

La v1 se había implementado el 2026-08-01. La v2 traía cambios reales, no cosméticos:

| | v1 | v2 |
|---|---|---|
| Agregar platillo | Pastilla ancha con texto | **FAB circular de 56 dp** |
| Categorías | Al pie de la pantalla Menú | Movidas a una pantalla "Ajustes" |
| Descripción de la tarjeta | 2 líneas | **3 líneas**, interlineado 1.35 |
| Chip de categoría | `margin-top: 5` | `margin-top: 2` |

---

## Decisiones consultadas con el usuario

1. **Categorías** → se quedan accesibles desde Menú, restilizadas. Crear la pantalla
   "Ajustes" del mockup era tocar navegación, no diseño.
2. **Tipografías chicas vs. accesibilidad** → tamaños exactos del diseño (9.5/10.5 sp),
   pero la tarjeta usa `minHeight` en vez del alto fijo de 150 px: se ve idéntica por
   defecto y no recorta texto si el usuario sube la fuente del sistema.

---

## Qué se hizo

### Infraestructura visual nueva

- **`values/styles.xml`** (no existía) — 13 `TextAppearance.App.*` con los tamaños
  exactos del mockup, más los estilos de chip, hoja modal, botones y campos.
- **`res/color/`** (no existía) — los tres `ColorStateList` del chip de filtro.
- Tokens del diseño en `colors.xml` **y su contraparte en `values-night/colors.xml`**.
- Vectores calcados del SVG del mockup: `ic_buscar`, `ic_categoria_barras`, `ic_cerrar`,
  y versiones a 11 dp de los íconos de acción.

### Pantalla, tarjeta y hojas

- `fragment_menu.xml`: buscador pastilla, chips con colores por estado, **FAB circular**.
- `item_platillo.xml`: medidas exactas de la v2; acciones como `TextView` con
  `drawableStart`.
- `FormularioPlatilloDialog` y `CategoriasDialog`: `MaterialAlertDialogBuilder` →
  **`BottomSheetDialogFragment`**. Las interfaces de callback **no se tocaron**, así que
  `MenuFragment` y `MenuViewModel` quedaron intactos.
- `item_categoria.xml`: tarjetas blancas de radio 14 como el mockup.

### Rendimiento

- `Glide.override(150dp, 150dp)`: decodifica al tamaño del hueco en vez de traer el
  bitmap completo a memoria.
- Buscador de 3 vistas → 1 (`drawableStart`); acciones de tarjeta, 2 niveles menos.

---

## Bugs propios encontrados y corregidos en el camino

| Bug | Cómo se detectó | Arreglo |
|---|---|---|
| `brand_tarjeta_platillo` sin variante oscura | Revisión de `values-night/` | Se agregó la contraparte de cada token nuevo |
| `android:foreground` para el círculo de "cerrar" | Razonamiento antes de instalar | `foreground` dibuja **encima**: tapaba el ícono. Va en `background`; el ripple queda en `foreground` |
| `<!-- ----- Foto ----- -->` | **Build roto** | `--` es ilegal dentro de un comentario XML |
| Círculo de 32 dp vs. 48 dp tocables | Al escribir el layout | `<inset>` de 8 dp: figura de 32 dentro de un área de 48 |

El de `foreground` es el interesante: **compila sin error** y solo se ve mirando la
pantalla.

---

## Dos bugs que reportó el usuario al probarlo (y que los tests no veían)

Los dos son de la hoja del formulario de platillo y **ninguno rompía el build ni los
tests**: se veían usando la app. Buen recordatorio de que el inflado no es lo mismo que
el comportamiento.

### 1. La hoja abría a medias y tapaba los botones

Una `BottomSheetDialogFragment` **arranca en `STATE_COLLAPSED`**, mostrando solo la
altura de asomo. En un formulario largo eso deja "Cancelar / Guardar" abajo del borde de
la pantalla.

Arreglo, en la nueva base `ui/comun/HojaModal`:

```java
comportamiento.setState(BottomSheetBehavior.STATE_EXPANDED);
comportamiento.setSkipCollapsed(true);   // sin esto, arrastrar hacia abajo re-colapsa
comportamiento.setMaxHeight(85% del alto de pantalla);
```

`setSkipCollapsed(true)` no es adorno: sin él, arrastrar la hoja hacia abajo vuelve al
estado colapsado en vez de cerrarla, y el usuario se queda otra vez sin ver los botones.
El tope del 85% viene del mockup y además deja ver una franja del fondo, que es la pista
de que se puede tocar afuera para cerrar.

Se aprovechó para mover ahí el `getTheme()` y el `SOFT_INPUT_ADJUST_RESIZE` (la hoja vive
en su propia ventana, así que no hereda el `windowSoftInputMode` de la Activity).

### 2. El placeholder de la foto se veía como una franja horizontal

El ícono de "sin foto" es un vector **cuadrado de 24 dp**. El hueco de la foto mide
~355 × 130 dp. Con `scaleType="centerCrop"`, Android escalaba el ícono ~12× hasta cubrir
el ancho y mostraba solo una **banda del medio** — de ahí la franja.

`centerCrop` sirve para una foto real, no para un ícono. El hueco ahora arranca en
`CENTER_INSIDE` y el código pasa a `CENTER_CROP` recién cuando hay una foto de verdad
(`mostrarEstadoConFoto()` / `mostrarEstadoSinFoto()`).

De paso, el estado vacío dejó de parecer una imagen rota: caja de borde punteado, ícono
centrado, el aviso "Tocá para subir una foto" del mockup, y **el hueco entero abre el
selector**, no solo el botón.

Quedó cubierto por un test de regresión que afirma el `scaleType` inicial.

---

## Estado de verificación

- `:app:assembleDebug` — ✅ limpio
- `:app:testDebugUnitTest` — ✅ **356 tests, 0 fallas, 0 errores** (351 previos + 5 nuevos)
- Instalado en el emulador — ✅
- **Verificación visual — pendiente.** No hay forma de que un agente entre a la app: por
  política del proyecto ningún agente crea cuentas ni maneja contraseñas, así que el
  login lo tiene que hacer la persona. Queda como el último paso del
  [[Gate de Autoverificación]] para este cambio.

### `LayoutsDelMenuTest` — 5 tests nuevos

Se agregó un test de **inflado real** de los cinco layouts del módulo, bajo el tema de la
app y con Robolectric.

Existe porque compilar **no** prueba que un layout funcione: una referencia a un estilo
inexistente, un atributo que el widget no soporta o un drawable mal armado pasan el build
y revientan al inflar, ya con la app instalada. Este rediseño estrenó `values/styles.xml`
y `res/color/` enteros, o sea mucha referencia nueva que un refactor puede dejar colgada.

Además verifica que sigan existiendo los `id` que `PlatilloAdapter` busca por
`findViewById`: si un rediseño futuro renombra uno, hoy falla el test en vez de tirar un
`NullPointerException` en producción.

> Detalle de implementación: usa `RuntimeEnvironment.getApplication()` y no
> `ApplicationProvider`, porque `androidx.test.core` no está entre las dependencias de
> test del proyecto — es la vía que ya usaban los tests de DAO.

---

## Conocimiento destilado

Se creó [[Traducir un Diseño Web a Views de Android]] en `50 - Referencia`: unidades,
tipografía escalable, el truco de área tocable ≠ figura visible, `ColorStateList`,
aplanar con `drawableStart`, hojas modales y las trampas que compilan pero se ven mal.

Lo más útil de lo investigado: **desde Material Components 1.8.0-alpha2 existe
`shouldRemoveExpandedCorners`, y vale `false` por defecto en los estilos M3** — el viejo
hack de reaplicar un `MaterialShapeDrawable` en el `BottomSheetCallback` para que la hoja
no pierda las esquinas al expandirse **ya no hace falta**. El proyecto usa Material 1.14.

---

## Relaciones

- [[Traducir un Diseño Web a Views de Android]] — el conocimiento que salió de acá
- [[Módulo Menú]] — actualizado con el rediseño
- [[Guía de Diseño Visual]] — actualizada con la regla de tipografía y accesibilidad
- [[Sesión 2026-08-01 - Rediseño de la tarjeta de platillo y filtro que escondía lo guardado]] — el rediseño anterior, contra la v1
- [[Presupuestos de Rendimiento en Gama Baja]]
- [[Accesibilidad Android]]
- [[Gate de Autoverificación]]

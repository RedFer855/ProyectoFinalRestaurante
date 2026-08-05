---
title: "Traducir un Diseño Web a Views de Android"
tags:
  - referencia
  - diseno
  - android
  - ui
  - material3
  - accesibilidad
date: 2026-08-04
lifecycle: verified
---

# Traducir un Diseño Web a Views de Android

> [!abstract] Principio
> Un mockup HTML/CSS no se "copia": se **traduce**. El CSS asume un modelo de caja, píxeles fijos y un solo usuario; Android asume densidades variables, fuente escalable del sistema y un mínimo tocable. Traducir literal produce una pantalla que se ve igual en el emulador y se rompe en el teléfono de un usuario real.

Recetas verificadas al implementar el módulo Menú desde `Restaurant App v2.dc.html` (Claude Design) — ver [[Módulo Menú]] y [[Guía de Diseño Visual]].

---

## 1. La unidad: cuándo `px` del mockup = `dp`

Si el lienzo del diseño mide **~360–420 px de ancho**, está hecho en puntos lógicos de teléfono: **1 px del mockup = 1 dp**, sin conversión. (El mockup de este proyecto usa 402 px, el ancho lógico de un iPhone moderno.)

Si el lienzo midiera 1440 px sería un diseño de escritorio y habría que reescalar. **Mirá el ancho del lienzo antes de copiar un solo número.**

---

## 2. Tamaños de texto: `sp` con nombre, nunca `dp` ni literal

El CSS especifica tamaños al medio punto (`13.5px`, `9.5px`). La escala Material del tema no los tiene, así que `?attr/textAppearanceBodySmall` **no** reproduce el diseño.

La salida correcta no es hardcodear `android:textSize` en el layout, sino **definir estilos `TextAppearance` propios**:

```xml
<style name="TextAppearance.App.NombrePlatillo" parent="TextAppearance.Material3.TitleSmall">
    <item name="android:textSize">13.5sp</item>
    <item name="android:textStyle">bold</item>
</style>
```

Tres razones, en orden de importancia:

1. **`sp`, no `dp`** — el texto escala cuando el usuario sube la fuente del sistema. Un diseño exacto que se vuelve ilegible al 200 % no es exacto, es frágil.
2. **Con nombre** — subir el piso tipográfico después es una edición por estilo, no una cacería por todos los layouts.
3. Android acepta decimales en `sp` (`13.5sp` es válido).

> [!warning] El corolario: alto fijo + texto escalable = recorte
> Si el mockup dice `height:150px`, usá **`minHeight`**, no `layout_height`. Con alto fijo, subir la fuente del sistema recorta el texto en vez de agrandar la tarjeta.

**Interlineado:** `line-height:1.35` del CSS → `android:lineSpacingMultiplier="1.35"` (disponible desde API 1, a diferencia de `android:lineHeight`, que es API 28+).

---

## 3. El choque diseño-vs-accesibilidad y cómo NO ceder ninguno

El conflicto recurrente: el diseño dibuja controles chicos, la accesibilidad exige **48 dp tocables** (ver [[Accesibilidad Android]]). Casi siempre se puede cumplir con los dos, porque **el área tocable y la figura visible son cosas distintas**.

| Caso | Solución | Resultado |
|---|---|---|
| Chip de 36 dp | `app:ensureMinTouchTargetSize="true"` | Se dibuja de 36, se toca de 48 |
| Botón circular de 32 dp | `<inset>` de 8 dp dentro de una vista de 48 dp | Círculo de 32 visible, 48 tocables |
| Acción de texto chica | `minHeight="48dp"` + `wrap_content` visual | El texto no crece, el toque sí |

```xml
<!-- 48 − 8 − 8 = 32 dp de círculo visible dentro de 48 dp tocables -->
<inset android:insetLeft="8dp" android:insetTop="8dp"
       android:insetRight="8dp" android:insetBottom="8dp">
    <shape android:shape="oval">
        <solid android:color="@color/brand_primary_container" />
    </shape>
</inset>
```

---

## 4. Estados CSS → `ColorStateList`, no código

Un `background: active ? '#a8452d' : '#fff'` del mockup **no** se traduce a un `if` en el adapter. Se traduce a un selector en `res/color/`:

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/brand_primary" android:state_checked="true" />
    <item android:color="@color/brand_surface_card" />
</selector>
```

> [!bug] Un `Chip` creado con `new Chip(context)` ignora los estilos
> El constructor no aplica `style`. Para que tome `Widget.App.Chip.Filtro` hay que **inflarlo** desde un layout de una sola etiqueta:
> ```java
> Chip chip = (Chip) LayoutInflater.from(ctx)
>         .inflate(R.layout.item_chip_filtro, grupo, false);
> ```

---

## 5. Aplanar la jerarquía: `drawableStart` en vez de contenedores

El CSS resuelve "icono + texto" con un `flex` de dos hijos. En Android eso serían tres vistas; con un drawable compuesto es **una**:

```xml
<EditText
    android:background="@drawable/bg_busqueda"
    app:drawableStartCompat="@drawable/ic_buscar"
    android:drawablePadding="10dp" />
```

Vale para buscadores, chips con icono y acciones de tarjeta. En un `RecyclerView` es donde más se paga (ver [[Presupuestos de Rendimiento en Gama Baja]]).

Dos detalles:

- **El tamaño lo fija el vector**, no el layout: un drawable compuesto se dibuja a su tamaño intrínseco. Si el diseño pide un ícono de 11 dp, hay que crear el vector a 11 dp; no alcanza con reusar el de 24.
- **Cambiar el drawable por código descarta el tinte** de `app:drawableTint`. Hay que reaplicarlo:
  ```java
  vista.setCompoundDrawablesRelativeWithIntrinsicBounds(icono, 0, 0, 0);
  TextViewCompat.setCompoundDrawableTintList(vista, colorStateList);
  ```

---

## 6. Hojas modales (`BottomSheetDialogFragment`)

Un modal `align-items:flex-end` del mockup es una hoja modal inferior.

> [!success] Las esquinas redondeadas ya no necesitan hack
> Es un bug clásico: las esquinas se pierden al expandir la hoja. **Desde Material Components 1.8.0-alpha2 existe `shouldRemoveExpandedCorners`, y en los estilos M3 vale `false` por defecto** — heredando de `Widget.Material3.BottomSheet.Modal` las esquinas sobreviven solas. Ya no hace falta reaplicar un `MaterialShapeDrawable` en el `BottomSheetCallback`.

```xml
<style name="Widget.App.HojaModal" parent="Widget.Material3.BottomSheet.Modal">
    <item name="shapeAppearanceOverlay">@style/ShapeAppearance.App.HojaModal</item>
    <item name="backgroundTint">@color/brand_surface_card</item>
</style>
<style name="Theme.App.HojaModal" parent="Theme.Material3.DayNight.BottomSheetDialog">
    <item name="bottomSheetStyle">@style/Widget.App.HojaModal</item>
</style>
```

Se activa sobrescribiendo `getTheme()` en el fragment. Convertir un `DialogFragment` en hoja es: `extends BottomSheetDialogFragment`, `onCreateDialog` → `onCreateView` + `onViewCreated`, y **mover título y botones al layout** (ya no los pone `MaterialAlertDialogBuilder`).

**El contenido va en un `NestedScrollView`**: la hoja se limita al 85 % del alto y, con el teclado abierto, sin scroll los últimos campos quedan fuera de alcance. Si adentro hay un `RecyclerView`, marcalo `android:nestedScrollingEnabled="false"` o los dos scrolls se pelean el gesto.

> [!danger] Una hoja modal **abre a medias** salvo que le digas lo contrario
> Arranca en `STATE_COLLAPSED`, mostrando solo la altura de asomo: en un formulario largo, los botones del pie quedan fuera de la pantalla y parece que "no sube lo suficiente". Hay que pedirlo explícitamente en `onStart()`:
> ```java
> BottomSheetBehavior<FrameLayout> b = ((BottomSheetDialog) getDialog()).getBehavior();
> b.setState(BottomSheetBehavior.STATE_EXPANDED);
> b.setSkipCollapsed(true);              // sin esto, arrastrar abajo re-colapsa en vez de cerrar
> b.setMaxHeight(alto * 0.85f);          // el 85% del mockup
> ```
> `getBehavior()` devuelve `BottomSheetBehavior<FrameLayout>`, no `<View>`.
>
> La hoja vive en **su propia ventana**: no hereda el `windowSoftInputMode` de la Activity, así que para que el teclado no tape los campos hay que ponerle `SOFT_INPUT_ADJUST_RESIZE` a `getDialog().getWindow()`.

---

## 6 bis. `centerCrop` es para fotos, no para íconos

Un hueco de imagen ancho y bajo (por ejemplo 355 × 130 dp) con un **placeholder vectorial cuadrado** y `scaleType="centerCrop"` produce un resultado desconcertante: el ícono se escala hasta cubrir el ancho —12× para uno de 24 dp— y solo se ve **una banda horizontal del medio**. Parece una imagen rota.

La regla: **el `scaleType` depende del contenido, no del hueco.**

| Contenido | `scaleType` |
|---|---|
| Foto real | `centerCrop` (llenar y recortar es lo correcto) |
| Ícono de placeholder | `centerInside` (respetar proporción) |

O sea que el `scaleType` se **alterna en código** al cargar o limpiar la imagen, no se fija una vez en el XML. Conviene encapsularlo en dos métodos (`mostrarEstadoConFoto()` / `mostrarEstadoSinFoto()`) que además muevan el fondo, el padding y el aviso.

---

## 7. Etiqueta encima del campo ≠ `TextInputLayout`

Si el mockup pone la etiqueta **arriba** del campo (no flotando dentro), `TextInputLayout` es la traducción equivocada: trae la etiqueta flotante de Material, que es justo lo que el diseño no tiene. Lo correcto es un `EditText` con `background` propio y un `TextView` de etiqueta encima.

Lo mismo con un buscador tipo pastilla rellena: `TextInputLayout.OutlinedBox` dibuja un contorno con muesca que el diseño no pide.

---

## 8. Trampas que cuestan una compilación (o peor, no la cuestan)

> [!danger] `android:foreground` dibuja ENCIMA del contenido
> Poner el círculo de fondo de un `ImageButton` en `foreground` **tapa el ícono**. El fondo va en `android:background`; `foreground` queda para el ripple (`?attr/selectableItemBackgroundBorderless`). Esto **compila sin error** y se descubre mirando la pantalla.

> [!danger] `--` es ilegal dentro de un comentario XML
> Un separador decorativo `<!-- ----- Foto ----- -->` **rompe el build** con "La cadena `--` no está permitida en los comentarios". Usá `=====`.

**Colores con alfa:** el CSS pone el alfa al final (`rgba(36,28,25,0.08)`), Android lo pone **primero** y en hex: `0.08 × 255 = 20 = 0x14` → `#14241C19`.

**`android:tint` en el vector vs. tinte desde el layout:** dejá el vector en negro sólido y tiñelo con `app:tint` / `app:drawableTint`. Así un solo archivo sirve en claro y en oscuro.

---

## 9. Modo oscuro: el mockup no lo tiene, la app sí

Un mockup en claro **no** exime de `values-night/`. Todo token nuevo necesita su contraparte oscura, y no es el mismo hex: se reinterpreta manteniendo la *relación* (la tarjeta un escalón por encima del fondo, los bordes apenas visibles). Un separador que oscurece en claro debe **aclarar** en oscuro.

Olvidarlo no rompe el build: deja un color claro chillón sobre fondo oscuro.

---

## Checklist de traducción

- [ ] ¿El ancho del lienzo confirma que 1 px = 1 dp?
- [ ] ¿Los tamaños de texto son estilos con nombre, en `sp`?
- [ ] ¿Ningún alto es fijo donde hay texto?
- [ ] ¿Todo lo tocable llega a 48 dp (aunque se vea más chico)?
- [ ] ¿Los estados son `ColorStateList` y no `if` en el adapter?
- [ ] ¿Cada token nuevo tiene su variante en `values-night/`?
- [ ] ¿Las hojas modales abren **expandidas** y con `skipCollapsed`?
- [ ] ¿El `scaleType` corresponde al contenido (foto vs. ícono), no al hueco?
- [ ] ¿Se **usó** la pantalla, no solo se miró que compile? (`foreground` tapa, una hoja abre a medias — nada de eso rompe el build ni los tests de inflado)

---

## Relaciones

- [[Guía de Diseño Visual]] — el contrato visual que estas recetas implementan
- [[Accesibilidad Android]] — de dónde sale el mínimo de 48 dp
- [[Presupuestos de Rendimiento en Gama Baja]] — por qué importa aplanar jerarquías
- [[Módulo Menú]] — primer módulo traducido con este método
- [[Estándar de Ingeniería Android]]

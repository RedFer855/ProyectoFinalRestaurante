---
title: Guía de Diseño Visual — App Restaurante
tags:
  - restaurante
  - diseno
  - ui
  - material3
date: 2026-07-31
lifecycle: draft
---

# Guía de Diseño Visual — App Restaurante

> [!info] Para qué sirve
> Contrato visual del proyecto: paleta, tipografía, componentes y reglas. Su razón de ser es que **todas las pantallas de todas las fases se vean como una sola app**, y que se vean bien **en gama baja** — ver [[Presupuestos de Rendimiento en Gama Baja]].

---

## Principio rector: "barato de renderizar, caro de ver"

La app corre en teléfonos de gama baja con red intermitente. Todo lo que se ve bonito **sin costar recursos** se usa; todo lo que cuesta recursos se descarta.

| ✅ Se usa (gratis o casi) | ❌ Se evita (caro) |
|---|---|
| Color plano y jerarquía tipográfica | Degradados, `DropShadowEffect` pesados |
| Esquinas redondeadas (`shapeAppearance`) | Imágenes de fondo, ilustraciones grandes |
| Espacio en blanco generoso | Animaciones custom, Lottie |
| Iconos **vectoriales** (`VectorDrawable`) | PNG/JPG en múltiples densidades |
| Elevación 0-2 dp | Elevación alta + sombras superpuestas |
| Estados vacíos con texto + icono | Splash screens animados |

Lección de Bimbo aplicable: ahí hubo que **quitar** `DropShadowEffect` de los modales por rendimiento (`Sesión 2026-06-25`). Acá se evita desde el día uno.

---

## Paleta — "Terracota Cálido"

Material 3 con acento cálido: evoca comida y hospitalidad sin ser estridente. Un solo color de acento + neutros; el color se usa **para señalar**, no para decorar.

> [!success] Valores oficiales — extraídos del diseño aprobado (2026-07-31)
> Estos hex **no son una propuesta**: salen del archivo `Restaurant App v2.dc.html` del proyecto de Claude Design (`3f58f5fc-cf82-4509-9b37-978984f85107`), leído directamente. La versión anterior de esta nota tenía valores tentativos ligeramente distintos (`#9C4221` en vez de `#a8452d`); se reemplazaron para que exista **una sola fuente de verdad**.

### Claro (`values/colors.xml`)

```xml
<color name="brand_primary">#a8452d</color>          <!-- terracota: botones, acentos, activo -->
<color name="brand_on_primary">#FFFFFF</color>
<color name="brand_primary_container">#f6dcce</color><!-- ítem seleccionado, íconos de tarjeta -->
<color name="brand_on_primary_container">#6b4a3a</color>
<color name="brand_secondary">#8a5a45</color>        <!-- chips, elementos de apoyo -->
<color name="brand_surface">#f7ece7</color>          <!-- fondo de pantalla -->
<color name="brand_surface_card">#FFFFFF</color>     <!-- tarjetas, hojas, modales -->
<color name="brand_surface_field">#fbf3ee</color>    <!-- fondo de campos de texto -->
<color name="brand_on_surface">#241c19</color>       <!-- texto principal -->
<color name="brand_on_surface_variant">#8a7a74</color><!-- texto secundario, placeholders -->
<color name="brand_outline">#f0dccf</color>          <!-- bordes de campos y tarjetas -->
<color name="brand_error">#9a4a42</color>
<color name="brand_success">#4f8a5b</color>          <!-- estado "listo" / mesa libre -->
```

### Oscuro (`values-night/colors.xml`)

```xml
<color name="brand_primary">#FFB59B</color>
<color name="brand_on_primary">#5A1B00</color>
<color name="brand_primary_container">#7D2C0B</color>
<color name="brand_on_primary_container">#FFDBCF</color>
<color name="brand_secondary">#E7BDB0</color>
<color name="brand_surface">#201A18</color>
<color name="brand_surface_variant">#53433E</color>
<color name="brand_on_surface">#EDE0DC</color>
<color name="brand_on_surface_variant">#D8C2BB</color>
<color name="brand_outline">#A08C86</color>
<color name="brand_error">#FFB4AB</color>
```

### Colores de estado (pedidos y mesas)

Semánticos, no decorativos. **Nunca comunicar estado solo con color** — siempre color + texto/icono (ver [[Accesibilidad Android]]):

| Estado | Color | Uso |
|---|---|---|
| Pendiente | `#B26A00` ámbar | Pedido recién tomado |
| En preparación | `#0B57D0` azul | Cocina trabajando |
| Listo / Libre | `#1B6C3A` verde | Listo para entregar / mesa libre |
| Entregado | `#53433E` neutro | Cerrado, sin acción |
| Cancelado / Ocupada | `#BA1A1A` rojo | Requiere atención |

---

## Tipografía

**Solo la fuente del sistema** (Roboto). Cero fuentes custom: cada `.ttf` son cientos de KB en el APK y tiempo de carga en gama baja. La jerarquía se logra con tamaño y peso — nunca `sp` hardcodeado dentro de un layout.

Escala base del tema, para pantallas sin diseño específico:

| Uso | Atributo |
|---|---|
| Título de pantalla | `?attr/textAppearanceHeadlineSmall` |
| Título de sección / card | `?attr/textAppearanceTitleMedium` |
| Cuerpo | `?attr/textAppearanceBodyMedium` |
| Etiquetas, chips, captions | `?attr/textAppearanceLabelMedium` |

> [!info] Cuando el diseño pide tamaños que la escala Material no tiene (desde 2026-08-04)
> El mockup aprobado especifica tamaños al medio punto (`13.5`, `10.5`, `9.5`) que ningún `?attr/textAppearance*` reproduce. La salida **no** es hardcodear `android:textSize` en el layout, sino declarar estilos propios en **`values/styles.xml`**:
> ```xml
> <style name="TextAppearance.App.NombrePlatillo" parent="TextAppearance.Material3.TitleSmall">
>     <item name="android:textSize">13.5sp</item>
> </style>
> ```
> Siguen en `sp` (escalan con la fuente del sistema) y siguen teniendo nombre (subir el piso tipográfico es una edición por estilo). El catálogo `TextAppearance.App.*` vive en `values/styles.xml`; ver [[Traducir un Diseño Web a Views de Android]].
>
> **Corolario:** si el diseño fija un alto en píxeles para un bloque con texto, en Android va como `minHeight`. Con alto fijo, subir la fuente recorta en vez de crecer.

---

## Espaciado y forma

Ya existe en `values/dimens.xml` (escala de 8 dp):

| Token | Valor | Uso |
|---|---|---|
| `espaciado_minimo` | 8 dp | Entre elementos relacionados |
| `espaciado_campo` | 16 dp | Entre campos de formulario |
| `espaciado_pantalla` | 24 dp | Margen lateral de pantalla |
| `espaciado_seccion` | 32 dp | Entre bloques distintos |
| `altura_minima_tactil` | 48 dp | **Mínimo de cualquier elemento tocable** |

**Radio de esquinas:** 12 dp en tarjetas y campos, 24 dp (o `fullyRounded`) en botones. Consistente en toda la app.

---

## Componentes canónicos

| Necesito… | Uso |
|---|---|
| Campo de texto | `TextInputLayout` estilo `OutlinedBox` + `TextInputEditText` |
| Acción principal | `MaterialButton` (filled), ancho completo, `minHeight` 48 dp |
| Acción secundaria | `MaterialButton` estilo `OutlinedButton` o `TextButton` |
| Item de lista | `MaterialCardView`, elevación 0-1 dp, borde `brand_outline` |
| Lista | `RecyclerView` + `ListAdapter`/`DiffUtil` — nunca un `ScrollView` con N hijos |
| Etiqueta de estado | `Chip` con color semántico + texto |
| Mensaje temporal | `Snackbar` (nunca `Toast` — no es accesible ni descartable) |
| Carga | `CircularProgressIndicator` centrado |

---

## Reglas de estado de pantalla

Toda pantalla que cargue datos maneja **cuatro** estados, no dos. Es la lección de [[UiState Inmutable y Flujo Unidireccional]] llevada al diseño:

1. **Cargando** — indicador centrado (o *skeleton* si la lista es larga)
2. **Con datos** — el contenido
3. **Vacío** — icono grande tenue + texto que explique + acción sugerida ("Todavía no hay pedidos · Crear pedido")
4. **Error** — mensaje corto en lenguaje humano + botón "Reintentar". **Nunca** el mensaje crudo de la excepción

---

## Accesibilidad — mínimos no negociables

- Contraste de texto ≥ 4.5:1 (la paleta de arriba ya lo cumple)
- Todo tocable ≥ 48×48 dp — **el área tocable y la figura visible son cosas distintas**: si el diseño dibuja un control más chico, se cumplen las dos (`app:ensureMinTouchTargetSize` en chips, `<inset>` en botones circulares). Recetas en [[Traducir un Diseño Web a Views de Android]]
- `contentDescription` en todo icono que comunique algo (los decorativos: `null`)
- Errores con `accessibilityLiveRegion="polite"` para que TalkBack los anuncie
- La app debe seguir siendo usable con **fuente al 200 %** — de ahí que nada tenga altura fija en `dp`

Ver [[Accesibilidad Android]].

---

## El prompt de diseño (para generar pantallas)

El prompt completo y detallado, pantalla por pantalla, está en la nota de sesión [[Sesión 2026-07-31 - Plan de Fase 1 y guía de diseño]]. Resumen de las pantallas de la Fase 1:

1. Login (existe, ajustar a esta paleta)
2. Recuperar contraseña — pedir correo
3. Recuperar contraseña — código + nueva contraseña
4. Home con menú hamburguesa filtrado por rol
5. (Opcional) Splash de decisión de sesión

---

## Relaciones

- [[Traducir un Diseño Web a Views de Android]] — cómo se implementa este contrato desde el mockup
- [[Plan de Fase 1 - Roles, Autenticación y Recuperación]]
- [[Módulo Login]]
- [[Accesibilidad Android]]
- [[Presupuestos de Rendimiento en Gama Baja]]
- [[UiState Inmutable y Flujo Unidireccional]]
- [[Estándar de Ingeniería Android]]

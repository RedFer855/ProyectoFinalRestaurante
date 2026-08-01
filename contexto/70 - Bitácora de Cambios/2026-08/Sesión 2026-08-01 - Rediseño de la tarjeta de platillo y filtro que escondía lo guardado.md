---
title: "Sesión 2026-08-01 — Rediseño de la tarjeta de platillo y filtro que escondía lo guardado"
tags:
  - sesion
  - restaurante
  - fase2a
  - menu
  - ui
date: 2026-08-01
branch: feat/fase2-menu
autor_cambios: Claude Code
---

# Sesión 2026-08-01 — Rediseño de la tarjeta de platillo y filtro que escondía lo guardado

> [!success] Resultado
> Se diagnosticó y corrigió el reporte de "no se puede cambiar un platillo de categoría"
> —que no era un problema de Supabase— y se rediseñó `item_platillo.xml` según una
> referencia visual del usuario. **127 tests en verde** (eran 124) y `assembleDebug` limpio.

---

## Problema / motivo

Dos pedidos del usuario en el mismo mensaje: *"algo falla a la hora de editar un producto
y cambiarlo de categoría, puede que falte la función que haga eso en Supabase"* y
*"agrandá la imagen al ancho del item"*, con una imagen de referencia de la tarjeta.

## Diagnóstico: no era Supabase

La hipótesis del usuario era que faltaba algo del lado del servidor. **No era así**, y se
descartó con evidencia antes de tocar código:

1. **`UPDATE` directo en una transacción revertida**, simulando al admin: cambiar
   `id_categoria` afectó 1 fila y `vista_platillos` devolvió la categoría nueva.
2. **Logs de la API**: los `PATCH /rest/v1/platillo?id_platillo=eq.1` respondieron **204**.
3. **Estado real de los datos**: "Baleada sencilla" estaba en `Desayunos` — el cambio
   había entrado.

Los logs además mostraban el síntoma en crudo: **tres `PATCH` seguidos al mismo
`id_platillo`**, separados por ~20 y ~30 segundos. Nadie repite tres veces algo que ve
funcionar.

La causa era de UI: con un chip de categoría activo, el platillo recién guardado dejaba de
cumplir el filtro y **desaparecía de la lista**. El servidor guardaba, el Snackbar decía
"Platillo actualizado", y el ítem se esfumaba de la pantalla.

> [!tip] La lección, que no es del Menú
> **Una operación que el servidor acepta puede seguir siendo un fallo para el usuario si el
> resultado no queda a la vista.** Cualquier módulo con filtros y con escritura tiene este
> problema latente; Pedidos y Mesas lo van a tener igual.

## Cambios aplicados

**`MenuViewModel`** — nuevo `descartarFiltroQueEsconde(int)`: tras crear o actualizar un
platillo, si el filtro por categoría vigente lo dejaría fuera, se suelta el filtro. Es el
mismo criterio que `borrarCategoria` ya aplicaba cuando el filtro apuntaba a una categoría
borrada: un filtro que esconde el resultado de la acción recién hecha es un filtro obsoleto.

`cambiarEstadoPlatillo` **no** se tocó: desactivar no mueve el platillo de categoría, así
que el filtro sigue siendo válido y el test `elFiltroSobreviveAUnaRecarga` lo comprueba.

**`item_platillo.xml`** — reescrito de `LinearLayout` a `ConstraintLayout`: foto al 40 %
del ancho y al alto completo de la tarjeta (`layout_height=0dp` anclado arriba y abajo, con
`layout_constraintHeight_min` para el piso), nombre grande, precio en pastilla, categoría en
pastilla, descripción a tres líneas y las acciones abajo a la derecha.

**`PlatilloAdapter`** — el menú ⋮ pasó a dos botones a la vista. `AlElegirAccion` cambió de
`onAccion(platillo, int accionId)` a `onEditarPlatillo` / `onAlternarEstadoPlatillo`: el
`int` era el id del `MenuItem` del `PopupMenu`, y sin menú no tenía razón de existir.
`MenuFragment` ahora implementa la interfaz en vez de pasar un método por referencia.

**Recursos nuevos:** `bg_precio_platillo.xml`, `bg_categoria_platillo.xml`, `ic_editar.xml`,
`ic_desactivar.xml`, `ic_reactivar.xml`, y cuatro dimens. Se eliminó `menu/menu_platillo.xml`.

## Decisiones de diseño

**El precio va en una pastilla, no en un círculo.** El diseño de referencia lo muestra
circular, pero un `<shape android:shape="oval">` recorta "L 1,250.00". Con radio grande y
`minWidth` igual al `minHeight`, un precio corto se ve circular y uno largo se estira.

**El botón dice "Desactivar" y su ícono es un círculo tachado, no un basurero.** La imagen
de referencia decía "Eliminar". No se copió: `trg_platillo_no_borrar` rechaza el `DELETE` de
un platillo, así que un basurero prometería una acción que el servidor niega. Se avisó al
usuario en la respuesta.

**Los colores van solo por nombre de la paleta** (`brand_primary_container`,
`brand_secondary`, `brand_on_surface`…), así el modo oscuro sale de
`values-night/colors.xml` sin trabajo extra.

## Verificación

```bash
./gradlew testDebugUnitTest assembleDebug
```

Ambos **BUILD SUCCESSFUL**. **127 tests, 0 fallos** (124 antes). Los 3 nuevos, en
`MenuViewModelTest`, cubren el bug del filtro: se suelta al mover el platillo de categoría,
se conserva cuando el platillo sigue en la categoría filtrada, y se suelta también al crear
un platillo en otra categoría.

> [!warning] No verificado en pantalla
> El layout compila y `aapt2` lo acepta, pero **nadie lo vio corriendo**: no hay emulador ni
> dispositivo en este entorno. La cadena vertical del `ConstraintLayout` con
> `txt_estado_platillo` en `gone` es lo que más conviene mirar en el primer arranque.

## Lo que NO cambió

- **Cero SQL, cero migraciones.** El servidor estaba bien; se consultó para descartarlo.
- Room / offline-first (**P-014**), **P-011**, **P-017**, **P-009**, **P-002**: siguen abiertos.
- `dialog_platillo.xml` y `CategoriasDialog` no se tocaron.

---

## Relaciones

- [[Módulo Menú]] — el estado vivo del módulo
- [[Sesión 2026-07-31 - Fase 2a implementada (CRUD de Menú con fotos en Storage)]]
- [[Plan Fase 2a - CRUD de Platillos y Categorias]]
- [[Guía de Diseño Visual]] — la paleta que usa la tarjeta
- [[Accesibilidad Android]]
- [[Deuda Técnica - Pendientes]]
- [[UiState Inmutable y Flujo Unidireccional]]

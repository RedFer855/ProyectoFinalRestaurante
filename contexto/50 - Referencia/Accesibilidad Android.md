---
title: "Accesibilidad Android"
tags:
  - referencia
  - accesibilidad
  - ui
date: 2026-07-29
lifecycle: verified
---

# Accesibilidad Android

> [!abstract] Principio
> La accesibilidad es **deuda técnica carísima de retro-añadir**. Cuesta minutos hacerlo bien la primera vez y semanas repararlo en 30 pantallas.

## Reglas obligatorias en cada pantalla nueva

| Regla | Cómo se cumple |
|---|---|
| **`contentDescription`** en todo elemento no textual | Icono con significado → describe la acción (`"Agregar producto al pedido"`). Si es decorativo → `android:importantForAccessibility="no"` |
| **Objetivos táctiles ≥ 48dp** | `minWidth`/`minHeight` o padding; un icono de 24dp necesita 12dp de padding a cada lado |
| **Contraste ≥ 4.5:1** | Texto sobre fondo; verificar con el Accessibility Scanner |
| **Etiquetas en formularios** | `android:labelFor` apuntando al campo, o `TextInputLayout` con `android:hint` |
| **Nunca solo color** para transmitir información | El estado "pedido pendiente" lleva icono o texto, no solo el color rojo |
| **Texto escalable hasta 200%** | `sp` para texto, `dp` para todo lo demás. Layouts que no truncan al aumentar la fuente |
| **RTL** | `start`/`end`, **nunca** `left`/`right` |
| **Navegación por teclado y TalkBack** | Verificada manualmente en cada pantalla nueva |
| **Modo oscuro** | Desde el día 1, con `values-night/` |

## Verificación

- **Accessibility Scanner** (app de Google Play) sobre cada pantalla nueva.
- TalkBack activado: recorrer el flujo completo sin mirar la pantalla.
- Aumentar el tamaño de fuente del sistema al máximo y confirmar que nada se corta.

> [!note] Estado en este proyecto
> La pantalla de login **no tiene `contentDescription` ni `labelFor`**, y los `EditText` usan `android:hint` suelto en vez de `TextInputLayout`. Registrado como **P-010** en [[Deuda Técnica - Pendientes]].
>
> Sí se cumple ya: textos en `strings.xml` (nada hardcodeado), `sp` para texto, tema con `values-night/`.

---

## Relaciones

- [[Convenciones Java]] — recursos y strings
- [[Android 16 y 17 - Cambios de Comportamiento]] — edge-to-edge afecta el área táctil real
- [[Estándar de Ingeniería Android]]
- [[Deuda Técnica - Pendientes]] — P-010

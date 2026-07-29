---
title: "Niveles de API y minSdk — Cobertura Real"
tags:
  - referencia
  - android
  - api-level
date: 2026-07-29
lifecycle: verified
---

# Niveles de API y minSdk — Cobertura Real

> [!info] Fuente
> [apilevels.com](https://apilevels.com/) (datos acumulados, abril 2026) + [developer.android.com — uses-sdk](https://developer.android.com/guide/topics/manifest/uses-sdk-element). Verificado el 2026-07-29.

## El hecho

`minSdk` **no es una preferencia de estilo: define en cuántos teléfonos del mundo la app se puede instalar.** La distribución acumulada real (abril 2026):

| API | Versión | Codename | Dispositivos que la soportan |
|---|---|---|---|
| 24 | Android 7.0 | Nougat | **96.6%** |
| 26 | Android 8.0 | Oreo | 96.1% |
| 30 | Android 10 | Quince Tart | 91.1% |
| 35 | Android 15 | Vanilla Ice Cream | 41.0% |
| 36 | Android 16 | Baklava | 22.3% |
| **37** | **Android 17** | **Cinnamon Bun** | **~0%** |

**Android 17 (API 37) salió el 2026-06-16** — hace poco más de un mes. Su base instalada es prácticamente nula.

## Por qué importa aquí

> [!danger] Hallazgo crítico del proyecto
> El esqueleto generado por Android Studio dejó `minSdk = 37` en `app/build.gradle.kts`. Eso significa que **esta app no instala en ningún teléfono real del mercado hondureño** (ni de casi ningún otro): solo en dispositivos con Android 17, que a julio 2026 son unidades de desarrollo y betas.
>
> Registrado como **P-003** en [[Deuda Técnica - Pendientes]]. Es el ítem de mayor prioridad del proyecto.

Para una app dirigida a **gama baja en LATAM**, el valor correcto es `minSdk = 24`: cubre el 96.6% del parque de dispositivos y, con **desugaring** (`isCoreLibraryDesugaringEnabled`), da acceso a `java.time`, `Optional`, `Stream` y `CompletableFuture` sin arrastrar Dalvik.

## Regla del proyecto

- **`minSdk = 24`** — cobertura máxima, gama baja real.
- **`targetSdk` = el máximo que exige Play** (hoy 36; ver [[Requisitos de Google Play 2026]]). `targetSdk` alto ≠ excluir dispositivos: solo activa los cambios de comportamiento nuevos. Ver [[Android 16 y 17 - Cambios de Comportamiento]].
- **`compileSdk` ≥ `targetSdk`** — compilar contra el SDK más nuevo no restringe nada; solo habilita las APIs.

> [!warning] Confusión frecuente
> Subir `minSdk` **no** "moderniza" la app: la vuelve ininstalable. Subir `targetSdk` sí la moderniza (y es lo que Play exige). Son parámetros con efectos opuestos y se confunden con facilidad.

## Nota sobre AndroidX

Las librerías Jetpack/AndroidX exigen `minSdk` ≥ 23 desde junio 2025, así que `24` está cómodamente dentro del rango soportado.

---

## Relaciones

- [[Requisitos de Google Play 2026]] — qué targetSdk exige Play y desde cuándo
- [[Android 16 y 17 - Cambios de Comportamiento]] — qué se rompe al subir targetSdk
- [[ADR-003 - Politica de minSdk 24 y targetSdk 36]]
- [[Deuda Técnica - Pendientes]] — P-003
- [[Estándar de Ingeniería Android]]

---
title: "Requisitos de Google Play 2026"
tags:
  - referencia
  - google-play
  - publicacion
date: 2026-07-29
lifecycle: verified
---

# Requisitos de Google Play (2026)

> [!info] Fuente
> [Play Console Help — Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878), [developer.android.com — Meet Google Play's target API level requirement](https://developer.android.com/google/play/requirements/target-sdk), [Android Developers Blog — 16 KB page size](https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html). Verificado el 2026-07-29.

## 1. targetSdk 36 — fecha límite 31 de agosto de 2026

- Desde el **2026-08-31**, las **apps nuevas y las actualizaciones** deben apuntar a **Android 16 (API 36)** o superior para publicarse en Google Play.
- Las apps existentes deben apuntar a **API 35+** para seguir disponibles para usuarios nuevos en dispositivos con Android más reciente que el target de la app.
- Hay **prórroga solicitable hasta el 2026-11-01** vía formulario en Play Console.
- **Consecuencia de no cumplir:** la app deja de aparecer para usuarios nuevos en dispositivos Android 16+. Los usuarios que ya la tienen instalada la conservan.

> [!warning] Horizonte siguiente
> Se anticipa que en **agosto de 2027** el requisito suba a **API 37 (Android 17)**. Ver [[Android 16 y 17 - Cambios de Comportamiento]] para lo que eso implicará (adaptabilidad obligatoria en pantallas grandes, `ACCESS_LOCAL_NETWORK`, nuevo Contact Picker).

## 2. Páginas de 16 KB

- Desde el **2025-11-01**, toda app nueva o actualización dirigida a Android 15+ debe **soportar tamaños de página de 16 KB**.
- Aplica **solo si la app incluye código nativo** (`.so`, NDK, directo o vía un SDK de terceros). Se resuelve con AGP 8.5.1+ / NDK r28+ y recompilando.
- **Apps sin código nativo son compatibles sin cambios.**

> [!note] Estado en este proyecto
> El Proyecto Restaurante **no tiene código nativo** — este requisito no aplica hoy. Reevaluar si en el futuro se agrega alguna librería con `.so` (escáner de códigos, procesamiento de imagen, SQLCipher, etc.).

## 3. Formato de publicación

- **Android App Bundle (AAB) obligatorio.** El APK universal ya no se acepta para apps nuevas.
- Splits por densidad, ABI e idioma se generan automáticamente y reducen el tamaño de descarga — crítico para gama baja. Ver [[Presupuestos de Rendimiento en Gama Baja]].

## 4. Otros requisitos que impactan el diseño

- **Declaración de Seguridad de los Datos** obligatoria y consistente con lo que la app realmente hace.
- **Permisos sensibles** (full-screen intent, ubicación en background, contactos, almacenamiento) requieren justificación y, en varios casos, formulario de declaración.
- Apps con `USE_FULL_SCREEN_INTENT` deben solicitarlo explícitamente al apuntar a Android 16.

## Cómo aplica al proyecto

| Requisito | Estado en Proyecto Restaurante |
|---|---|
| `targetSdk` ≥ 36 | ✅ Cumple (está en 37) — pero ver [[Niveles de API y minSdk - Cobertura Real]] por el problema de `minSdk` |
| 16 KB page size | ➖ No aplica (sin código nativo) |
| AAB | ⬜ Todavía no se ha configurado build de release ni firma |
| Data Safety | ⬜ Pendiente para cuando se publique |

---

## Relaciones

- [[Niveles de API y minSdk - Cobertura Real]]
- [[Android 16 y 17 - Cambios de Comportamiento]]
- [[Presupuestos de Rendimiento en Gama Baja]]
- [[Estándar de Ingeniería Android]]

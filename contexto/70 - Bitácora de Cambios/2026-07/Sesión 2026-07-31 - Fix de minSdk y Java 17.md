---
title: "Sesión 2026-07-31 — Fix de minSdk y Java 17"
tags:
  - sesion
  - build
  - minsdk
date: 2026-07-31
branch: feat/fase1-login
autor_cambios: Claude Code (Opus 5)
---

# Sesión 2026-07-31 — Fix de minSdk y Java 17

> [!success] Resultado
> **P-003** y **P-006** resueltos en el mismo cambio: `minSdk` bajó de 37 a 24 (de ~0% a ~96.6% de dispositivos reales) y `sourceCompatibility`/`targetCompatibility` subieron a Java 17. Se encontró y corrigió un efecto colateral (ícono adaptativo incompatible con API < 26). Verificado sobre el APK real, no solo sobre el código fuente.

---

## Problema / motivo

`minSdk = 37` (Android 17, API publicada 2026-06-16) dejaba la app fuera de prácticamente el 100% de los teléfonos reales del mercado — solo instalable en un emulador con esa imagen específica. Era el ítem 🔴 más severo del catálogo de deuda técnica.

---

## Cambios aplicados

### `app/build.gradle.kts`

```kotlin
defaultConfig {
    minSdk = 24        // era 37
    targetSdk = 37      // sin cambios — solo baja el piso, no el techo
}
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17   // era VERSION_11
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = true          // nuevo
}
dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)   // nuevo
}
```

### `gradle/libs.versions.toml`

Agregada la versión `desugarJdkLibs = "2.1.4"` (verificada en Maven Central, no inventada) y su entrada en `[libraries]`.

### Efecto colateral encontrado: ícono adaptativo incompatible

Al reconstruir, `aapt2` (el linker de recursos) rechazó el build:

```
mipmap-anydpi/ic_launcher.xml: error: <adaptive-icon> elements require a sdk version of at least 26.
```

`mipmap-anydpi` (sin calificador de versión) declara un `<adaptive-icon>`, que exige API 26+. Con `minSdk=24` recién declarado, el linker exige que todo recurso sin calificador de versión sea válido en **todo** el rango soportado — y ya no lo era.

**Solución:** `git mv app/src/main/res/mipmap-anydpi app/src/main/res/mipmap-anydpi-v26` — el sufijo `-v26` le dice al sistema de recursos que ese ícono adaptativo solo aplica en API 26+. Los bitmaps de `mipmap-hdpi/mdpi/xhdpi/xxhdpi/xxxhdpi` **ya existían** en el proyecto como *fallback* para API 24-25 (los generó Android Studio junto con el ícono adaptativo desde el inicio), así que no hizo falta crear nada nuevo — solo renombrar la carpeta.

---

## Verificación

1. `./gradlew clean assembleDebug testDebugUnitTest` → **BUILD SUCCESSFUL**, 17 tests sin fallas (mismos de la sesión anterior: `ValidadorContraseniaTest` ×9, `VisibilidadMenuTest` ×5, `SesionActualTest` ×3, `ExampleUnitTest` ×1).
2. `aapt2 dump badging app-debug.apk` sobre el **APK ya empaquetado** (no el `.kts` fuente) confirmó:
   ```
   sdkVersion:'24'
   targetSdkVersion:'37'
   ```
3. ⬜ **Pendiente:** reinstalar y relanzar en el emulador para confirmar comportamiento en runtime — el emulador estaba desconectado (`adb: no devices/emulators found`) al momento de intentarlo. El cambio es puramente de configuración de build (no toca ningún `.java`), así que el riesgo de regresión en runtime es bajo, pero queda anotado para reverificar.

---

## Deuda tocada

| Ítem | Antes | Ahora |
|---|---|---|
| **P-003** | 🔴 Pendiente — máxima prioridad | ✅ Resuelto |
| **P-006** | 🟡 Pendiente | ✅ Resuelto (mismo cambio) |
| **P-004** | Nota "falta verificar, bloqueado por P-003" | Ya no bloqueado — solo falta la prueba física |

---

## Lo que NO cambió

- Ningún archivo `.java` — el cambio es 100% configuración de Gradle + un rename de carpeta de recursos.
- `compileSdk`/`targetSdk` siguen en 37 — se resolvió únicamente el piso de compatibilidad, no el objetivo.
- No se instaló en un dispositivo físico real todavía (eso sigue siendo una prueba manual pendiente, ahora que ya no está bloqueada).
- No se tocó R8/Baseline Profile (**P-008**, separado, para antes de publicar).

---

## Relaciones

- [[Deuda Técnica - Pendientes]] — P-003 y P-006 resueltos
- [[Arquitectura Actual]]
- [[Toolchain Android 2026 - AGP, Gradle y JDK]]
- [[Niveles de API y minSdk - Cobertura Real]]
- [[Roadmap de Fases]] — cierra buena parte de la Fase 0
- [[Sesión 2026-07-31 - Primer login verificado en emulador]]

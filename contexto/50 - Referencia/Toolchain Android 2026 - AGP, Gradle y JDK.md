---
title: "Toolchain Android 2026 — AGP, Gradle y JDK"
tags:
  - referencia
  - gradle
  - build
date: 2026-07-29
lifecycle: verified
---

# Toolchain Android 2026 — AGP, Gradle y JDK

> [!info] Fuente
> [developer.android.com — About Android Gradle plugin](https://developer.android.com/build/releases/about-agp), [Gradle 9.0 release notes](https://docs.gradle.org/9.0.0/release-notes.html). Verificado el 2026-07-29.

## Matriz de compatibilidad AGP ↔ Gradle

| AGP | Gradle mínimo |
|---|---|
| 9.3 (última estable, julio 2026) | 9.5.0 |
| 9.2 | 9.4.1 |
| 9.1 | 9.3.1 |
| 9.0 | 9.1.0 |

- **JDK 17 es requisito** para AGP 9.x / Gradle 9.x. Se fija con el **toolchain de Gradle**, no con el JDK del sistema.
- Saltar de AGP 8.x a 9.x **obliga** a saltar de Gradle 8.x a 9.x. No es opcional ni evitable.
- AGP 9.0+ trae **soporte de Kotlin integrado y activado por defecto** (ya no hace falta aplicar `org.jetbrains.kotlin.android`), y tiene dependencia en runtime de KGP 2.2.10+. Esto no obliga a escribir Kotlin, pero explica por qué el build descarga artefactos de Kotlin en un proyecto Java.

## Estado de este proyecto

| Componente | Valor actual | ¿Consistente? |
|---|---|---|
| AGP | 9.2.1 | ✅ |
| Gradle (wrapper) | 9.4.1 | ✅ — coincide con el mínimo de AGP 9.2 |
| JDK / `sourceCompatibility` | **11** | ⚠️ El estándar pide **17** (ver **P-006** en [[Deuda Técnica - Pendientes]]) |
| `compileSdk` / `targetSdk` | 37 | ✅ (con salvedades, ver [[Android 16 y 17 - Cambios de Comportamiento]]) |
| `minSdk` | **37** | 🔴 Crítico — ver [[Niveles de API y minSdk - Cobertura Real]] |
| Build DSL | Kotlin DSL (`.kts`) | ✅ |
| Version Catalog | `gradle/libs.versions.toml` | ✅ |

## Reglas del proyecto

1. **Kotlin DSL + Version Catalog obligatorios.** Groovy DSL prohibido en proyecto nuevo. Ninguna versión se escribe suelta en un `build.gradle.kts`: todas viven en `libs.versions.toml`.
2. **Cero versiones dinámicas** (`+`, `latest.release`). Rompen builds reproducibles.
3. **Nunca inventar un número de versión.** Antes de fijarlo, verificar en la fuente oficial (developer.android.com, Maven Central, GitHub Releases). Si no se puede verificar, se documenta el supuesto.
4. **Desugaring obligatorio** con `minSdk` bajo:
   ```kotlin
   compileOptions {
       isCoreLibraryDesugaringEnabled = true
       sourceCompatibility = JavaVersion.VERSION_17
       targetCompatibility = JavaVersion.VERSION_17
   }
   dependencies {
       coreLibraryDesugaring(libs.desugar.jdk.libs)
   }
   ```
   Habilita `java.time`, `Optional`, `Stream` y `CompletableFuture` desde API 24.
5. En multi-módulo, la configuración compartida va en **convention plugins** (`build-logic`), nunca copiada entre módulos.

---

## Relaciones

- [[Niveles de API y minSdk - Cobertura Real]]
- [[Librerias Java-Friendly vs Kotlin-Only]]
- [[Presupuestos de Rendimiento en Gama Baja]]
- [[Estándar de Ingeniería Android]]
- [[Deuda Técnica - Pendientes]] — P-006

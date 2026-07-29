---
title: "Sesión 2026-07-29 — Auditoría contra el Estándar de Ingeniería Android"
tags:
  - sesion
  - auditoria
date: 2026-07-29
branch: feat/fase1-login
autor_cambios: Claude Code (agente)
---

# Sesión 2026-07-29 — Auditoría contra el Estándar de Ingeniería Android

> [!success] Resultado
> Se adoptó un estándar de ingeniería Android (Java, gama baja LATAM) como contrato del proyecto, se verificaron sus afirmaciones técnicas contra fuentes oficiales, y se auditó el código de la Fase 1 contra él. Resultado: **16 ítems de deuda nuevos (P-003 a P-018)**, uno de ellos bloqueante para la distribución de la app.

---

## Problema / motivo

El usuario aportó un estándar de ingeniería Android v1.0 (julio 2026) y pidió **investigar y nutrir la bóveda** con esa información. Dos requisitos implícitos del propio protocolo de la bóveda: no inventar versiones ni fechas, y contrastar el estándar contra el código real en vez de documentarlo en abstracto.

## Hallazgo principal

> [!danger] `minSdk = 37` deja la app fuera del 100% de los teléfonos reales
> El esqueleto de Android Studio fijó `minSdk = 37`. Verificado contra [apilevels.com](https://apilevels.com/): **API 37 es Android 17 "Cinnamon Bun", publicada el 2026-06-16, con ~0% de cuota de dispositivos**. API 24, en cambio, cubre el **96.6%**.
>
> La app compila, empaqueta y corre en emulador — pero no se instala en ningún teléfono real. Es un fallo completamente silencioso. Registrado como **P-003**, máxima prioridad.

## Verificación de datos (fuentes consultadas)

Ninguna versión ni fecha se copió del prompt sin verificar:

| Dato | Verificado | Fuente |
|---|---|---|
| targetSdk 36 obligatorio desde 2026-08-31 (prórroga al 2026-11-01) | ✅ | [Play Console Help](https://support.google.com/googleplay/android-developer/answer/11926878) |
| Edge-to-edge sin opt-out en API 36 | ✅ | [Behavior changes Android 16](https://developer.android.com/about/versions/16/behavior-changes-16) |
| API 37 = Android 17, adaptabilidad obligatoria en pantallas grandes | ✅ | [Behavior changes Android 17](https://developer.android.com/about/versions/17/behavior-changes-17) |
| AGP estable = 9.3.0 (Gradle 9.5); AGP 9.2 → Gradle 9.4.1 | ✅ | [about-agp](https://developer.android.com/build/releases/about-agp) |
| 16 KB page size desde 2025-11-01, solo si hay código nativo | ✅ | [Android Developers Blog](https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html) |
| Supabase: `sb_publishable_`/`sb_secret_`; `anon`/`service_role` se deprecan a fines de 2026 | ✅ | [Supabase Docs](https://supabase.com/docs/guides/getting-started/migrating-to-new-api-keys) |
| Hilt 2.59.2; 2.57.1+ requiere Java 17 | ✅ | [dagger.dev](https://dagger.dev/hilt/gradle-setup.html) |
| Distribución de API levels (abril 2026) | ✅ | [apilevels.com](https://apilevels.com/) |

**Corrección al estándar aportado:** el prompt indicaba "Retrofit 3.x + OkHttp 5.x". Verificado en el [CHANGELOG de Retrofit](https://github.com/square/retrofit/blob/trunk/CHANGELOG.md): Retrofit 3.0.0 depende de **OkHttp 4.12**, no 5, y está **escrito en Kotlin** (usable desde Java, pero arrastra una dependencia transitiva de Kotlin que pesa en el APK). Documentado con ese matiz en [[Librerias Java-Friendly vs Kotlin-Only]].

## Cambios aplicados

**Bóveda — 18 notas nuevas o reescritas. Cero cambios de código.**

- `40 - Proyecto Restaurante/`: **[[Estándar de Ingeniería Android]]** (nodo raíz del contrato), **[[Gate de Autoverificación]]**, y reescritura de [[Deuda Técnica - Pendientes]], [[Arquitectura Actual]] y [[Roadmap de Fases]] (con la nueva **Fase 0** de remediación).
- `50 - Referencia/` (8 notas de hechos externos verificados): [[Niveles de API y minSdk - Cobertura Real]], [[Requisitos de Google Play 2026]], [[Android 16 y 17 - Cambios de Comportamiento]], [[Toolchain Android 2026 - AGP, Gradle y JDK]], [[Librerias Java-Friendly vs Kotlin-Only]], [[Lista Negra de APIs Android]], [[Presupuestos de Rendimiento en Gama Baja]], [[Seguridad y Privacidad Android]], [[Accesibilidad Android]], [[Estrategia de Pruebas Android]]; actualizadas [[Convenciones Java]] y [[Supabase Auth REST - Login Android]].
- `20 - Patrones/`: [[Catálogo de Patrones Android]] (16 patrones × 5 usos concretos), [[UiState Inmutable y Flujo Unidireccional]], [[Offline-First con Room y Outbox]], [[Asincronia en Java para Android]]; actualizadas [[MVVM en Android (ViewModel + LiveData)]] y [[Result Pattern]].
- `10 - Arquitectura/`: [[Modularizacion por Feature]]; actualizada [[Clean Architecture]].
- `45 - Decisiones/`: [[ADR-003 - Politica de minSdk 24 y targetSdk 36]], [[ADR-004 - Java + Views en vez de Kotlin + Compose]], [[ADR-005 - Offline-first obligatorio desde la Fase 2]].
- `00 - MOC/`: [[Conocimiento Principal]] reindexado.

## Deuda registrada (P-003 a P-018)

| 🔴 Críticos | 🟡 Importantes | 🟢 Menores |
|---|---|---|
| P-003 `minSdk 37` | P-005 Executor no inyectado / cero tests | P-007 Retrofit 2 sin adaptadores |
| P-004 login sin insets | P-006 Java 11 → 17 | P-011 IDs y color hardcodeado |
| P-014 sin offline-first | P-008 sin R8/Baseline Profile | P-012 nombre de llave legado |
| | P-009 token sin persistir ni refresh | P-017 paquetes layer-first |
| | P-010 accesibilidad | P-018 `applicationId` `com.example.*` |
| | P-013 evento de navegación sin consumir | |
| | P-015 `Activity`+`findViewById` | |
| | P-016 `Result` con `String` | |

## Verificación

- Todo dato técnico contrastado contra fuente oficial (tabla arriba); ninguna versión inventada.
- Todos los hallazgos de código verificados leyendo los archivos reales de este repo, no inferidos del prompt.
- **No se ejecutó build** en esta sesión: no se tocó una sola línea de código. El último build verificado sigue siendo el de [[Sesión 2026-07-29 - Bootstrap de bóveda y Fase 1 Login]] (`./gradlew assembleDebug` → BUILD SUCCESSFUL).

## Lo que NO cambió

- **Cero código modificado.** La auditoría solo documenta; corregir es la Fase 0.
- No se tocó `minSdk` ni ninguna configuración de build, aunque P-003 sea crítico — cambiarlo es un trabajo con verificación propia (compilar con `minSdk 24`, revisar APIs sin guard), no un retoque al pasar.
- No se agregaron dependencias (Room, Hilt, WorkManager quedan documentados, no instalados).
- No se completó la "Pregunta Clave" del MOC: es del usuario.

---

## Relaciones

- [[Estándar de Ingeniería Android]]
- [[Gate de Autoverificación]]
- [[Deuda Técnica - Pendientes]]
- [[Arquitectura Actual]]
- [[Roadmap de Fases]]
- [[Sesión 2026-07-29 - Bootstrap de bóveda y Fase 1 Login]]

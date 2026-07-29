---
title: "ADR-003 — Política de minSdk 24 y targetSdk 36+"
tags:
  - adr
  - decision
date: 2026-07-29
estado: aceptado
---

# ADR-003 — Política de `minSdk 24` y `targetSdk 36+`

## Contexto

El esqueleto generado por Android Studio dejó `minSdk = 37`, `targetSdk = 37`, `compileSdk = 37`. Al auditar el proyecto contra el [[Estándar de Ingeniería Android]] se descubrió que **API 37 es Android 17 ("Cinnamon Bun"), publicada el 2026-06-16, con ~0% de cuota de dispositivos**.

Consecuencia: la app compila, empaqueta y corre en emulador, pero **no se instala en ningún teléfono real** — ni de los usuarios del restaurante, ni de quien la evalúe. El fallo es completamente silencioso.

La app apunta a **gama baja en LATAM**, donde el parque de dispositivos es viejo: Android 9–11 con 2 GB de RAM es el caso típico.

## Decisión

- **`minSdk = 24`** (Android 7.0 Nougat) — cubre el **96.6%** de los dispositivos.
- **`targetSdk` = el máximo que exija Google Play** (hoy **36**; obligatorio desde el 2026-08-31).
- **`compileSdk` ≥ `targetSdk`** — compilar contra el SDK más nuevo no restringe nada.
- **Desugaring obligatorio** (`isCoreLibraryDesugaringEnabled = true` + `desugar_jdk_libs`) para tener `java.time`, `Optional`, `Stream` y `CompletableFuture` desde API 24.
- **Java 17** en `sourceCompatibility`/`targetCompatibility`.

## Alternativas consideradas

| Opción | Pro | Contra | ¿Elegida? |
|---|---|---|---|
| Dejar `minSdk 37` | Acceso a todas las APIs sin guards ni desugaring | **La app es indistribuible.** 0% de dispositivos | ❌ |
| `minSdk 26` (Oreo) | 96.1%, evita algunos quirks de Nougat | Solo 0.5 puntos menos de cobertura que 24, sin ganancia real | ❌ |
| `minSdk 30` (Android 11) | 91.1%, APIs más modernas sin desugaring | Deja fuera **9 de cada 100** dispositivos — inaceptable en el mercado objetivo | ❌ |
| **`minSdk 24` + desugaring** | 96.6% de cobertura; `java.time` y `Stream` disponibles igual | Algún guard `Build.VERSION.SDK_INT` puntual; APK levemente mayor | ✅ |

## Consecuencias

- **Se gana:** la app pasa de instalable en ~0% a instalable en ~96.6% de los dispositivos. Es, literalmente, la diferencia entre tener producto y no tenerlo.
- **Se sacrifica:** algunos guards de versión puntuales y unos KB extra de desugaring en el APK.
- `targetSdk` alto activa cambios de comportamiento que **hay que manejar**: edge-to-edge obligatorio, `onBackPressed()` inerte, adaptabilidad en pantallas grandes. Ver [[Android 16 y 17 - Cambios de Comportamiento]].
- **Seguimiento:** al subir `targetSdk` a 37 (previsto como requisito de Play para agosto de 2027), revisar la adaptabilidad en pantallas grandes y el permiso `ACCESS_LOCAL_NETWORK` — este último es plausible si en el futuro se manda la comanda a una impresora en la red local del restaurante.

> [!warning] Confusión que motivó este ADR
> `minSdk` y `targetSdk` tienen efectos **opuestos** y se confunden con facilidad. Subir `minSdk` **no moderniza** la app: la vuelve ininstalable. Subir `targetSdk` sí la moderniza, y es lo que Play exige.

---

## Relaciones

- [[Niveles de API y minSdk - Cobertura Real]]
- [[Requisitos de Google Play 2026]]
- [[Android 16 y 17 - Cambios de Comportamiento]]
- [[Toolchain Android 2026 - AGP, Gradle y JDK]]
- [[Deuda Técnica - Pendientes]] — P-003, P-006
- [[Arquitectura Actual]]

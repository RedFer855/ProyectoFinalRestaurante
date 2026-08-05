---
title: "Sesión 2026-08-04 — Repriorización de la deuda técnica y planes P0, P1 y P2"
tags:
  - sesion
  - deuda-tecnica
  - planificacion
  - refactor
date: 2026-08-04
branch: feat/fase2cd-mesas-clientes
autor_cambios: Claude (Opus 5) + usuario
---

# Sesión 2026-08-04 — Repriorización de la deuda técnica y planes P0, P1 y P2

> [!info] Sesión de **planificación**
> No se tocó código de `app/`. Continúa
> [[Sesión 2026-08-04 - Plan de Fase 3, tiempo real por señal y buzón de notificaciones]].

## Qué se hizo

Se releyó la deuda completa (**29 ítems: 10 resueltos, 2 parciales, 17 pendientes**) y se
repriorizó en tres bandas, con un plan por banda:

| Banda | Ítems | Plan |
|---|---|---|
| **P0** | P-018 · P-029 · P-009 · P-004 | [[Plan Fase 0b - Cierre de la deuda P0]] |
| **P1** | P-015 · P-016+P-019+P-001 · P-025 | [[Plan Fase 0c - Deuda P1 y P2]] |
| **P2** | P-028 · P-008 · P-017+P-011 · P-024 · P-010 | idem |

El criterio no fue la severidad 🔴🟡🟢 del archivo —asignada en julio y ya desactualizada:
los dos rojos, P-003 y P-014, están cerrados— sino **bloqueo**, **irreversibilidad** y **costo
de postergar**.

## Los hallazgos que cambiaron decisiones

### 1. `EncryptedSharedPreferences` está deprecado — P-009 tenía mal la solución

Google deprecó **todas** las APIs de `androidx.security:security-crypto` en `1.1.0-alpha07`
(abril 2025), repetido en `1.1.0-beta01` (junio 2025):
*"Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore."*

La bóveda lo prescribía en **dos** notas. Se corrigieron las dos y se diseñó el reemplazo
—**Android Keystore directo**, que es literalmente lo que Google indica— en el plan de P0.
Se descartó DataStore + Tink: su API idiomática es Kotlin `Flow` y el puente Java arrastra
RxJava3 entero.

### 2. Kotlin ya está en el APK, así que un criterio de decisión era falso

Inspeccionando `debugRuntimeClasspath`: **`kotlin-stdlib 2.2.10` está en el runtime**, con 47
referencias, traído por `activity`, `appcompat`, `core`, `annotation` y `lifecycle`.

Eso refuta el argumento con el que se venían tomando dos decisiones:

- **P-007** rechazaba Retrofit 3 porque *"arrastra una dependencia transitiva de Kotlin… suma
  peso al APK"*. No suma: ya está.
- La misma objeción se le podía hacer a Navigation Component (**P-015**). Tampoco aplica.

Lo que **sí** sigue valiendo es el criterio de **ergonomía** (`Flow`/`suspend`/Compose son
incómodos desde Java). La regla queda: *"¿es usable desde Java sin bridges?"*, no *"¿arrastra
Kotlin?"*. Corregido en [[Librerias Java-Friendly vs Kotlin-Only]] y en P-007.

### 3. Baseline Profile está bloqueado por versiones — P-008 se parte en dos

`androidx.benchmark` / `androidx.baselineprofile` estable es **1.4.1** (2025-09-10), que
recomienda AGP máximo `9.0.0-alpha01` y exigía `newDsl=false`. El soporte del DSL nuevo de
AGP 9 llegó en **1.5.0-alpha01**, y 1.5.0 **sigue en alpha** (`1.5.0-alpha05`). El proyecto
está en **AGP 9.2.1**.

- **P-008a** (R8 + `shrinkResources`) → se puede hacer ya.
- **P-008b** (Baseline Profile + Macrobenchmark) → **bloqueado**, con condición de desbloqueo
  explícita: *"cuando benchmark 1.5.0 llegue a estable"*. Meter un plugin alpha en el camino
  de release por una optimización de arranque no pasa la regla de vigencia del estándar.

### 4. Tres ítems comparten radio de impacto — se ejecutan juntos

Medición del código:

| Métrica | Valor |
|---|---|
| `findViewById` | **169** en 22 archivos |
| Pantallas | **17** (4 Activities + 7 Fragments + 6 diálogos) |
| Layouts | **28** |
| IDs en `snake_case` | **197 de 198** |
| `mensajeDeError()` duplicado | 4 remotos + `SupabaseAuthRepository` |
| Constantes de texto en `ui/`+`data/` | **~57** |

**ViewBinding deriva el nombre del campo del ID.** Así que si se convierten las 169
`findViewById` (P-015) y se renombran los IDs después (P-011), hay que tocar **dos veces** cada
referencia. Renombrando en el mismo pase, P-011 cuesta **cero**. Y P-017 (feature-first) mueve
los mismos archivos.

La bóveda ya había visto la mitad — P-011 estaba marcado "bloqueado por P-017". Lo que faltaba
ver es que **P-015 es el que los vuelve gratis**.

Por eso el plan de P1/P2 no está organizado por ítem sino en **tres pases**: A (errores,
~1,5 días), B (el gran pase de UI, ~3 días), C (infraestructura, ~1 día). A y C son ortogonales
a B y se pueden intercalar; **B es todo o nada**.

## Decisiones tomadas

| Decisión | Resultado |
|---|---|
| **P-017** feature-first vs layer-first | **Feature-first**, en módulo Gradle único |
| **Multi-módulo Gradle** | **Descartado** — `api`/`implementation`, builds lentos y Room repartido a cambio de poco |
| **P-011** cuándo | Junto a P-015, porque ahí es gratis |
| **P-008** | Partido en 008a (se hace) y 008b (bloqueado) |
| Orden vs. Fase 3 | **Fase 3 primero**: el pase B crece ~12% por las 2 pantallas nuevas; postergar el tiempo real posterga lo único que se pidió |

Del pase B sale un **ADR-009 — Feature-first en módulo único**, que escribe quien lo ejecute.

## Archivos de la bóveda

| Archivo | Acción |
|---|---|
| `40 …/Plan Fase 0b - Cierre de la deuda P0.md` | **Nuevo** (commit anterior) |
| `40 …/Plan Fase 0c - Deuda P1 y P2.md` | **Nuevo** |
| `40 …/Deuda Técnica - Pendientes.md` | P-007, P-008, P-015, P-017 actualizados |
| `50 …/Librerias Java-Friendly vs Kotlin-Only.md` | Corregido el argumento del peso de Kotlin |
| `50 …/Seguridad y Privacidad Android.md` | Corregido `EncryptedSharedPreferences` (commit anterior) |
| `40 …/Roadmap de Fases.md` | Continuaciones 0b/0c; P-017 y multi-módulo cerrados |

## Qué falta

- **Ejecutar.** Nada de esto se implementó: son planes.
- **Decisión pendiente:** el `applicationId` de P-018 (`hn.restaurante.app` u otro).
- **P-004 y P-010** son verificaciones **del usuario**, con un teléfono físico. Van juntas.

---

## Relaciones

- [[Plan Fase 0b - Cierre de la deuda P0]] · [[Plan Fase 0c - Deuda P1 y P2]]
- [[Sesión 2026-08-04 - Plan de Fase 3, tiempo real por señal y buzón de notificaciones]]
- [[Deuda Técnica - Pendientes]] · [[Roadmap de Fases]]
- [[Librerias Java-Friendly vs Kotlin-Only]] · [[Seguridad y Privacidad Android]]
- [[Estándar de Ingeniería Android]] · [[Propuesta de División de Arquitectura]]

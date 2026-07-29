---
title: Arquitectura Actual — Proyecto Restaurante
tags:
  - restaurante
  - arquitectura
  - moc
aliases:
  - Estado actual
---

# Arquitectura Actual — Proyecto Restaurante

> [!info] MOC del proyecto
> Este nodo describe el estado **actual y real** de la arquitectura. Lo que *debería* ser está en [[Estándar de Ingeniería Android]]; la brecha entre ambos está en [[Deuda Técnica - Pendientes]].

> [!danger] Actualizado 2026-07-29 — Auditoría contra el estándar
> Se adoptó el [[Estándar de Ingeniería Android]] y se auditó la Fase 1 contra él. Resultado: **16 ítems de brecha**, entre ellos `minSdk = 37` (**P-003**), que hace que la app **no se instale en ningún teléfono real del mercado**. La remediación es la **Fase 0** de [[Roadmap de Fases]].

> [!success] Bootstrap 2026-07-29 — Bóveda + Fase 1 (Login)
> Proyecto Android (Java, esqueleto de Android Studio) + bóveda Obsidian construida sobre el patrón del proyecto Bimbo, con la arquitectura adaptada a mobile. Primer módulo: **Login** contra Supabase Auth vía REST/Retrofit, en la rama `feat/fase1-login`.

---

## Único módulo Gradle: `app`

A diferencia de un proyecto .NET con un proyecto/DLL por capa, acá **todo vive en el módulo Gradle `app`**, separado por **paquetes Java** que cumplen el mismo rol:

```
com.example.proyectofinalrestaurante
├── ui/       → Activities, ViewModels, estado de UI
├── domain/   → entidades, interfaces de repositorio, Result
├── data/     → implementaciones concretas (Retrofit, DTOs)
└── core/     → cliente HTTP/Supabase compartido
```

Ver [[ADR-001 - Arquitectura por capas (ui-domain-data) en Android con Java]] y [[Modularizacion por Feature]].

---

## Diagrama de dependencias

```mermaid
graph TD
    UI[ui] --> DOM[domain]
    DATA[data] --> DOM
    UI --> CORE[core]
    DATA --> CORE
```

> [!warning] Regla
> `domain` **nunca** referencia `data`. La flecha va en sentido contrario: `data` implementa las interfaces que `domain` define. ✅ Se cumple hoy.

---

## Paquetes y responsabilidades

| Paquete | Rol | Depende de |
|---|---|---|
| `ui.login` | `LoginActivity`, `LoginViewModel`, `EstadoLogin`, `LoginViewModelFactory` | `domain` |
| `domain.model` | `Sesion` | — |
| `domain.repository` | `AuthRepository` (interfaz) | — |
| `domain` (raíz) | `Result<T>` | — |
| `data.remote` | `SupabaseAuthApi` (Retrofit), DTOs | `core` |
| `data.repository` | `SupabaseAuthRepository` | `domain`, `data.remote` |
| `core` | `SupabaseClient` (Retrofit singleton, credenciales vía `BuildConfig`) | — |

---

## Entry point

- `AndroidManifest.xml`: `LoginActivity` es la actividad `LAUNCHER`.
- Login exitoso → `startActivity(MainActivity)` + `finish()`.
- `MainActivity` es la pantalla post-login (placeholder de Fase 1).

> [!warning] Diverge del estándar
> El estándar pide **single-Activity + Fragments + Navigation Component**. Hoy son dos `Activity` con `Intent` explícito. Ver **P-015**.

---

## Configuración de build

| Parámetro | Valor actual | Objetivo del estándar |
|---|---|---|
| AGP | 9.2.1 | ✅ 9.x |
| Gradle | 9.4.1 | ✅ coincide con el mínimo de AGP 9.2 |
| `compileSdk` / `targetSdk` | 37 / 37 | ✅ ≥ 36 |
| **`minSdk`** | **37** | 🔴 **24** — ver **P-003** |
| Java source/target | 11 | 🟡 17 — ver **P-006** |
| R8 en release | desactivado | 🟡 activado — ver **P-008** |
| Build DSL | Kotlin DSL + Version Catalog | ✅ |

Ver [[Toolchain Android 2026 - AGP, Gradle y JDK]] y [[Niveles de API y minSdk - Cobertura Real]].

---

## Patrones implementados

| Patrón | Dónde | Estado |
|---|---|---|
| [[Clean Architecture]] | `ui`/`domain`/`data`/`core` | ✅ Capas separadas; ⬜ sin `UseCase` todavía |
| [[Repository Pattern]] | `data.repository` | ✅ Implementado |
| [[Result Pattern]] | `domain.Result`, `SupabaseAuthRepository` | ✅ Básico; ⬜ sin `AppException` (**P-016**) |
| [[MVVM en Android (ViewModel + LiveData)]] | `ui.login` | ✅ Implementado; ⚠️ `Executor` no inyectado (**P-005**) |
| [[UiState Inmutable y Flujo Unidireccional]] | `ui.login.EstadoLogin` | ✅ Estado único inmutable; ⚠️ sin evento consumido (**P-013**) |
| Interceptor (Decorator) | `core.SupabaseClient` — header `apikey` | ✅ Implementado |
| [[Offline-First con Room y Outbox]] | — | ⬜ **No implementado** (**P-014**) |
| [[Base Repository con manejo de errores]] | — | ⬜ Documentado, no extraído (**P-001**) |
| DI con Hilt | — | ⬜ DI manual (**P-002**) |
| ViewBinding | — | ⬜ `findViewById` (**P-015**) |

---

## Módulos

| Módulo | Estado | Archivos clave |
|---|---|---|
| [[Módulo Login]] | 🟡 Funcional con deuda | `LoginActivity`, `LoginViewModel`, `SupabaseAuthRepository` |
| Menú | ⬜ No iniciado — **primer módulo con Room/offline** | — |
| Pedidos | ⬜ No iniciado | — |
| Mesas | ⬜ No iniciado | — |
| Usuarios/Roles | ⬜ No iniciado | — |
| Reportes | ⬜ No iniciado | — |

Ver [[Roadmap de Fases]].

---

## Advertencias conocidas

1. 🔴 **`minSdk = 37`** — la app no instala en dispositivos reales (**P-003**).
2. 🔴 **`LoginActivity` sin manejo de insets** — con `targetSdk 37` el contenido queda bajo las barras del sistema (**P-004**).
3. 🔴 **Sin arquitectura offline** — decisión obligada al arrancar la Fase 2 (**P-014**).
4. ⚠️ **Cero pruebas propias** — solo los ejemplos de Android Studio (**P-005**).
5. ⚠️ `SUPABASE_URL`/`SUPABASE_ANON_KEY` vacíos en `local.properties` — hay que crear un proyecto Supabase real y usar la llave **publishable** (`sb_publishable_...`), no la `anon` legada (**P-012**).
6. ⚠️ `applicationId` sigue en `com.example.*` — Play lo rechaza y es irreversible tras publicar (**P-018**).

---

## Próximos pasos recomendados

1. **Fase 0** — cerrar la brecha crítica contra el estándar, empezando por **P-003**.
2. **Fase 2 (Menú)** — implementar Room + offline-first **desde el día uno** de ese módulo.
3. Extraer `BaseRepository` + `AppException` al agregar el segundo repositorio.
4. Decidir feature-first vs layer-first antes de que existan tres features.

---

## Relaciones

- [[Estándar de Ingeniería Android]]
- [[Deuda Técnica - Pendientes]]
- [[Roadmap de Fases]]
- [[Gate de Autoverificación]]
- [[Módulo Login]]
- [[Clean Architecture]]

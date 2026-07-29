---
title: Clean Architecture
tags:
  - arquitectura
  - conceptos
aliases:
  - Arquitectura Limpia
---

# Clean Architecture

> [!abstract] Definición
> Separar el software en capas donde las dependencias apuntan **siempre hacia adentro** (hacia el dominio). Las capas externas (UI, infraestructura) pueden cambiar sin tocar la lógica de negocio.

---

## El modelo del proyecto: 3 capas + MVVM con flujo unidireccional

```
        ┌────────────────────────────────────────────────┐
UI      │ Activity/Fragment → ViewModel → UiState         │  androidx, ViewBinding
        └──────────────▲─────────────────┬───────────────┘
                       │ LiveData<UiState>│ eventos = llamadas a métodos
        ┌──────────────┴─────────────────▼───────────────┐
DOMAIN  │ UseCase (verbo) · Modelos puros · Puertos       │  Java puro, SIN android.*
        └──────────────▲─────────────────┬───────────────┘
        ┌──────────────┴─────────────────▼───────────────┐
DATA    │ RepositoryImpl → LocalDataSource (Room)         │
        │                → RemoteDataSource (Retrofit)    │
        └────────────────────────────────────────────────┘
```

| Capa | Rol | Depende de |
|---|---|---|
| `ui` | Activities, Fragments, ViewModels, `UiState` | `domain` |
| `domain` | Entidades, `UseCase`, interfaces de repositorio, `Result` | **nada** |
| `data` | Implementaciones concretas (Retrofit, Room, mappers) | `domain`, `core` |
| `core` | Infraestructura compartida: cliente HTTP, executors | — |

---

## Las 6 reglas de dependencia (se verifican en cada entrega)

1. **`domain` no importa `android.*`, `androidx.*`, Retrofit, Room ni Gson/Moshi.** Es Java puro, testeable en JVM sin Robolectric.
2. **Las dependencias apuntan hacia adentro.** `data` implementa interfaces declaradas en `domain` (inversión de dependencias).
3. **La UI nunca toca un `DataSource`, un DAO ni un `ApiService`**: solo `UseCase` (o `Repository` en apps pequeñas).
4. **Un modelo por capa** cuando difieren: `PedidoDto` (red) → `PedidoEntity` (Room) → `Pedido` (dominio) → `PedidoUiModel` (UI). Los mappers son explícitos y probados.
5. **Flujo unidireccional:** el estado baja (`LiveData<UiState>`), los eventos suben (llamadas a métodos). El ViewModel **no emite eventos hacia la UI**: procesa y publica un estado nuevo. Ver [[UiState Inmutable y Flujo Unidireccional]].
6. **Existe un `Repository` aunque haya una sola fuente de datos.** Es la costura que permite meter Room después sin tocar la UI.

> [!warning] El límite es disciplina, no compilador
> En un módulo Gradle único, nada impide técnicamente que `domain` importe algo de `data`. La regla se sostiene con revisión y con el [[Gate de Autoverificación]]. En multi-módulo, Gradle sí lo impide — una razón más para [[Modularizacion por Feature]].

---

## Por qué importa en una app Android chica

Es tentador meter todo en la `Activity`. El costo aparece cuando:

- Querés testear la lógica de login sin levantar un emulador → si vive en `domain`/`data` puro se testea con JUnit plano en milisegundos.
- Cambiás Retrofit por otra librería, o Supabase por otro backend → solo se toca `data`.
- Agregás **caché offline** → se mete Room detrás del `Repository` y la UI no se entera. Sin esa costura, hay que reescribir cada pantalla. Ver [[Offline-First con Room y Outbox]].

---

## Estado en el proyecto

✅ Las 4 capas existen y la regla #1 y #2 se cumplen: `domain` no importa nada de Android, `data` implementa `AuthRepository`.

⬜ Todavía **no hay `UseCase`**: `LoginViewModel` habla directamente con `AuthRepository`. Es una simplificación aceptada para una app chica (la regla 3 lo permite: *"o `Repository` en apps pequeñas"*), pero cuando aparezca lógica de negocio real (validar stock, calcular total con impuestos) debe subir a un `UseCase` y no quedarse en el ViewModel.

---

## Relaciones

- [[SOLID]]
- [[Capa de Dominio]]
- [[Modularizacion por Feature]]
- [[Repository Pattern]]
- [[Result Pattern]]
- [[UiState Inmutable y Flujo Unidireccional]]
- [[Catálogo de Patrones Android]]
- [[Arquitectura Actual]]

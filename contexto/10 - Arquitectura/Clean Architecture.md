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
> Separar el software en capas concéntricas donde las dependencias siempre apuntan **hacia adentro** (hacia el dominio). Las capas externas (UI, infraestructura) pueden cambiar sin tocar la lógica de negocio.

---

## Las capas en este proyecto

```
ui  → domain ← data
           ↑
         core
```

| Capa | Rol | Depende de |
|---|---|---|
| `ui` | Activities, Fragments, ViewModels, layouts XML | `domain` |
| `domain` | Entidades, interfaces de repositorio (contratos), `Result` | — (no depende de nada) |
| `data` | Implementaciones concretas (Retrofit, DTOs, mapeo) | `domain` |
| `core` | Infraestructura compartida: cliente HTTP/Supabase, utilidades | — |

> [!warning] Regla de oro
> `domain` **nunca** referencia `data`. La flecha de dependencia va en sentido contrario: `data` implementa los contratos que `domain` define. Así, `ui` solo conoce interfaces (`AuthRepository`), nunca la implementación concreta (`SupabaseAuthRepository`).

---

## Por qué importa en una app Android chica

En un proyecto de una sola persona/equipo chico es tentador meter todo en la `Activity`. El costo aparece cuando:
- Querés testear la lógica de login sin levantar un emulador → si vive en `domain`/`data` puro (Java, sin `android.*`), se puede testear con JUnit plano.
- Cambiás Retrofit por otra librería, o Supabase por otro backend → solo tocás `data`, `ui` no se entera porque solo conoce la interfaz de `domain`.

---

## Relaciones

- [[SOLID]] — principios que sostienen esta separación
- [[Capa de Dominio]]
- [[Repository Pattern]]
- [[Result Pattern]]
- [[Arquitectura Actual]]

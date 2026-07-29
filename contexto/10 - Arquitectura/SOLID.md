---
title: SOLID
tags:
  - arquitectura
  - conceptos
---

# SOLID

> [!abstract] Definición
> Cinco principios de diseño orientado a objetos que hacen el código más fácil de mantener y extender sin romper lo existente.

---

## Aplicados en este proyecto

| Principio | Ejemplo concreto en el proyecto |
|---|---|
| **S** — Responsabilidad única | `LoginViewModel` solo coordina estado de UI; `SupabaseAuthRepository` solo sabe hablar con la API de Auth |
| **O** — Abierto/cerrado | Agregar un nuevo método de login (ej. Google Sign-In) implica una nueva implementación de `AuthRepository`, no tocar la existente |
| **L** — Sustitución de Liskov | Cualquier implementación de `AuthRepository` (real, fake para tests) debe ser intercambiable sin romper el `ViewModel` |
| **I** — Segregación de interfaces | `AuthRepository` solo declara lo que el login necesita — no un mega-repositorio con 20 métodos sin relación |
| **D** — Inversión de dependencias | `LoginViewModel` depende de la interfaz `AuthRepository` (definida en `domain`), no de `SupabaseAuthRepository` (en `data`) |

---

## Relaciones

- [[Clean Architecture]]
- [[Repository Pattern]]
- [[Result Pattern]]

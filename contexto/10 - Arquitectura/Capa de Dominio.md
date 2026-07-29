---
title: Capa de Dominio (domain)
tags:
  - arquitectura
  - dominio
aliases:
  - domain
---

# Capa de Dominio (`domain`)

> [!abstract] Definición
> El corazón de la app: entidades y contratos que no saben nada de Android, de Retrofit ni de Supabase. Es Java plano, testeable sin emulador.

---

## Qué vive acá

- **Entidades**: `Sesion` (usuario autenticado: id, correo, access token).
- **Contratos (interfaces)**: `AuthRepository` — define *qué* se puede hacer (`login(correo, contrasenia)`), no *cómo*.
- **`Result`/`Result<T>`** — tipo de retorno para manejar éxito/fallo sin excepciones como flujo de control. Ver [[Result Pattern]].

## Qué NO vive acá

- Nada que importe `android.*` (excepto lo estrictamente necesario si en el futuro se usan anotaciones de `androidx.annotation`).
- Nada que importe Retrofit/OkHttp/Gson — eso es de `data`.
- Nada de `Activity`, `ViewModel`, layouts — eso es de `ui`.

---

## Relaciones

- [[Clean Architecture]]
- [[Repository Pattern]]
- [[Result Pattern]]
- [[Arquitectura Actual]]

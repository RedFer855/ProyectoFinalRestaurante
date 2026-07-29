---
title: "Caso 01 - Login con Supabase Auth"
tags:
  - caso-de-uso
date: 2026-07-29
---

# Caso 01 — Login con Supabase Auth

> [!abstract] Objetivo
> Autenticar un usuario contra Supabase Auth (email + contraseña) desde una app Android en Java, sin usar el SDK oficial de Supabase (que es Kotlin-first), consumiendo el endpoint REST directamente con Retrofit.

## Flujo end-to-end

```
LoginActivity
    → LoginViewModel.login(correo, contrasenia)
        → AuthRepository.login(correo, contrasenia)   [interfaz, domain]
            → SupabaseAuthRepository.login(...)        [implementación, data]
                → SupabaseAuthApi.login(dto)            [Retrofit, POST /auth/v1/token?grant_type=password]
                    → Supabase Auth (backend)
                ← LoginResponseDto (access_token, user)
            ← Result<Sesion>
        ← LiveData<EstadoLogin>
    ← Activity observa y navega a MainActivity si hay éxito
```

## Request

```
POST {SUPABASE_URL}/auth/v1/token?grant_type=password
Headers:
  apikey: {SUPABASE_ANON_KEY}
  Content-Type: application/json
Body:
  { "email": "...", "password": "..." }
```

## Response (éxito)

```json
{
  "access_token": "...",
  "token_type": "bearer",
  "expires_in": 3600,
  "refresh_token": "...",
  "user": { "id": "...", "email": "..." }
}
```

## Errores esperados

| Código HTTP | Causa | Cómo se maneja |
|---|---|---|
| 400 | Credenciales inválidas | `Result.fail("Correo o contraseña incorrectos")` |
| 401/403 | `apikey` mal configurada | `Result.fail("Error de configuración del servidor")` |
| Sin conexión | `IOException` de OkHttp | `Result.fail("Sin conexión a internet")` |

---

## Relaciones

- [[Módulo Login]]
- [[Supabase Auth REST - Login Android]]
- [[Repository Pattern]]
- [[Result Pattern]]
- [[MVVM en Android (ViewModel + LiveData)]]

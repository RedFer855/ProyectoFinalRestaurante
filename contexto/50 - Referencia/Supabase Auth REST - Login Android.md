---
title: "Supabase Auth REST - Login Android"
tags:
  - referencia
  - supabase
  - android
date: 2026-07-29
lifecycle: verified
---

# Supabase Auth REST — Login desde Android (Java)

> [!info] Fuente
> [Supabase Auth REST API](https://supabase.com/docs/reference/api/auth-signin) y [Understanding API keys](https://supabase.com/docs/guides/getting-started/api-keys). Verificado el 2026-07-29.

## El hecho

Supabase expone su Auth como una API REST estándar (GoTrue). Loguear con email+password es un `POST` simple, sin necesidad del SDK:

```
POST {SUPABASE_URL}/auth/v1/token?grant_type=password
Headers:
  apikey: {LLAVE_PUBLISHABLE}
  Content-Type: application/json
Body: { "email": "...", "password": "..." }
```

Respuesta de éxito:

```json
{
  "access_token": "...",
  "token_type": "bearer",
  "expires_in": 3600,
  "refresh_token": "...",
  "user": { "id": "...", "email": "..." }
}
```

## Por qué importa acá

Permite implementar el login en Java puro con Retrofit, sin traer el SDK Kotlin de Supabase — ver [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]] y [[Librerias Java-Friendly vs Kotlin-Only]].

---

## ⚠️ Llaves: usar las nuevas (`sb_publishable_`)

| Tipo | Formato | ¿Va en la app? |
|---|---|---|
| **Publishable** (reemplaza `anon`) | `sb_publishable_...` | ✅ Sí |
| **Secret** (reemplaza `service_role`) | `sb_secret_...` | 🔴 **JAMÁS** — va en una Edge Function |

- Las llaves legadas `anon`/`service_role` **se deprecan a finales de 2026** y luego se eliminan. Los proyectos creados desde noviembre de 2025 ya no las reciben.
- En **código nuevo se usa la llave publishable.** Ver [[Seguridad y Privacidad Android]].

> [!danger] RLS obligatoria
> La llave publishable está dentro del APK y cualquiera puede extraerla. **Lo único que protege los datos es Row Level Security.** Toda tabla se crea con sus policies en la misma migración.

## Configuración (`local.properties`, NO versionado)

```properties
SUPABASE_URL=https://TU-PROYECTO.supabase.co
SUPABASE_ANON_KEY=sb_publishable_...
```

Se exponen a Java vía `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY` (ver `app/build.gradle.kts`). Sin un proyecto Supabase real y estos valores completados, el login siempre falla.

> [!note] Nombre de la constante
> La constante todavía se llama `SUPABASE_ANON_KEY` por herencia del nombre legado. El **valor** que debe ponerse es la llave `sb_publishable_...`. Renombrarla a `SUPABASE_PUBLISHABLE_KEY` está registrado como **P-012** en [[Deuda Técnica - Pendientes]].

## Ejemplo (Retrofit)

```java
public interface SupabaseAuthApi {
    @POST("auth/v1/token?grant_type=password")
    Call<LoginResponseDto> login(@Body LoginRequestDto body);
}
```

## Errores esperados

| Código | Causa | Manejo |
|---|---|---|
| 400 | Credenciales inválidas | `Result.fail("Correo o contraseña incorrectos")` |
| 401/403 | `apikey` mal configurada | `Result.fail("Error de configuración del servidor")` |
| `IOException` | Sin conexión | `Result.fail("Sin conexión al servidor")` |

## Pendiente: refresh de token

El endpoint REST devuelve `refresh_token` y `expires_in`, pero **el refresh no está implementado** — al consumirlo directo (sin SDK) hay que hacerlo a mano contra `/auth/v1/token?grant_type=refresh_token`. Ver [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]] y **P-009** en [[Deuda Técnica - Pendientes]].

---

## Relaciones

- [[Módulo Login]]
- [[Caso 01 - Login con Supabase Auth]]
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]]
- [[Seguridad y Privacidad Android]]
- [[Librerias Java-Friendly vs Kotlin-Only]]

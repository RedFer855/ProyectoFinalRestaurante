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
> Documentación oficial de Supabase Auth (REST API) — https://supabase.com/docs/reference/api/auth-signin — verificado contra el endpoint `grant_type=password`.

## El hecho

Supabase expone su Auth como una API REST estándar (GoTrue). Loguear un usuario con email+password es un `POST` simple, sin necesidad del SDK:

```
POST {SUPABASE_URL}/auth/v1/token?grant_type=password
Headers:
  apikey: {SUPABASE_ANON_KEY}
  Content-Type: application/json
Body: { "email": "...", "password": "..." }
```

## Por qué importa acá

Permite implementar el login en Java puro con Retrofit, sin traer el SDK Kotlin de Supabase — ver [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]].

## Configuración requerida (`local.properties`, NO versionado)

```properties
SUPABASE_URL=https://TU-PROYECTO.supabase.co
SUPABASE_ANON_KEY=tu-anon-key-publica
```

Estos valores se exponen a Java vía `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY` (ver `app/build.gradle.kts`). Sin un proyecto Supabase real creado en [supabase.com](https://supabase.com) y estos valores completados, el login siempre falla.

> [!warning] Nunca commitear la key
> `local.properties` ya está en `.gitignore`. La `anon key` es pública por diseño (se usa desde clientes), pero igual no debe versionarse junto con otras configuraciones locales del entorno.

## Ejemplo (Retrofit)

```java
public interface SupabaseAuthApi {
    @POST("auth/v1/token?grant_type=password")
    Call<LoginResponseDto> login(@Body LoginRequestDto body);
}
```

---

## Relaciones

- [[Módulo Login]]
- [[Caso 01 - Login con Supabase Auth]]
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]]

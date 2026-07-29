---
title: Módulo Login
tags:
  - restaurante
  - modulo
  - login
date: 2026-07-29
---

# Módulo Login

> [!info] Patrón de referencia
> Este es el primer módulo del proyecto — sirve de plantilla de arquitectura para los módulos de fases siguientes (Menú, Pedidos, etc.).

## Archivos clave

### `domain/`
```
model/Sesion.java              — entidad: id de usuario, correo, access token
repository/AuthRepository.java — interfaz: Result<Sesion> login(correo, contrasenia)
Result.java                    — tipo Result/Result<T>
```

### `data/`
```
remote/SupabaseAuthApi.java         — interfaz Retrofit
remote/dto/LoginRequestDto.java     — { email, password }
remote/dto/LoginResponseDto.java    — { access_token, user{ id, email } }
repository/SupabaseAuthRepository.java — implementa AuthRepository
```

### `core/`
```
SupabaseClient.java — Retrofit singleton, base URL + header apikey desde BuildConfig
```

### `ui/login/`
```
LoginActivity.java     — infla activity_login.xml, observa el ViewModel
LoginViewModel.java    — expone LiveData<EstadoLogin>
EstadoLogin.java       — objeto de estado único (cargando / error / sesión)
LoginViewModelFactory.java — DI manual: construye LoginViewModel con su AuthRepository
```

## Layout

`res/layout/activity_login.xml` — campos correo/contraseña, botón de login, `ProgressBar`, `TextView` de error.

## Flujo

Ver [[Caso 01 - Login con Supabase Auth]] para el diagrama completo request/response.

## Pendiente antes de que funcione de verdad

> [!warning]
> `local.properties` tiene `SUPABASE_URL` y `SUPABASE_ANON_KEY` vacíos por defecto. Hay que crear un proyecto en [supabase.com](https://supabase.com) y completar esos dos valores — ver [[Supabase Auth REST - Login Android]]. Sin eso, el login siempre falla con "sin conexión"/URL inválida.

---

## Relaciones

- [[Arquitectura Actual]]
- [[Caso 01 - Login con Supabase Auth]]
- [[Repository Pattern]]
- [[Result Pattern]]
- [[MVVM en Android (ViewModel + LiveData)]]
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]]

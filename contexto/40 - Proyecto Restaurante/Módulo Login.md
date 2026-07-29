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
> `local.properties` tiene `SUPABASE_URL` y `SUPABASE_ANON_KEY` vacíos por defecto. Hay que crear un proyecto en [supabase.com](https://supabase.com) y completar esos dos valores, usando la llave **publishable** (`sb_publishable_...`), no la `anon` legada — ver [[Supabase Auth REST - Login Android]]. Sin eso, el login siempre falla.

## Deuda de este módulo

> [!danger] Este módulo **no pasa** el [[Gate de Autoverificación]]
> Se escribió antes de adoptar el [[Estándar de Ingeniería Android]]. Antes de replicar sus patrones a un módulo nuevo, leer esta lista:

| Ítem | Qué falta |
|---|---|
| 🔴 **P-004** | `LoginActivity` no maneja edge-to-edge ni insets (obligatorio con `targetSdk 36+`) |
| 🟡 **P-005** | El `Executor` se crea dentro del ViewModel → intesteable; **el módulo no tiene ni un test** |
| 🟡 **P-010** | Sin `contentDescription`, sin `labelFor`, sin `TextInputLayout` |
| 🟡 **P-013** | El evento de navegación no se marca como consumido |
| 🟡 **P-015** | `Activity` + `findViewById` en vez de `Fragment` + ViewBinding + Navigation Component |
| 🟡 **P-016** | `Result` transporta un `String`, no un `AppException` tipado |
| 🟢 **P-009** | El `access_token` se descarta: no se persiste, no se cifra, no hay refresh |
| 🟢 **P-011** | IDs en `snake_case` y color de error hardcodeado en el layout |

Detalle completo en [[Deuda Técnica - Pendientes]]. La remediación es la **Fase 0** de [[Roadmap de Fases]].

---

## Relaciones

- [[Arquitectura Actual]]
- [[Caso 01 - Login con Supabase Auth]]
- [[Repository Pattern]]
- [[Result Pattern]]
- [[MVVM en Android (ViewModel + LiveData)]]
- [[UiState Inmutable y Flujo Unidireccional]]
- [[Asincronia en Java para Android]]
- [[Gate de Autoverificación]]
- [[Deuda Técnica - Pendientes]]
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]]

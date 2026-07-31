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
remote/SupabaseAuthApi.java         — interfaz Retrofit: login (token?grant_type=password), logout
remote/SupabasePerfilApi.java       — interfaz Retrofit: GET rest/v1/perfiles (PostgREST)
remote/dto/LoginRequestDto.java     — { email, password }
remote/dto/LoginResponseDto.java    — { access_token, user{ id, email } }
remote/dto/PerfilDto.java           — { nombre, rol, activo }
repository/SupabaseAuthRepository.java — implementa AuthRepository; login + verificación de perfil activo
```

### `core/`
```
SupabaseClient.java — Retrofit singleton, base URL + header apikey desde BuildConfig; expone getAuthApi() y getPerfilApi()
```

### `ui/login/`
```
LoginActivity.java     — infla activity_login.xml, observa el ViewModel
LoginViewModel.java    — expone LiveData<EstadoLogin>
EstadoLogin.java       — objeto de estado único (cargando / error / sesión)
LoginViewModelFactory.java — DI manual: construye LoginViewModel con su AuthRepository
```

## Layout

`res/layout/activity_login.xml` — rehecho el **2026-07-29** con Material 3, solo correo y contraseña, sin efectos visuales:

```
NestedScrollView (login_root, fillViewport)   ← recibe los insets
└── LinearLayout vertical (padding @dimen/espaciado_pantalla)
    ├── TextView  txt_titulo_login        ?attr/textAppearanceHeadlineSmall
    ├── TextInputLayout til_correo        > TextInputEditText txt_correo
    ├── TextInputLayout til_contrasenia   > TextInputEditText txt_contrasenia (password_toggle)
    ├── TextView  txt_error_login         ?attr/colorError · accessibilityLiveRegion
    ├── MaterialButton btn_login          minHeight @dimen/altura_minima_tactil
    └── ProgressBar progress_login        contentDescription
```

Cero strings, colores y `dp` hardcodeados: todo sale de `strings.xml`, `dimens.xml` o de atributos del tema Material 3. Los IDs siguen en `snake_case` (pendiente de **P-011**) para no tocar `LoginActivity`.

## Flujo

Ver [[Caso 01 - Login con Supabase Auth]] para el diagrama completo request/response.

**Flujo real desde el 2026-07-29** (Propuesta A + verificación de perfil, ver [[Plan de Conexión con Supabase]]):

1. `POST auth/v1/token?grant_type=password` → `access_token` + `user.id`.
2. Con ese token: `GET rest/v1/perfiles?id=eq.<user.id>&select=nombre,rol,activo`.
3. Si no hay fila, o `activo = false` → `POST auth/v1/logout` (revoca el token recién emitido) y `Result.fail(...)`.
4. Si el perfil existe y está activo → `Sesion` con `rol` incluido, login exitoso.

Mismo patrón que `AuthService.LoginAsync` del proyecto Bimbo (verificación de cuenta activa antes de dejar entrar) — ver la sección 7 de [[Seguridad y Privacidad Android]].

## Conexión con Supabase — estado real

> [!success] Conectado el 2026-07-29
> Proyecto **Restaurante** (`mxarlisuueovxvttytcm`). `local.properties` tiene `SUPABASE_URL`/`SUPABASE_ANON_KEY` reales (llave `anon` legada — el proyecto todavía no tiene una `sb_publishable_...` generada, ver **P-012**). Tabla `public.perfiles` creada con RLS (`select` solo para `authenticated`, revocado para `anon`).
>
> **Verificado end-to-end el 2026-07-31** en emulador: login real con el usuario admin → navega a `MainActivity`. Ver [[Sesión 2026-07-31 - Primer login verificado en emulador]]. En el camino se encontró y arregló **P-022** (faltaba el permiso `INTERNET`, causaba un crash real).

## Deuda de este módulo

> [!danger] Este módulo **no pasa** el [[Gate de Autoverificación]]
> Se escribió antes de adoptar el [[Estándar de Ingeniería Android]]. Antes de replicar sus patrones a un módulo nuevo, leer esta lista:

| Ítem | Qué falta |
|---|---|
| ~~🔴 **P-004**~~ | ✅ Resuelto 2026-07-29 — `EdgeToEdge.enable()` + insets de barras del sistema **y** teclado |
| 🟡 **P-005** | El `Executor` se crea dentro del ViewModel → intesteable; **el módulo no tiene ni un test** |
| 🟡 **P-010** | 🟡 Parcial — ya usa `TextInputLayout` y `contentDescription`; falta TalkBack, fuente 200 % y `setError` por campo |
| 🟢 **P-019** | Mensajes de error hardcodeados en `LoginViewModel` y `SupabaseAuthRepository` |
| 🟡 **P-013** | El evento de navegación no se marca como consumido |
| 🟡 **P-015** | `Activity` + `findViewById` en vez de `Fragment` + ViewBinding + Navigation Component |
| 🟡 **P-016** | `Result` transporta un `String`, no un `AppException` tipado |
| 🟢 **P-009** | El `access_token` se descarta tras el login: no se persiste, no se cifra, no hay refresh — la sesión no sobrevive a cerrar la app |
| 🟢 **P-011** | 🟡 Parcial — color y `dp` ya salen del tema/`dimens.xml`; los IDs siguen en `snake_case` |
| 🟡 **P-020** | `SupabaseAuthRepository` (login + verificación de perfil + logout) **sin ningún test** — mismo problema de fondo que P-005 |

Detalle completo en [[Deuda Técnica - Pendientes]]. La remediación es la **Fase 0** de [[Roadmap de Fases]].

---

## Relaciones

- [[Plan de Conexión con Supabase]]
- [[Propuesta de División de Arquitectura]]
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

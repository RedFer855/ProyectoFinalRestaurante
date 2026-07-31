---
title: Plan de Fase 1 — Roles, Autenticación y Recuperación de Contraseña
tags:
  - restaurante
  - plan
  - fase1
  - auth
  - roles
date: 2026-07-31
lifecycle: draft
---

# Plan de Fase 1 — Roles, Autenticación y Recuperación de Contraseña

> [!info] Alcance
> La Fase 1 se redefinió como **"Autenticación, roles y recuperación de contraseña"**. Esta nota cubre lo que falta para cerrarla. Lo ya hecho está en [[Módulo Login]] y [[Sesión 2026-07-31 - Primer login verificado en emulador]].

---

## Estado de partida (verificado 2026-07-31)

| Parte | Estado |
|---|---|
| **Autenticación** (login email+password) | 🟢 Funciona end-to-end, verificado en emulador |
| Verificación de perfil activo tras login | 🟢 Implementado (`SupabaseAuthRepository`) |
| **Roles** | 🟡 `Sesion.getRol()` existe y trae `'admin'`, pero **nada en la UI lo usa** |
| **Recuperación de contraseña** | 🔴 No existe: ni pantalla, ni endpoint, ni método de repositorio |
| Persistencia de sesión | 🔴 Se pierde al cerrar la app (**P-009**) |
| Menú hamburguesa | 🔴 No existe |

---

## 1. Recuperación de contraseña

### Decisión de diseño: OTP de 6 dígitos, no deep link

> [!info] Fuentes verificadas el 2026-07-31
> [supabase/auth README](https://github.com/supabase/auth) · [Email templates](https://supabase.com/docs/guides/auth/auth-email-templates) · [Password security](https://supabase.com/docs/guides/auth/password-security)

Supabase ofrece dos caminos para recuperar contraseña:

| Camino | Cómo funciona | ¿Sirve acá? |
|---|---|---|
| **Link (`{{ .ConfirmationURL }}` / `{{ .TokenHash }}`)** | El correo trae un link que abre la app vía deep link | ❌ Requiere `intent-filter` con `android:scheme` + Site URL configurada. La app **no tiene ningún deep link** hoy. |
| **OTP (`{{ .Token }}`)** ← **elegido** | El correo trae un **código de 6 dígitos** que el usuario tipea en la app | ✅ Cero infraestructura nueva. Es el mismo patrón que usó Bimbo (`ForgotCodePanel`). |

Cita textual de la doc: *"`{{ .Token }}` contains a 6-digit One-Time-Password (OTP) that can be used instead of the `{{ .ConfirmationURL }}`"*. Supabase además lo **recomienda** para mitigar el problema de *email prefetching* (clientes de correo que "clickean" los links automáticamente e invalidan el token antes de que el usuario lo use).

### Flujo REST (3 llamadas)

```
1. Usuario pide recuperar
   POST /auth/v1/recover
   Headers: apikey, Content-Type
   Body:    { "email": "..." }
   → {}   (siempre 200, aunque el correo no exista — ver nota de seguridad)

2. Usuario tipea el código de 6 dígitos que le llegó
   POST /auth/v1/verify
   Headers: apikey, Content-Type
   Body:    { "type": "recovery", "email": "...", "token": "123456" }
   → { access_token, refresh_token, expires_in, type: "recovery" }

3. Con ese access_token, se fija la contraseña nueva
   PUT /auth/v1/user
   Headers: apikey, Authorization: Bearer <access_token>
   Body:    { "password": "nueva" }
   → objeto user actualizado

4. Inmediatamente después: POST /auth/v1/logout
   (revoca la sesión temporal — el usuario vuelve a loguearse con su clave nueva)
```

### Reglas de seguridad que se aplican

1. **Nunca revelar si un correo existe.** El paso 1 siempre muestra el mismo mensaje ("Si el correo está registrado, te enviamos un código"), gane o falle. Enumerar cuentas es una fuga clásica.
2. **`logout` tras cambiar la contraseña** — mismo patrón que Bimbo (`SignOut()` post-update, documentado en su `Plan de Seguridad`). Fuerza a reautenticar y revoca la sesión temporal del OTP.
3. **Rate limit del lado servidor:** *"recovery links can only be sent once every 60 seconds"*. La UI debe deshabilitar el botón "Reenviar código" con un contador de 60 s en vez de dejar que el usuario reciba errores.
4. **Mensajes de error genéricos** — nunca mostrar el `ex.getMessage()` crudo de Supabase (regla ya documentada en [[Seguridad y Privacidad Android]], sección 7, lección de Bimbo).
5. **Sin logs de tokens ni códigos**, ni truncados.

### Configuración manual requerida (dashboard — la hace el dueño del proyecto)

| Dónde | Qué |
|---|---|
| **Authentication → Emails → Reset Password** | Editar la plantilla para que incluya `{{ .Token }}` (el código) en vez de solo el link. Ej. asunto: `{{ .Token }} es tu código de recuperación` |
| **Authentication → Providers → Email** | Verificar que "Confirm email" esté como se quiere y revisar el tiempo de expiración del OTP (recomendado ≤ 3600 s) |
| **Authentication → Policies** | **Longitud mínima 8+** y requerir dígitos + mayúsculas + minúsculas + símbolos. La validación en la app es UX; **la que cuenta es la del servidor** |
| **(Plan Pro)** | Activar *Leaked Password Protection* (HaveIBeenPwned) si el plan lo permite |

### Archivos a crear (siguiendo el patrón existente)

```
domain/repository/AuthRepository.java        (+3 métodos)
    Result<Void>   solicitarRecuperacion(String correo)
    Result<String> verificarCodigo(String correo, String codigo)   → devuelve access_token
    Result<Void>   cambiarContrasenia(String accessToken, String nueva)

data/remote/SupabaseAuthApi.java             (+3 endpoints)
    @POST("auth/v1/recover")      Call<Void>              recuperar(@Body RecuperarRequestDto)
    @POST("auth/v1/verify")       Call<VerifyResponseDto> verificar(@Body VerifyRequestDto)
    @PUT ("auth/v1/user")         Call<Void>              cambiarContrasenia(@Header("Authorization") String bearer, @Body CambiarContraseniaDto)

data/remote/dto/  → RecuperarRequestDto, VerifyRequestDto, VerifyResponseDto, CambiarContraseniaDto
data/repository/SupabaseAuthRepository.java  (implementa los 3, con Result y catch de IOException/SecurityException)

ui/recuperacion/
    RecuperarContraseniaActivity.java   — paso 1: correo
    CodigoYNuevaContraseniaActivity.java — pasos 2+3: código + nueva contraseña
    EstadoRecuperacion.java             — estado único inmutable
    RecuperacionViewModel.java          — un ViewModel para todo el flujo
    RecuperacionViewModelFactory.java
```

---

## 2. Roles en la UI

### Principio no negociable

> [!danger] El rol del cliente es solo para la UI
> `Sesion.getRol()` sirve para **mostrar u ocultar** opciones, nunca como control de seguridad. Cualquiera puede modificar un APK. **La seguridad real son las policies RLS de Postgres**, que se evalúan del lado del servidor con el JWT del usuario. Toda tabla nueva debe tener su policy que restrinja por rol — ver [[Esquema de Base de Datos]].

### Qué falta

1. **Un lugar donde viva la sesión activa.** Hoy `Sesion` se crea en el login y se pierde. Mínimo: un holder en memoria (`core/SesionActual.java`) o pasarla por `Intent`. Lo correcto a mediano plazo es persistirla cifrada (**P-009**, Propuesta B de [[Plan de Conexión con Supabase]]).
2. **Menú hamburguesa (`DrawerLayout` + `NavigationView`)** en la pantalla principal, con los ítems filtrados por rol:

| Ítem del menú | admin | mesero | cocina |
|---|---|---|---|
| Inicio / Dashboard | ✅ | ✅ | ✅ |
| Pedidos | ✅ | ✅ | ✅ (solo ver/actualizar estado) |
| Mesas | ✅ | ✅ | ❌ |
| Menú (platillos/categorías) | ✅ | 👁 solo lectura | 👁 solo lectura |
| Clientes | ✅ | ✅ | ❌ |
| Empleados / Usuarios | ✅ | ❌ | ❌ |
| Reportes | ✅ | ❌ | ❌ |
| Cerrar sesión | ✅ | ✅ | ✅ |

3. **Cabecera del drawer** con nombre y rol del usuario logueado (dato que ya viene en `Sesion`).

> [!note] Los ítems de módulos no construidos
> Menú, Pedidos, Mesas, etc. son Fases 2-6. En la Fase 1 el drawer puede mostrarlos **deshabilitados o llevando a un placeholder** — lo importante es que la estructura de navegación y el filtrado por rol queden listos y probados.

---

## 3. Orden sugerido de implementación

| # | Tarea | Por qué en este orden |
|---|---|---|
| 1 | `SesionActual` + drawer con filtrado por rol | Desbloquea "roles", y el drawer es el esqueleto de navegación de todas las fases siguientes |
| 2 | Recuperación de contraseña (3 endpoints + 2 pantallas) | Autocontenido, no depende de lo anterior |
| 3 | Configuración del dashboard (plantilla OTP + password policy) | Manual, se puede hacer en paralelo |
| 4 | Tests del repositorio (**P-020**) | Cierra el gate: hay 4 caminos de error nuevos por flujo |

> [!warning] Antes de dar la fase por cerrada
> **P-003** (`minSdk 37 → 24`) sigue abierto: la app **no instala en ningún teléfono real**, solo en emulador. Es Fase 0 en [[Roadmap de Fases]] y bloquea cualquier entrega demostrable.

---

## Relaciones

- [[Módulo Login]]
- [[Guía de Diseño Visual]]
- [[Plan de Conexión con Supabase]]
- [[Seguridad y Privacidad Android]]
- [[Esquema de Base de Datos]]
- [[Deuda Técnica - Pendientes]]
- [[Roadmap de Fases]]
- [[UiState Inmutable y Flujo Unidireccional]]

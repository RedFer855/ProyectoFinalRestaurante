---
title: Plan Fase 1b — Recuperación de Contraseña y Roles
tags:
  - restaurante
  - plan
  - fase1b
  - auth
  - roles
date: 2026-07-31
lifecycle: draft
---

# Plan Fase 1b — Recuperación de Contraseña y Roles

> [!important] Cómo se ejecuta este plan
> Se entrega **un `Entregable` a la vez**. Al terminar cada uno, se detiene, se revisa y recién entonces se sigue con el siguiente. No se adelanta trabajo de entregables posteriores.

---

## Objetivo principal

Completar la Fase 1 (*"Autenticación, roles y recuperación de contraseña"*) agregando las dos piezas que faltan: **(a)** un flujo de recuperación de contraseña por código de 6 dígitos, y **(b)** que el rol del usuario autenticado condicione lo que ve en la app.

El objetivo secundario, igual de importante: que **todo el código sea escribible y verificable sin acceso al panel de Supabase**. El contrato REST está documentado abajo; quien programe trabaja contra ese contrato, no contra el dashboard.

---

## 🔴 Fuera de alcance de este plan — solo el dueño del proyecto

Estas tareas **no las hace quien programa**. Requieren el panel de Supabase y quedan como prerequisito para la prueba end-to-end final:

| # | Tarea | Dónde |
|---|---|---|
| S-1 | Editar la plantilla de correo de recuperación para que incluya **`{{ .Token }}`** (el código de 6 dígitos) además del link | Authentication → Emails → Reset Password |
| S-2 | Fijar la política de contraseñas: **mínimo 8 caracteres** + requerir dígitos, mayúsculas, minúsculas y símbolos | Authentication → Policies |
| S-3 | Revisar el tiempo de expiración del OTP (recomendado ≤ 3600 s) | Authentication → Providers → Email |
| S-4 | *(Opcional, plan Pro)* activar **Leaked Password Protection** (HaveIBeenPwned) | Authentication → Policies |

> [!warning] Consecuencia
> Sin **S-1**, el correo de recuperación llega **sin código** y el flujo no se puede probar de punta a punta — aunque el código de la app esté perfecto. Todo lo demás (compilación, pruebas unitarias, navegación, estados de error) **sí se verifica sin Supabase**.

---

## Contrato REST verificado

> [!info] Fuentes — verificadas el 2026-07-31
> [supabase/auth (GoTrue) README](https://github.com/supabase/auth) · [Email Templates](https://supabase.com/docs/guides/auth/auth-email-templates) · [Password Security](https://supabase.com/docs/guides/auth/password-security)

Todas las llamadas llevan los headers que ya inyecta `SupabaseClient` (`apikey`, `Content-Type: application/json`).

| # | Operación | Request | Response |
|---|---|---|---|
| 1 | Pedir código | `POST /auth/v1/recover`<br>`{"email": "..."}` | `{}` — **200 aunque el correo no exista** |
| 2 | Verificar código | `POST /auth/v1/verify`<br>`{"type":"recovery","email":"...","token":"123456"}` | `{access_token, refresh_token, expires_in, type:"recovery"}` |
| 3 | Fijar contraseña nueva | `PUT /auth/v1/user`<br>Header `Authorization: Bearer <access_token>`<br>`{"password":"..."}` | objeto `user` actualizado |
| 4 | Cerrar la sesión temporal | `POST /auth/v1/logout` + `Bearer` | 204 — **ya implementado** en `SupabaseAuthApi` |

**Hechos que condicionan el diseño:**

1. **`{{ .Token }}` es un código de 6 dígitos.** Cita: *"contains a 6-digit One-Time-Password (OTP) that can be used instead of the `{{ .ConfirmationURL }}`"*. Por eso se eligió OTP y no deep link: la app **no tiene ningún `intent-filter` con scheme** y agregarlo sería infraestructura nueva.
2. **Rate limit del servidor: un correo de recuperación cada 60 segundos.** Cita: *"recovery links can only be sent once every 60 seconds"*. La UI debe impedirlo antes de que el servidor lo rechace.
3. El paso 2 devuelve una **sesión temporal real**. Hay que cerrarla (paso 4) apenas se cambia la contraseña.
4. La validación de fuerza de contraseña en la app es **UX**; la que manda es la del servidor (S-2).

---

## Entregables

### Entregable 1 — Capa de datos de recuperación

**Objetivo:** que la app pueda hablar los 3 endpoints nuevos, sin ninguna pantalla todavía.

**Archivos a crear:**
```
data/remote/dto/RecuperarRequestDto.java          { email }
data/remote/dto/VerificarCodigoRequestDto.java    { type, email, token }
data/remote/dto/VerificarCodigoResponseDto.java   { access_token }
data/remote/dto/CambiarContraseniaRequestDto.java { password }
```

**Archivos a modificar:**
- `data/remote/SupabaseAuthApi.java` → agregar los 3 endpoints (`@POST recover`, `@POST verify`, `@PUT user`)
- `domain/repository/AuthRepository.java` → agregar 3 métodos:
  ```java
  Result<Void>   solicitarCodigo(String correo);
  Result<String> verificarCodigo(String correo, String codigo);  // devuelve access_token
  Result<Void>   cambiarContrasenia(String accessToken, String nuevaContrasenia);
  ```
- `data/repository/SupabaseAuthRepository.java` → implementarlos

**Reglas obligatorias:**
- Todo devuelve `Result<T>` — ninguna excepción cruza a la UI.
- `catch (IOException)` **y** `catch (SecurityException)` en cada método (lección de **P-022**: `SecurityException` no es `IOException` y tumbó la app).
- `cambiarContrasenia()` llama `logout(bearer)` inmediatamente después de un cambio exitoso.
- `solicitarCodigo()` devuelve `Result.ok()` **también** cuando el servidor responde error de "correo no encontrado" — nunca se revela si una cuenta existe.
- Mensajes de error fijos y genéricos, nunca `ex.getMessage()`.

**Criterio de aceptación:** `./gradlew assembleDebug` → BUILD SUCCESSFUL. Sin pantallas ni cambios de UI.

---

### Entregable 2 — Validador de contraseña (Java puro)

**Objetivo:** una clase sin dependencias de Android, testeable con JUnit, que valide la fuerza de una contraseña **con las mismas reglas que S-2**.

**Archivos a crear:**
```
domain/ValidadorContrasenia.java          — mínimo 8, ≥1 dígito, ≥1 mayúscula, ≥1 minúscula, ≥1 símbolo
domain/ResultadoValidacion.java           — qué requisitos cumple y cuáles no (para pintarlos en la UI)
app/src/test/java/.../ValidadorContraseniaTest.java
```

**Por qué separado:** es la única pieza de este plan **100 % testeable sin red, sin Android y sin Supabase**. Sirve de red de seguridad y empieza a cerrar **P-020** (cero tests en el proyecto).

**Criterio de aceptación:** `./gradlew testDebugUnitTest` verde, con casos para cada requisito incumplido, contraseña válida, vacía y `null`.

---

### Entregable 3 — Pantalla "¿Olvidaste tu contraseña?" (paso 1 de 2)

**Objetivo:** pedir el correo y disparar el envío del código.

**Archivos a crear:**
```
ui/recuperacion/SolicitarCodigoActivity.java
ui/recuperacion/SolicitarCodigoViewModel.java
ui/recuperacion/SolicitarCodigoViewModelFactory.java
ui/recuperacion/EstadoSolicitudCodigo.java        — estado único inmutable
res/layout/activity_solicitar_codigo.xml
```

**Archivos a modificar:**
- `res/layout/activity_login.xml` → agregar el `TextButton` "¿Olvidaste tu contraseña?"
- `ui/login/LoginActivity.java` → navegar a la pantalla nueva
- `AndroidManifest.xml` → registrar la Activity
- `res/values/strings.xml` → textos nuevos

**Diseño:** según [[Guía de Diseño Visual]] — título, párrafo explicativo, un campo de correo, botón de ancho completo, flecha de volver.

**Reglas:**
- Mensaje de confirmación **idéntico** exista o no el correo.
- `Executor` inyectado en el ViewModel (no crearlo adentro — es la deuda **P-005**, no replicarla).
- Estado único inmutable, no `LiveData` sueltas ([[UiState Inmutable y Flujo Unidireccional]]).

**Criterio de aceptación:** compila; se navega desde login; con `local.properties` vacío o sin red se ve el estado de error (no crashea).

---

### Entregable 4 — Pantalla "Código y nueva contraseña" (paso 2 de 2)

**Objetivo:** verificar el código y fijar la contraseña nueva.

**Archivos a crear:**
```
ui/recuperacion/CambiarContraseniaActivity.java
ui/recuperacion/CambiarContraseniaViewModel.java
ui/recuperacion/CambiarContraseniaViewModelFactory.java
ui/recuperacion/EstadoCambioContrasenia.java
res/layout/activity_cambiar_contrasenia.xml
```

**Contenido de pantalla:** campo de código de 6 dígitos (teclado numérico), botón "Reenviar código" **deshabilitado con cuenta regresiva de 60 s**, campos de contraseña nueva y confirmación, lista de requisitos que se marcan en vivo usando el `ValidadorContrasenia` del Entregable 2, botón "Cambiar contraseña" deshabilitado hasta que todo sea válido.

**Reglas:**
- Al éxito: `Snackbar` + volver al login **con la pila limpia** (`FLAG_ACTIVITY_CLEAR_TASK`), porque la sesión temporal ya fue revocada.
- El contador de 60 s debe sobrevivir a rotación de pantalla (vive en el ViewModel, no en la Activity).
- Nunca loguear el código ni el `access_token`, ni truncados.

**Criterio de aceptación:** compila; con un código inválido muestra error sin crashear; el botón permanece deshabilitado mientras la contraseña no cumpla los requisitos.

---

### Entregable 5 — Sesión activa en memoria

**Objetivo:** que la `Sesion` (con su `rol`) siga viva después del login, en vez de perderse.

**Archivos a crear:**
```
core/SesionActual.java     — holder en memoria: guardar(Sesion) / obtener() / limpiar()
```

**Archivos a modificar:**
- `ui/login/LoginActivity.java` → guardar la sesión al entrar
- `MainActivity.java` → leerla; si no hay sesión, volver al login

**Alcance deliberadamente corto:** es un holder **en memoria**, se pierde al cerrar la app. La persistencia cifrada es **P-009** y es otro trabajo (Propuesta B de [[Plan de Conexión con Supabase]]). Este entregable solo desbloquea los roles.

**Criterio de aceptación:** tras loguearse, `MainActivity` puede leer nombre y rol; matando la app se vuelve a pedir login.

---

### Entregable 6 — Menú hamburguesa filtrado por rol

**Objetivo:** que lo que ve el usuario dependa de su rol.

**Archivos a crear:**
```
res/layout/nav_header.xml          — iniciales, nombre y rol
res/menu/menu_navegacion.xml       — todos los ítems posibles
res/drawable/ic_*.xml              — iconos vectoriales
```

**Archivos a modificar:**
- `res/layout/activity_main.xml` → `DrawerLayout` + `MaterialToolbar` + `NavigationView`
- `MainActivity.java` → ocultar ítems según `SesionActual.obtener().getRol()`

**Matriz de visibilidad:**

| Ítem | admin | mesero | cocina |
|---|:--:|:--:|:--:|
| Inicio | ✅ | ✅ | ✅ |
| Pedidos | ✅ | ✅ | ✅ |
| Mesas | ✅ | ✅ | ❌ |
| Menú | ✅ | ✅ | ❌ |
| Clientes | ✅ | ✅ | ❌ |
| Empleados | ✅ | ❌ | ❌ |
| Reportes | ✅ | ❌ | ❌ |
| Cerrar sesión | ✅ | ✅ | ✅ |

> [!danger] El rol del cliente es solo cosmético
> Ocultar un ítem del menú **no es seguridad** — un APK se puede modificar. La seguridad real son las **policies RLS** de Postgres, que se evalúan en el servidor con el JWT del usuario. Cada tabla que se agregue en las fases siguientes necesita su policy por rol. Ver [[Esquema de Base de Datos]].

**Los módulos no construidos** (Menú, Pedidos, Mesas…) llevan a un placeholder — lo que se entrega es la **estructura de navegación y el filtrado**, no los módulos.

**Criterio de aceptación:** entrando como `admin` se ven los 8 ítems; el filtrado es verificable cambiando el rol en `perfiles` (o simulándolo en un test).

---

## Resumen de entregables

| # | Entregable | Depende de | ¿Necesita Supabase? |
|---|---|---|---|
| 1 | Capa de datos de recuperación | — | ❌ No |
| 2 | Validador de contraseña + tests | — | ❌ No |
| 3 | Pantalla paso 1 (correo) | E1 | ❌ No |
| 4 | Pantalla paso 2 (código + clave) | E1, E2, E3 | ❌ No |
| 5 | Sesión activa en memoria | — | ❌ No |
| 6 | Drawer filtrado por rol | E5 | ❌ No |
| — | **Prueba end-to-end del flujo** | E1-E4 | ✅ **Sí — requiere S-1** |

---

## Riesgos conocidos

| Riesgo | Mitigación |
|---|---|
| **P-003** (`minSdk 37`) — la app no instala en teléfonos reales | Todo se prueba en emulador; arreglarlo es Fase 0 |
| Sin **S-1**, el correo llega sin código | El código igual se escribe y compila; solo la prueba final queda bloqueada |
| Replicar la deuda del login (Executor no inyectado, strings hardcodeados) | Cada entregable dice explícitamente qué patrón **no** copiar |
| El rate limit de 60 s confunde en pruebas | La UI lo hace visible con el contador; documentado acá |

---

## Relaciones

- [[Plan de Fase 1 - Roles, Autenticación y Recuperación]]
- [[Guía de Diseño Visual]]
- [[Módulo Login]]
- [[Seguridad y Privacidad Android]]
- [[Deuda Técnica - Pendientes]]
- [[Plan de Conexión con Supabase]]
- [[Esquema de Base de Datos]]

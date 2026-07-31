---
title: Plan de Conexión con Supabase — Propuestas
tags:
  - restaurante
  - supabase
  - plan
  - auth
date: 2026-07-29
lifecycle: draft
---

# Plan de Conexión con Supabase — 4 propuestas

> [!info] Qué es esta nota
> El código del login **ya está escrito y compila** (ver [[Módulo Login]]); lo que falta es **conectarlo a un backend real**. Esta nota presenta **cuatro propuestas** de cómo hacerlo, de la más barata a la más completa, con su costo y lo que habilita cada una. No es una decisión tomada — cuando se elija una, se registra como ADR en `45 - Decisiones/`.

---

## Punto de partida (estado verificado el 2026-07-29)

| Pieza | Estado |
|---|---|
| `LoginActivity` + `LoginViewModel` + `EstadoLogin` | ✅ Implementado |
| `AuthRepository` (interfaz) + `SupabaseAuthRepository` | ✅ Implementado — ahora también verifica perfil activo |
| `SupabaseAuthApi` (login + logout) + `SupabasePerfilApi` (Retrofit) + DTOs | ✅ Implementado |
| `SupabaseClient` (OkHttp + interceptor `apikey`) | ✅ Implementado — expone `getAuthApi()` y `getPerfilApi()` |
| `SUPABASE_URL` / `SUPABASE_ANON_KEY` en `local.properties` | ✅ **Completados** (2026-07-29) con el proyecto real |
| Proyecto Supabase del restaurante | ✅ Verificado: **Restaurante** (`mxarlisuueovxvttytcm`), `ACTIVE_HEALTHY`, Postgres 17.6.1 |
| Tabla `public.perfiles` + RLS | ✅ **Creada** (2026-07-29) — ver Propuesta C, ejecutada |
| Usuario de prueba en Auth + su fila en `perfiles` | ✅ **Creado 2026-07-31** — Fernando Barahona, `rol=admin`, también enlazado en `empleados`+`usuarios` |

> [!success] Ejecutado el 2026-07-29 — Propuesta A + parte de la C
> `local.properties` tiene la URL y la llave `anon` reales (todavía formato legado — no hay `sb_publishable_...` generada en el proyecto, ver **P-012**). Se creó `public.perfiles` (`id`, `nombre`, `rol` con `check`, `activo`, `creado_en`) con RLS: `select` solo para `authenticated` sobre su propia fila (`auth.uid() = id`), revocado para `anon`. `SupabaseAuthRepository.login()` ahora hace login → `GET perfiles` → si falta o `activo=false`, `POST logout` (revoca el token) y falla; si no, arma la `Sesion` con `rol`. `./gradlew assembleDebug` → BUILD SUCCESSFUL.
>
> **Por qué no se prueba todavía de punta a punta:** la tabla tiene **0 filas**. Falta que alguien con acceso al dashboard cree un usuario en **Authentication → Users** (con *Auto Confirm*) y su fila correspondiente en `perfiles` (`insert into public.perfiles (id, nombre, rol) values ('<uuid del usuario>', 'Nombre', 'mesero')`, ejecutado como el rol `postgres`/dashboard, no desde la app). Un agente no crea ese usuario — ver la nota sobre "Prohibited actions" más abajo.

> [!danger] El login nunca se ejecutó contra un servidor real con un usuario real
> La conexión funciona y el código está completo, pero sin un usuario de prueba **todo login termina en "Usuario no registrado en el sistema."** en el paso de verificación de perfil — el camino feliz del login **sigue sin probarse end-to-end**.

---

## Contrato REST verificado

> [!info] Fuentes
> [supabase/auth (GoTrue) — API](https://github.com/supabase/auth), [Understanding API keys](https://supabase.com/docs/guides/getting-started/api-keys), [User sessions](https://supabase.com/docs/guides/auth/sessions). Verificado el 2026-07-29.

| Operación | Request | Headers |
|---|---|---|
| **Login** | `POST /auth/v1/token?grant_type=password` · body `{"email","password"}` | `apikey`, `Content-Type: application/json` |
| **Refresh** | `POST /auth/v1/token?grant_type=refresh_token` · body `{"refresh_token"}` | `apikey` |
| **Usuario actual** | `GET /auth/v1/user` | `apikey`, `Authorization: Bearer <access_token>` |
| **Logout** | `POST /auth/v1/logout` | `apikey`, `Authorization: Bearer <access_token>` |
| **Alta** | `POST /auth/v1/signup` · body `{"email","password"}` | `apikey` |
| **Recuperar clave** | `POST /auth/v1/recover` · body `{"email"}` | `apikey` |
| **Datos (PostgREST)** | `GET /rest/v1/<tabla>?select=*` | `apikey`, `Authorization: Bearer <access_token>` |

Respuesta de login/refresh:

```json
{ "access_token": "...", "token_type": "bearer", "expires_in": 3600,
  "refresh_token": "...", "user": { "id": "...", "email": "..." } }
```

**Hechos que condicionan el diseño:**

1. El `access_token` dura **~1 hora** (`expires_in: 3600`). Sin refresh, la sesión se muere sola.
2. El `refresh_token` es de **un solo uso**, con una ventana de reutilización de ~10 s para tolerar cortes de red. Usarlo dos veces fuera de esa ventana **revoca toda la sesión**.
3. Las sesiones expiradas se borran de la base ~24 h después de expirar.
4. La llave **publishable** (`sb_publishable_...`) está pensada para ir dentro del APK; lo único que protege los datos es **RLS** sobre los roles `anon` / `authenticated`. La llave **secret** (`sb_secret_...`) hace *bypass* de RLS: **jamás** en la app.
5. Las llaves legadas `anon` / `service_role` **se deprecan a fin de 2026**.

---

## Propuesta A — Encender lo que ya existe (*smoke test*)

**Costo:** ~15 min · **Código nuevo:** cero · **Riesgo:** nulo

1. En el dashboard del proyecto → **Settings › API keys** → copiar la llave `sb_publishable_...`.
2. Completar `local.properties` (no versionado):
   ```properties
   SUPABASE_URL=https://mxarlisuueovxvttytcm.supabase.co
   SUPABASE_ANON_KEY=sb_publishable_...
   ```
   *(la URL sale del `project_ref` del `.mcp.json` — confirmarla en el dashboard antes de darla por buena)*
3. **Authentication › Users › Add user** → crear un usuario de prueba con *Auto Confirm User* activado (si el correo no está confirmado, el login devuelve 400).
4. **Crear su fila en `perfiles`** (Table Editor o SQL Editor): `insert into public.perfiles (id, nombre, rol) values ('<uuid del usuario recién creado>', 'Nombre de prueba', 'mesero');` — sin esto el login autentica pero la app lo rechaza igual (paso 2 de Propuesta C, ya implementado en el repositorio).
5. Validar sin la app antes de gastar un ciclo de build:
   ```bash
   curl -X POST "https://mxarlisuueovxvttytcm.supabase.co/auth/v1/token?grant_type=password" -H "apikey: <la de local.properties>" -H "Content-Type: application/json" -d "{\"email\":\"prueba@restaurante.hn\",\"password\":\"...\"}"
   ```
6. `./gradlew installDebug` y probar el login en un dispositivo.

> [!success] Pasos 1–2 ya ejecutados el 2026-07-29
> `local.properties` y el proyecto Supabase ya están conectados (ver el aviso al principio de esta nota). Faltan los pasos 3–4 (usuario + fila en `perfiles`) — **eso lo hace quien tenga acceso al dashboard**, no un agente: crear cuentas/usuarios está fuera de lo que un agente de IA debe hacer por su cuenta.

> [!warning] Bloqueante de instalación
> Hoy `minSdk = 37` (**P-003**): el APK **no instala en ningún teléfono real**. Para probar en hardware físico hay que bajar `minSdk` primero — es la primera tarea de la Fase 0 en [[Roadmap de Fases]].

**Habilita:** confirmar que la cadena `Activity → ViewModel → Repository → Retrofit → Supabase` funciona.
**No habilita:** la sesión se pierde al cerrar la app, y muere a la hora.

---

## Propuesta B — Sesión persistente + refresh de token

**Costo:** ~1 día · **Código nuevo:** medio · **Resuelve:** P-009

Es lo mínimo para que sea "un login de verdad": el mesero no debería re-loguearse cada hora ni cada vez que mata la app.

```
domain/repository/SesionRepository.java     — guardar(Sesion) / obtener() / limpiar()
data/local/SesionLocalDataSource.java       — EncryptedSharedPreferences (androidx.security-crypto)
data/remote/SupabaseAuthApi#refresh(...)    — POST token?grant_type=refresh_token
core/AuthInterceptor.java                   — agrega Authorization: Bearer <access_token>
core/TokenAuthenticator.java                — OkHttp Authenticator: ante 401, refresca y reintenta 1 vez
ui/splash/  (o LoginActivity al arrancar)   — ¿hay sesión válida? → MainActivity, si no → login
```

**Puntos finos que hay que respetar:**
- El `refresh_token` es de un solo uso → el refresh debe estar **sincronizado** (un solo hilo a la vez). `okhttp3.Authenticator` sirve para eso; dos refreshes en paralelo revocan la sesión.
- Guardar también `expires_at` (calculado como `ahora + expires_in`) para refrescar *antes* de que expire, no después.
- Si el refresh falla → limpiar el almacenamiento y volver al login. Nunca dejar al usuario en un limbo con token muerto.
- El token va cifrado en disco, no en `SharedPreferences` plano — ver [[Seguridad y Privacidad Android]].

**Habilita:** sesión que sobrevive al cierre de la app; base obligatoria para cualquier llamada a datos autenticada.

---

## Propuesta C — Auth + tabla `perfiles` con rol y RLS ← *ejecutada parcialmente el 2026-07-29*

**Costo:** ~1–2 días (sobre B) · **Código nuevo:** medio · **Habilita:** Fase 2 y Fase 5

Supabase Auth solo devuelve `id` y `email`. Un restaurante necesita saber **quién es** ese usuario: mesero, cocina o admin. Ese dato va en una tabla propia enlazada a `auth.users`.

```sql
create table public.perfiles (
  id uuid primary key references auth.users(id) on delete cascade,
  nombre text not null,
  rol text not null check (rol in ('mesero','cocina','admin')),
  creado_en timestamptz not null default now()
);

alter table public.perfiles enable row level security;

create policy "cada quien ve su perfil"
  on public.perfiles for select
  to authenticated
  using (auth.uid() = id);
```

En la app: después del login, `GET /rest/v1/perfiles?select=*&id=eq.<uid>` con el `Bearer`, y `Sesion` pasa a llevar `rol`.

> [!danger] RLS primero, tabla después
> Toda tabla nace con `enable row level security` **en la misma migración**. Una tabla sin RLS con la llave publishable en el APK = base de datos abierta a internet.

**Habilita:** menú/pedidos/mesas con permisos reales; el patrón PostgREST que van a reusar todos los módulos siguientes.

---

## Propuesta D — Edge Function como fachada (BFF)

**Costo:** ~2–3 días · **Código nuevo:** alto (Deno/TypeScript + Android) · **Recomendación:** *solo si aparece la necesidad*

Una Edge Function (`POST /functions/v1/login`) hace el login, lee el perfil y devuelve **una sola respuesta** ya armada. La llave `sb_secret_` vive en el servidor.

**A favor:** una llamada de red en vez de dos (importa en 3G de gama baja); reglas de negocio fuera del APK; permite operaciones que RLS no puede expresar.
**En contra:** un segundo lenguaje y un segundo pipeline de despliegue en un proyecto de curso; hoy no hay ninguna regla que lo justifique.

**Cuándo sí:** reportes agregados (Fase 6), o algo que exija llave secreta (mails, integraciones de pago).

---

## Descartada — SDK Kotlin de Supabase

Ya está descartado por [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]] y [[ADR-004 - Java + Views en vez de Kotlin + Compose]]: el SDK es Kotlin-only (corrutinas, `suspend`), y consumirlo desde Java es hostil. Se deja anotado para que nadie lo re-proponga sin leer los ADRs.

---

## Comparación

| | A — Encender | B — + Sesión | C — + Perfiles/RLS | D — Edge Function |
|---|---|---|---|---|
| Costo | 15 min | ~1 día | ~2 días | ~3 días |
| Código nuevo | 0 | Medio | Medio | Alto |
| Sobrevive al cierre de la app | ❌ | ✅ | ✅ | ✅ |
| Sobrevive a la hora | ❌ | ✅ | ✅ | ✅ |
| Roles / permisos | ❌ | ❌ | ✅ | ✅ |
| Llamadas de red al entrar | 1 | 1 | 2 | 1 |
| Segundo lenguaje | ❌ | ❌ | SQL | SQL + TS |
| Deuda que resuelve | — | P-009 | P-009 | P-009 |

## Camino sugerido

1. **Ahora:** Propuesta **A** — 15 minutos y sacamos la duda de si el login funciona. Se puede hacer contra el emulador aunque `minSdk` siga roto.
2. **Fase 0:** arreglar **P-003** para poder probar en un teléfono real, y de paso **B** (sesión + refresh).
3. **Fase 2 (Menú):** **C**, porque la primera tabla del dominio ya necesita RLS y rol.
4. **D** solo si Fase 6 (reportes) lo pide.

---

## Checklist de seguridad (aplica a todas)

- [ ] `local.properties` está en `.gitignore` — **verificar antes de commitear**
- [ ] En el APK va la llave **publishable**, nunca `sb_secret_`
- [ ] Toda tabla nueva nace con RLS activada y sus policies en la misma migración
- [ ] El `access_token` se guarda cifrado (nunca en `SharedPreferences` plano)
- [ ] Ningún token ni credencial en `Log`
- [ ] Renombrar `SUPABASE_ANON_KEY` → `SUPABASE_PUBLISHABLE_KEY` (**P-012**)

---

## Relaciones

- [[Módulo Login]]
- [[Supabase Auth REST - Login Android]]
- [[Caso 01 - Login con Supabase Auth]]
- [[Propuesta de División de Arquitectura]]
- [[Seguridad y Privacidad Android]]
- [[Roadmap de Fases]]
- [[Deuda Técnica - Pendientes]]
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]]
- [[ADR-005 - Offline-first obligatorio desde la Fase 2]]

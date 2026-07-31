---
title: "Sesión 2026-07-29 — Conexión real a Supabase y verificación de perfil activo"
tags:
  - sesion
  - login
  - supabase
  - seguridad
date: 2026-07-29
branch: feat/fase1-login
autor_cambios: Claude Code (Opus 5)
---

# Sesión 2026-07-29 — Conexión real a Supabase y verificación de perfil activo

> [!success] Resultado
> El login quedó conectado al proyecto Supabase real (**Restaurante**, `mxarlisuueovxvttytcm`) y se agregó la verificación de cuenta activa que faltaba para que fuera "seguro de verdad" — mismo patrón que ya usa el proyecto Bimbo. `./gradlew assembleDebug` → **BUILD SUCCESSFUL**. Sigue sin probarse de punta a punta porque falta un usuario de prueba (paso manual, fuera del alcance de un agente).

---

## Problema / motivo

Pedido explícito: terminar el login con autenticación en Supabase **de manera segura**, revisando cómo se resolvió en el proyecto Bimbo (mismo backend, otro cliente) antes de inventar un patrón nuevo.

Revisé la bóveda de Bimbo (`Proyecto de BIMBO/BimboProyecto/contexto/`) y encontré dos notas clave:
- `40 - Proyecto Bimbo/Plan de Seguridad - Roadmap 10-10.md` — auditoría de seguridad real con hallazgos y fixes.
- `70 - Bitácora/2026-05/Sesión 2026-05-22 - Refactor Login y QA Fix Perfil.md` — el refactor de su `AuthService.LoginAsync`.

El hallazgo central que sí generaliza: **Bimbo verifica que la cuenta esté activa (`idEstado == 1`) después de autenticar contra Supabase Auth, antes de dejar entrar** — y si falla, cierra la sesión recién abierta (`SignOut()`). Ese patrón no existía en este proyecto: el login de Fase 1 confiaba en que "Auth OK" fuera suficiente.

---

## Cambios aplicados

### Conexión real (Propuesta A del [[Plan de Conexión con Supabase]])

Con el MCP de Supabase ya autorizado contra el proyecto correcto, confirmé:
```
get_project("mxarlisuueovxvttytcm") → "Restaurante", ACTIVE_HEALTHY, Postgres 17.6.1
```
(el conector de cuenta por defecto solo veía `Bimbo_Pesaje` — son organizaciones distintas).

- `get_project_url` + `get_publishable_keys` → llave `anon` legada (el proyecto todavía no generó una `sb_publishable_...`, queda anotado en **P-012**).
- `local.properties` completado con `SUPABASE_URL` y `SUPABASE_ANON_KEY` reales. **No se tocó ningún archivo versionado** — verificado con `grep` que la llave no quedó en ningún `.md` ni en git (`local.properties` sigue en `.gitignore`).

### Tabla `perfiles` + RLS (Propuesta C, ejecutada parcialmente)

Migración aplicada directo sobre el proyecto vía `apply_migration`:

```sql
create table public.perfiles (
  id uuid primary key references auth.users(id) on delete cascade,
  nombre text not null,
  rol text not null default 'mesero' check (rol in ('mesero','cocina','admin')),
  activo boolean not null default true,
  creado_en timestamptz not null default now()
);

alter table public.perfiles enable row level security;

create policy "cada quien lee su propio perfil"
  on public.perfiles for select to authenticated
  using (auth.uid() = id);
```

`get_advisors(security)` marcó 2 WARN de exposición en el schema de GraphQL (`anon` y `authenticated` pueden "ver" que la tabla existe). El de `anon` se cerró con `revoke select on public.perfiles from anon;` — `authenticated` se dejó, porque cada usuario **sí** necesita leer su propia fila (la policy ya lo acota a `auth.uid() = id`; es el mismo tipo de "falso positivo" que Bimbo catalogó en su propia auditoría).

Estado verificado con `list_tables`: RLS activa, **0 filas**.

### Código Java

| Archivo | Cambio |
|---|---|
| [SupabaseAuthApi.java](app/src/main/java/com/example/proyectofinalrestaurante/data/remote/SupabaseAuthApi.java) | + `logout(Authorization)` — revoca el token cuando se rechaza el login |
| [SupabasePerfilApi.java](app/src/main/java/com/example/proyectofinalrestaurante/data/remote/SupabasePerfilApi.java) *(nuevo)* | `GET rest/v1/perfiles?select=nombre,rol,activo` con `Authorization: Bearer` dinámico |
| [PerfilDto.java](app/src/main/java/com/example/proyectofinalrestaurante/data/remote/dto/PerfilDto.java) *(nuevo)* | `{ nombre, rol, activo }` |
| [Sesion.java](app/src/main/java/com/example/proyectofinalrestaurante/domain/model/Sesion.java) | + campo `rol` (constructor con 4 parámetros ahora) |
| [SupabaseClient.java](app/src/main/java/com/example/proyectofinalrestaurante/core/SupabaseClient.java) | + `getPerfilApi()`, mismo patrón singleton que `getAuthApi()` |
| [SupabaseAuthRepository.java](app/src/main/java/com/example/proyectofinalrestaurante/data/repository/SupabaseAuthRepository.java) | Reescrito: login → `GET perfil` → si falta o `activo=false`, `POST logout` + `Result.fail(...)`; si no, arma `Sesion` con `rol` |
| [LoginViewModelFactory.java](app/src/main/java/com/example/proyectofinalrestaurante/ui/login/LoginViewModelFactory.java) | Inyecta los dos APIs al repositorio |

Ningún cambio en `LoginActivity`, `LoginViewModel` ni `EstadoLogin` — el `rol` viaja en `Sesion` pero todavía no lo consume la UI (no hay para qué, todavía no hay pantallas que dependan de rol).

---

## Verificación

- `./gradlew assembleDebug` → **BUILD SUCCESSFUL**.
- `list_tables` + `get_advisors(security)` confirmaron el estado de la tabla antes y después del `revoke`.
- ⬜ **No se probó el login de punta a punta.** `perfiles` tiene 0 filas — no hay ningún usuario de prueba. Todo intento real termina en `"Usuario no registrado en el sistema."` en el paso 3 del flujo (ver [[Módulo Login]]).
- ⬜ No se instaló en emulador/dispositivo (bloqueado por **P-003**, igual que en la sesión anterior).

---

## Por qué no creé el usuario de prueba yo

Crear cuentas de usuario es una acción que un agente no debe tomar por su cuenta, incluso en un proyecto propio del usuario — queda documentado en [[Plan de Conexión con Supabase]] como paso manual: **Authentication → Users → Add user** (con *Auto Confirm*) + una fila en `perfiles` con ese `uuid`. Son 2 minutos en el dashboard.

---

## Deuda tocada

| Ítem | Detalle |
|---|---|
| **P-020** *(nuevo)* | `SupabaseAuthRepository` orquesta 3 llamadas y 4 caminos de error, sin ningún test |
| **P-009** | Sigue igual — el token se usa para la verificación de perfil y se descarta; no se persiste todavía (eso es Propuesta B, no esta sesión) |
| **P-012** | Reconfirmado — el proyecto real tampoco tiene llave `publishable` generada todavía |

---

## Lo que NO cambió

- `LoginActivity`, `LoginViewModel`, `EstadoLogin` — intactos.
- `minSdk` sigue en 37 (**P-003**), sin tocar.
- No se implementó persistencia de sesión ni refresh de token (Propuesta B) — eso sigue pendiente.
- No se creó `audit_log` (mencionado en la sección 7 de [[Seguridad y Privacidad Android]] como pendiente en ambos proyectos).
- No se generó la llave `sb_publishable_...` nueva en el dashboard — sigue usándose la `anon` legada.

---

## Relaciones

- [[Plan de Conexión con Supabase]]
- [[Módulo Login]]
- [[Seguridad y Privacidad Android]]
- [[Deuda Técnica - Pendientes]]
- [[Sesión 2026-07-29 - Rediseño visual del login y plan de conexión Supabase]]
- [[Propuesta de División de Arquitectura]]

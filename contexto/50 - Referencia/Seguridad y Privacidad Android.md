---
title: "Seguridad y Privacidad Android"
tags:
  - referencia
  - seguridad
  - privacidad
date: 2026-07-29
lifecycle: verified
---

# Seguridad y Privacidad Android

> [!info] Fuente
> [Supabase — Understanding API keys](https://supabase.com/docs/guides/getting-started/api-keys), [Supabase — Migrating to publishable and secret API keys](https://supabase.com/docs/guides/getting-started/migrating-to-new-api-keys), guías de seguridad de developer.android.com. Verificado el 2026-07-29.

## 1. Secretos

- **Cero secretos en el repositorio.** Las llaves viven en `local.properties` (no versionado) o en variables de CI, y se exponen al código vía `buildConfigField`.
- `google-services.json`, keystores y `.env` van en `.gitignore`.
- **Los tokens de sesión se guardan cifrados con una clave del `AndroidKeyStore`**, nunca en texto plano ni en logs.

> [!danger] Corregido el 2026-08-04 — `EncryptedSharedPreferences` está deprecado
> Esta línea decía *"en `EncryptedSharedPreferences`/Jetpack Security"*. Google **deprecó
> todas** las APIs de `androidx.security:security-crypto` en `1.1.0-alpha07` (abril 2025),
> repetido en `1.1.0-beta01` (junio 2025):
> *"Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore."*
> Motivos conocidos: violaciones de StrictMode por I/O en el hilo principal y excepciones de
> *keyset corruption* de Tink.
>
> El reemplazo es el que la propia nota de Google indica: **usar el `AndroidKeyStore`
> directamente** (AES/GCM, IV por cifrado guardado junto al texto cifrado). Diseño completo
> en [[Plan Fase 0b - Cierre de la deuda P0]] §4.2. **DataStore + Tink** —lo que sugiere la
> comunidad— se descartó para este proyecto: su API idiomática es Kotlin `Flow` y el puente
> Java arrastra RxJava3 entero. Ver [[Librerias Java-Friendly vs Kotlin-Only]].

> [!danger] La bóveda se versiona: nunca escribir una contraseña acá
> `contexto/` está dentro del repo y se sube a GitHub. Anotar una credencial de prueba
> en una nota **es publicarla**, y borrarla después no la saca del historial de git.
>
> Las credenciales de cuentas de prueba se consultan o se resetean desde el dashboard
> de Supabase (Authentication → Users). En las notas se referencia la cuenta por su
> correo, nunca por su contraseña.
>
> **Ocurrió el 2026-07-29:** una nota de sesión quedó con la contraseña del admin y se
> pusheó. Se limpió el archivo el 2026-07-31, pero el valor sigue en commits anteriores
> — la única solución real fue **rotar esa contraseña**.

> [!note] Estado en este proyecto
> ✅ `SUPABASE_URL`/`SUPABASE_ANON_KEY` ya se leen de `local.properties` vía `BuildConfig`.
> ⚠️ El `access_token` que devuelve el login **hoy no se persiste en ningún lado** (se pierde al cerrar la app). Cuando se persista, debe ir cifrado. Ver **P-009** en [[Deuda Técnica - Pendientes]].

## 2. Llaves de Supabase — publishable vs secret

| Tipo | Formato | ¿Va en la app? |
|---|---|---|
| Publishable (reemplaza `anon`) | `sb_publishable_...` | ✅ Sí — es pública por diseño |
| Secret (reemplaza `service_role`) | `sb_secret_...` | 🔴 **JAMÁS** |

- Las llaves legadas `anon` / `service_role` **se deprecan a finales de 2026** y se eliminarán definitivamente. Los proyectos nuevos ya no las reciben desde noviembre de 2025.
- Ambos tipos conviven durante la migración: se pueden crear las nuevas sin romper las viejas y migrar cliente por cliente.
- Ventaja de seguridad real: una llave secreta filtrada **se revoca en segundos sin invalidar la sesión de todos los usuarios** — que era lo doloroso de rotar `service_role`.
- **Si una operación requiere privilegios elevados, va en una Edge Function**, nunca en la app.

## 2.5. Cómo guarda Supabase las contraseñas — nunca reimplementar hashing propio

> [!info] Verificado el 2026-07-29 contra la documentación oficial
> Supabase Auth usa **bcrypt** para las contraseñas: *"Supabase Auth uses bcrypt, a strong password hashing function, to store hashes of users' passwords. Only hashed passwords are stored... Each hash is accompanied by a randomly generated salt parameter for extra security. The hash is stored in the `encrypted_password` column of the `auth.users` table."* — [supabase.com/docs/guides/auth/passwords](https://supabase.com/docs/guides/auth/passwords). El nombre de la columna (`encrypted_password`) es engañoso por retrocompatibilidad: es un **hash**, no una encriptación reversible.

**Regla derivada:** ninguna tabla propia del proyecto debe tener una columna de contraseña. Si un módulo necesita "usuarios con credenciales", el patrón correcto es enlazar por `uuid` a `auth.users` (columna `id_auth_user uuid REFERENCES auth.users(id)`), nunca una columna `contrasena`/`password` propia. Aplicado el 2026-07-29 al resolver **P-021** en [[Esquema de Base de Datos]] — la tabla `public.usuarios` llegó con `contrasena VARCHAR(255)` y se corrigió antes de cargar datos reales.

Motivo: guardar y verificar contraseñas correctamente exige hashing lento (bcrypt/argon2/scrypt), salt por usuario, política de fuerza, límite de intentos y flujo de recuperación — todo trabajo ya hecho y auditado en Supabase Auth. Reimplementarlo es la fuente de vulnerabilidades más común en este tipo de proyectos (ver también la sección 7 de esta nota, lecciones de Bimbo).

## 3. Row Level Security (RLS)

> [!danger] No negociable
> **RLS activada en todas las tablas, sin excepción.** Ninguna tabla nueva se crea sin sus policies en la misma migración.
>
> Sin RLS, la llave publishable — que está dentro del APK y cualquiera puede extraer — da acceso de lectura/escritura a toda la base de datos.

## 4. Red

- `android:usesCleartextTraffic="false"`.
- HTTPS obligatorio. Considerar *certificate pinning* **solo con un plan de rotación escrito** (sin él, un cambio de certificado deja a todos los usuarios fuera).

## 5. Componentes y permisos

- `exported="false"` por defecto en todos los componentes; validar cada `Intent` entrante.
- Permisos mínimos, solicitados **en contexto** con `ActivityResultContracts.RequestPermission` y con explicación previa al usuario.
- `android:allowBackup="false"` o `dataExtractionRules` explícitas si hay datos sensibles.

## 6. Logs y PII

- **Sin PII en logs** (correos, tokens, nombres, teléfonos). Ni siquiera truncados.
- En release, R8 elimina `Log.v/d` con `-assumenosideeffects`.
- Crash reporting con `mappingFileUploadEnabled`, sin PII en breadcrumbs.

## 7. Lecciones de Bimbo — mismo patrón Supabase, otro cliente

> [!info] Por qué está acá
> El proyecto **Bimbo_Pesaje** (WPF/.NET) ya recorrió este camino con Supabase Auth y tiene un `Plan de Seguridad — Roadmap hacia 10/10` con hallazgos de una security review real. Lo que sigue es lo que **sí generaliza** a este proyecto (Android/Java/REST directo, sin el SDK). Fuente: bóveda de Bimbo, `40 - Proyecto Bimbo/Plan de Seguridad - Roadmap 10-10.md` y `70 - Bitácora/2026-05/Sesión 2026-05-22 - Refactor Login y QA Fix Perfil.md`.

1. **Nunca exponer `ex.getMessage()`/`ex.Message` a la UI.** Bimbo lo tuvo que corregir después de escribirlo (Fase 3.1 de su plan): el mensaje de una excepción de Supabase puede filtrar "invalid JWT", URLs internas, etc. Este proyecto ya nace bien en ese punto — `SupabaseAuthRepository` devuelve mensajes fijos (`"Correo o contraseña incorrectos"`), nunca la excepción cruda. **No romper este hábito** al tocar el repositorio.
2. **Verificar que la cuenta esté activa, server-side, antes de dejar entrar.** Bimbo: `AuthService` comprueba `idEstado == 1` después de autenticar contra Supabase Auth, y si falla hace `SignOut()` para no dejar un token vivo sin usar. Este proyecto replica el mismo patrón con la tabla `public.perfiles` (columna `activo`) — ver [[Plan de Conexión con Supabase]] y [[Módulo Login]]. Sin el SDK, "SignOut" es un `POST /auth/v1/logout` explícito con el token recién emitido.
3. **Jamás loguear un token, ni truncado.** Regla permanente de Bimbo tras un hallazgo de QA real (`Debug.WriteLine($"token={token[..20]}...")` sí cuenta como fuga). Si hace falta diagnosticar estado de sesión, loguear el **hecho booleano** (`sesión activa: sí/no`), nunca el valor.
4. **Rastro de auditoría (`audit_log`)** — tabla propia con `INSERT`-only por RLS, registrando `LOGIN_OK`/`LOGIN_FAIL`/`LOGOUT` sin exponer la causa interna del fallo. Bimbo lo tiene planificado, no implementado; acá tampoco existe todavía — queda para cuando haya más de un módulo autenticado escribiendo eventos.
5. **Password policy se configura en el dashboard de Supabase** (`Authentication → Policies → Password Requirements`), no solo en el cliente — la validación de fuerza en la `Activity` es UX, no seguridad, si el backend no la exige también.
6. **Timeout de sesión por inactividad** — pendiente en ambos proyectos; se implementa junto con la primera pantalla que lo necesite (en Bimbo iba junto con "Mi Usuario"; acá seguramente con la primera pantalla post-login real).
7. **DPAPI en WPF ⇄ Android Keystore acá.** Bimbo usa `ProtectedData.Protect(..., DataProtectionScope.CurrentUser)` para lo poco que persiste (el email de "recordarme"); el equivalente Android para persistir el `access_token` (cuando se implemente, **P-009**) es **cifrar con una clave del `AndroidKeyStore`**, nunca `SharedPreferences` plano — y ya **no** Jetpack Security, que está deprecado (ver el callout de la sección 1).

## 8. Cumplimiento

- Declaración de **Seguridad de los Datos** de Play consistente con lo que la app realmente hace.
- Políticas de permisos sensibles respetadas (ver [[Requisitos de Google Play 2026]]).

---

## Relaciones

- [[Supabase Auth REST - Login Android]]
- [[Requisitos de Google Play 2026]]
- [[Lista Negra de APIs Android]]
- [[Estándar de Ingeniería Android]]
- [[Deuda Técnica - Pendientes]] — P-009

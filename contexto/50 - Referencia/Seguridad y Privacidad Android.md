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
- **Los tokens de sesión se guardan en `EncryptedSharedPreferences`/Jetpack Security**, nunca en texto plano ni en logs.

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

## 7. Cumplimiento

- Declaración de **Seguridad de los Datos** de Play consistente con lo que la app realmente hace.
- Políticas de permisos sensibles respetadas (ver [[Requisitos de Google Play 2026]]).

---

## Relaciones

- [[Supabase Auth REST - Login Android]]
- [[Requisitos de Google Play 2026]]
- [[Lista Negra de APIs Android]]
- [[Estándar de Ingeniería Android]]
- [[Deuda Técnica - Pendientes]] — P-009

---
title: "Sesión 2026-07-29 — Rediseño visual del login y plan de conexión Supabase"
tags:
  - sesion
  - login
  - supabase
  - ui
date: 2026-07-29
branch: feat/fase1-login
autor_cambios: Claude Code (Opus 5)
---

# Sesión 2026-07-29 — Rediseño visual del login y plan de conexión Supabase

> [!success] Resultado
> Se rehízo la **capa visual** del login (Material 3, solo correo y contraseña, sin efectos), quedó **P-004 resuelta** y **P-010/P-011 parciales**, y se documentaron dos propuestas nuevas: [[Plan de Conexión con Supabase]] (4 opciones) y [[Propuesta de División de Arquitectura]] (3 opciones). `./gradlew assembleDebug` → **BUILD SUCCESSFUL**.

---

## Problema / motivo

Pregunta de partida: *¿qué parte del login está hecha y qué solo está planificada?*

**Respuesta verificada leyendo el código:** el login está **completo de punta a punta en código** — `LoginActivity` → `LoginViewModel` → `AuthRepository` → `SupabaseAuthRepository` → `SupabaseAuthApi` (Retrofit) → `SupabaseClient`. Nada de eso es plan: existe y compila.

Lo que **no** existe es la conexión real: `SUPABASE_URL` y `SUPABASE_ANON_KEY` están vacíos en `local.properties`, así que `SupabaseClient` cae al placeholder `https://supabase-no-configurado.invalid/` y **el camino feliz nunca se ejecutó**. Sumado a `minSdk = 37` (**P-003**), la app tampoco instala en un teléfono real.

---

## Cambios aplicados

### `res/layout/activity_login.xml` — reescrito

| Antes | Ahora | Por qué |
|---|---|---|
| `ConstraintLayout` raíz | `NestedScrollView` + `LinearLayout` (`fillViewport`) | Con el teclado abierto en pantalla chica, el botón ya no queda fuera |
| `EditText` con `android:hint` suelto | `TextInputLayout` (OutlinedBox) + `TextInputEditText` | El hint flotante no se pierde al escribir (**P-010**) |
| — | `app:endIconMode="password_toggle"` | Ver/ocultar contraseña, componente estándar de Material |
| `android:textSize="24sp"` | `?attr/textAppearanceHeadlineSmall` | Respeta la escala de fuente del sistema |
| `android:textColor="#D32F2F"` | `?attr/colorError` | Se adapta solo a claro/oscuro (**P-011**) |
| `dp` sueltos (24/32/16) | `@dimen/espaciado_*` | Regla de oro #8 |
| `Button` | `MaterialButton` con `minHeight="@dimen/altura_minima_tactil"` (48 dp) | Mínimo táctil de accesibilidad |
| — | `contentDescription` en el `ProgressBar`, `accessibilityLiveRegion="polite"` en el error | TalkBack anuncia el error solo (**P-010**) |

Se mantuvieron **los mismos IDs** (`txt_correo`, `txt_contrasenia`, `btn_login`, `progress_login`, `txt_error_login`, `login_root`) a propósito: renombrarlos a `camelCase` obliga a tocar `LoginActivity` y es parte pendiente de **P-011**. Sin ilustraciones, degradados, animaciones ni efectos.

### `res/values/dimens.xml` — nuevo

Escala de espaciado en múltiplos de 8 dp (`espaciado_minimo/campo/pantalla/seccion`) + `altura_minima_tactil` (48 dp). La usan todas las pantallas que vengan.

### `res/values/strings.xml`

Se agregó `login_cd_progreso` ("Iniciando sesión") para el `contentDescription` del `ProgressBar`.

### `ui/login/LoginActivity.java` — **P-004 resuelta**

```java
EdgeToEdge.enable(this);
setContentView(R.layout.activity_login);
aplicarInsets();   // systemBars() | ime() sobre login_root
```

Incluye `WindowInsetsCompat.Type.ime()` porque la pantalla tiene campos de texto — sin eso, con `targetSdk 37` el teclado tapa el botón. Es el mismo patrón que ya usaba `MainActivity`.

### Bóveda — notas nuevas

- **[[Plan de Conexión con Supabase]]** — contrato REST verificado contra la documentación oficial (login, refresh, user, logout, signup, recover) + 4 propuestas: **A** encender lo que ya existe (15 min), **B** sesión persistente + refresh, **C** perfiles con rol y RLS *(destino recomendado)*, **D** Edge Function como BFF. Incluye por qué el SDK Kotlin sigue descartado.
- **[[Propuesta de División de Arquitectura]]** — 3 opciones (layer-first actual, **feature-first dentro de `app`** *recomendada*, multi-módulo Gradle), con el momento exacto de migrar (**al empezar Fase 2, antes de escribir Menú**) y cómo crece la estructura fase por fase. Responde a **P-017**.

### Configuración del repo

Se agregó `.mcp.json` en la raíz (scope proyecto) apuntando al servidor MCP de Supabase con `project_ref=mxarlisuueovxvttytcm`. El CLI lo había escrito en `contexto/40 - Proyecto Restaurante/` por el `cwd` del shell; se movió a la raíz. **Requiere reiniciar Claude Code para que el servidor se conecte.**

Se instalaron las skills oficiales de Supabase con `npx skills add supabase/agent-skills` → `.agents/skills/supabase/` y `.agents/skills/supabase-postgres-best-practices/` (formato universal: sirven también para Codex, Copilot, Gemini CLI, etc.; para Claude Code quedan symlinkeadas). Se activan al reiniciar.

> [!warning] El proyecto del restaurante no es accesible todavía desde el MCP
> El conector Supabase activo en esta sesión está autorizado **solo** para la organización `zoeubdmhwzoxymmxradt` (proyecto `Bimbo_Pesaje`). `get_project("mxarlisuueovxvttytcm")` devuelve **"You do not have permission to perform this action"**. El servidor nuevo del `.mcp.json` necesita su propia autorización OAuth al arrancar — hasta entonces, la base del restaurante no se puede inspeccionar ni migrar desde acá.

---

## Verificación

- `./gradlew assembleDebug` → **BUILD SUCCESSFUL**, `app/build/outputs/apk/debug/app-debug.apk` generado.
- ⬜ **No verificado en dispositivo ni emulador**: el rediseño no se vio corriendo. Los insets y el comportamiento con teclado están escritos siguiendo el patrón de `MainActivity`, pero **no probados en pantalla** — y no se pueden probar en hardware real hasta arreglar **P-003** (`minSdk 37`).
- ⬜ El login sigue sin haberse ejecutado contra un Supabase real.

---

## Deuda tocada

| Ítem | Antes | Ahora |
|---|---|---|
| **P-004** edge-to-edge/insets | 🔴 Pendiente | ✅ **Resuelto** (falta ver en pantalla) |
| **P-010** accesibilidad | 🟡 Pendiente | 🟡 Parcial — falta TalkBack, fuente 200 %, `setError` por campo |
| **P-011** IDs y color | 🟢 Pendiente | 🟢 Parcial — color y dimens resueltos; IDs siguen en `snake_case` |
| **P-019** *(nuevo)* | — | Mensajes de error hardcodeados en `LoginViewModel` / `SupabaseAuthRepository` |

---

## Lo que NO cambió

- **Nada de lógica**: `LoginViewModel`, `EstadoLogin`, `SupabaseAuthRepository`, `SupabaseAuthApi`, `SupabaseClient` y los DTOs quedaron intactos. El único cambio en Java son las 8 líneas de insets.
- `local.properties` sigue vacío — completarlo es decisión de quien tenga las llaves del proyecto Supabase.
- No se creó ninguna tabla, policy ni usuario en Supabase.
- No se migró la arquitectura a feature-first: es una **propuesta**, y el momento correcto es la Fase 2.
- No se tocó `minSdk` (**P-003**) ni ningún otro ítem de la Fase 0.

---

## Relaciones

- [[Plan de Conexión con Supabase]]
- [[Propuesta de División de Arquitectura]]
- [[Módulo Login]]
- [[Deuda Técnica - Pendientes]]
- [[Arquitectura Actual]]
- [[Roadmap de Fases]]
- [[Supabase Auth REST - Login Android]]
- [[Accesibilidad Android]]
- [[Sesión 2026-07-29 - Auditoría contra el Estándar de Ingeniería Android]]

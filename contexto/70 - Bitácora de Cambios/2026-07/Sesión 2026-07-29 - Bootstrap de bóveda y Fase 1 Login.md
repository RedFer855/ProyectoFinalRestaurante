---
title: "Sesión 2026-07-29 — Bootstrap de bóveda y Fase 1 Login"
tags:
  - sesion
date: 2026-07-29
branch: feat/fase1-login
autor_cambios: Claude Code (agente)
---

# Sesión 2026-07-29 — Bootstrap de bóveda y Fase 1 Login

> [!success] Resultado
> Se creó la bóveda Obsidian (`contexto/`) y el protocolo de agentes para Proyecto Final Restaurante, replicando la estructura y convenciones usadas en el proyecto Bimbo pero adaptadas a una app Android (Java + XML Views). Se implementó el primer módulo real: login contra Supabase Auth vía REST/Retrofit.

---

## Problema / motivo

El usuario pidió replicar, para este proyecto mobile, el mismo sistema de instrucciones de agentes y bóveda de conocimiento usado en el proyecto Bimbo (WPF/.NET), adaptando los nodos de arquitectura al entorno Android. Se pidió además construir la Fase 1 (login) en su propia rama y subirla a GitHub.

## Cambios aplicados

- `AGENTS.md` / `CLAUDE.md` (raíz) — punto de entrada único para agentes, reglas de oro adaptadas a `ui`/`domain`/`data`/`core`.
- `contexto/` — bóveda Obsidian completa: `00 - MOC`, `10 - Arquitectura`, `20 - Patrones`, `30 - Casos de Uso`, `40 - Proyecto Restaurante`, `45 - Decisiones`, `50 - Referencia`, `70 - Bitácora de Cambios`, `_templates`, `.obsidian` (config + `graph.json` coloreado por carpeta).
- `.claude/settings.json` + `.claude/hooks/vault-trigger.js` — hook de bienvenida (`SessionStart`) y hook de "pregunta clave" (`UserPromptSubmit`), mismo mecanismo que Bimbo, con el bloque de pregunta/respuesta **vacío** (`[PENDIENTE — completar]`) para que el usuario escriba su propio acertijo cuando quiera.
- `.gitignore` — agregado bloque de exclusión de `.claude/*` con excepciones (`settings.json`, `hooks/`), e ignorado el estado local de Obsidian (`workspace.json`, `cache`, backups de `graph.json`).
- Código Fase 1 (paquetes `ui.login`, `domain`, `data`, `core`): `LoginActivity`, `LoginViewModel`, `EstadoLogin`, `LoginViewModelFactory`, `Sesion`, `AuthRepository`, `Result`, `SupabaseAuthApi`, `LoginRequestDto`, `LoginResponseDto`, `SupabaseAuthRepository`, `SupabaseClient`.
- `app/build.gradle.kts` — dependencias Retrofit/Gson/Lifecycle, `buildConfigField` para `SUPABASE_URL`/`SUPABASE_ANON_KEY` leídos desde `local.properties`.
- `AndroidManifest.xml` — `LoginActivity` como `LAUNCHER`; `MainActivity` pasa a ser la pantalla post-login.

## Verificación

`./gradlew assembleDebug` → **BUILD SUCCESSFUL** (34 tareas ejecutadas, 1m 6s). No se probó en emulador/dispositivo real (sin credenciales de Supabase todavía, ver [[Módulo Login]]) — solo se confirmó que compila y empaqueta.

## Lo que NO cambió

- No se creó ningún proyecto Supabase real ni se ingresaron credenciales — `local.properties` queda con `SUPABASE_URL`/`SUPABASE_ANON_KEY` vacíos, a completar por el usuario.
- No se migró el proyecto a Kotlin/Compose — se mantuvo Java + XML Views, siguiendo el esqueleto ya generado.
- No se copió la "pregunta clave" real del proyecto Bimbo (por regla explícita de esa bóveda: nunca se guarda ni se reutiliza fuera de su repo de origen).

---

## Relaciones

- [[Arquitectura Actual]]
- [[Módulo Login]]
- [[Deuda Técnica - Pendientes]]
- [[Roadmap de Fases]]

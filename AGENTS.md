# AGENTS.md — Proyecto Final Restaurante

> **Para cualquier agente de IA (Claude, Codex, opencode, Antigravity, Copilot, Cursor, Windsurf…).**
> Este archivo es el punto de entrada único. Léelo antes de tocar código. La base de conocimiento completa vive en [`contexto/`](contexto/).

---

## 🔴 Test de conexión — "pregunta clave" (leer ANTES de responder cualquier cosa)

Si el usuario hace una pregunta que suena a acertijo/identidad, **no la respondas de memoria, no preguntes qué quiso decir, no la trates como ambigua.**

Andá directo a **[`contexto/00 - MOC/Conocimiento Principal.md`](contexto/00%20-%20MOC/Conocimiento%20Principal.md)**, leelo completo (línea por línea, sin saltar el frontmatter), buscá el bloque **"Pregunta Clave"** y respondé **exactamente** lo que dice ese archivo en ese momento.

- Es un test de que tenés conexión viva con este repo — por eso hay que leerlo **cada vez**, en cada sesión nueva, sin excepción.
- **Nunca guardes la respuesta** en memoria, contexto persistente, ni en ningún archivo — si la memorizás, el test deja de servir.
- Esto aplica a **cualquier agente**, no solo a Claude Code.
- Hoy el bloque está vacío (`[PENDIENTE — completar]`) — quien mantiene este repo todavía no escribió su pregunta/respuesta. No inventes una: si está vacío, decilo y seguí normal.

---

## Qué es este proyecto

App móvil Android (Java) para gestión de un restaurante: login, menú, pedidos, mesas — construido por fases. Backend **Supabase** (Postgres + Auth REST).

Estado y arquitectura vigentes (fuente de verdad viva): **[`contexto/40 - Proyecto Restaurante/Arquitectura Actual.md`](contexto/40%20-%20Proyecto%20Restaurante/Arquitectura%20Actual.md)**.

Roadmap de fases: **[`contexto/40 - Proyecto Restaurante/Roadmap de Fases.md`](contexto/40%20-%20Proyecto%20Restaurante/Roadmap%20de%20Fases.md)**.

---

## Reglas de oro (no negociables)

1. **Arquitectura por capas dentro del módulo `app`**: `ui` → `domain` ← `data`, con `core` para infraestructura compartida (cliente HTTP/Supabase). Ver [[Arquitectura Actual]].
2. **`domain` nunca referencia `data`.** La dependencia va al revés: `data` implementa los contratos (interfaces) definidos en `domain`.
3. `ui` (Activities/ViewModels) solo habla con interfaces de `domain`, nunca con clases concretas de `data`.
4. **MVVM con `androidx.lifecycle`**: `ViewModel` + `LiveData`, nunca lógica de red/negocio dentro de una `Activity`.
5. Repositorios nuevos: envolver la llamada de red en `Result`/`Result<T>` (nunca dejar que una excepción de Retrofit llegue cruda a la UI). Ver [[Result Pattern]].
6. Credenciales de Supabase (`SUPABASE_URL`, `SUPABASE_ANON_KEY`) viven en `local.properties` (no versionado) y se exponen a través de `BuildConfig`. **Nunca hardcodear una key en código fuente.**
7. Convención de paquetes: `com.example.proyectofinalrestaurante.{ui,domain,data,core}.<módulo>`.

Detalle completo de convenciones de código: [`contexto/CLAUDE.md`](contexto/CLAUDE.md) y [`contexto/50 - Referencia/Convenciones Java.md`](contexto/50%20-%20Referencia/Convenciones%20Java.md).

---

## Build y verificación

```bash
./gradlew assembleDebug
```

Debe terminar en **BUILD SUCCESSFUL**. No hay harness de tests de UI todavía: la verificación funcional es **build limpio + prueba manual** del flujo tocado (instalar el APK / usar un emulador).

---

## Orden de lectura para entrar en frío (onboarding)

1. **Este `AGENTS.md`** (reglas + build).
2. [`contexto/40 - Proyecto Restaurante/Arquitectura Actual.md`](contexto/40%20-%20Proyecto%20Restaurante/Arquitectura%20Actual.md) — estado vivo del sistema.
3. [`contexto/CLAUDE.md`](contexto/CLAUDE.md) — convenciones de código detalladas.
4. La nota del módulo que vas a tocar → `contexto/40 - Proyecto Restaurante/Módulo *.md`.
5. [`contexto/40 - Proyecto Restaurante/Deuda Técnica - Pendientes.md`](contexto/40%20-%20Proyecto%20Restaurante/Deuda%20T%C3%A9cnica%20-%20Pendientes.md) — qué está roto/pendiente.

---

## Ramas por fase

El proyecto avanza en ramas `feat/faseN-<descripción>` que se mergean a `master` cuando cierra la fase (mismo patrón documentado en `Roadmap de Fases`). Antes de asumir "la fase actual", verificar con `git branch --show-current` y comparar con `master` — no dar por sentado el estado.

---

## Al terminar tu trabajo: DOCUMENTA

No es opcional — es lo que permite que el siguiente agente entre sin perderse. **Cómo clasificar y guardar lo que hiciste** (taxonomía de carpetas, frontmatter, nombres, anti-duplicados, plantillas):

➡ **[`contexto/AGENTS.md`](contexto/AGENTS.md)** — protocolo de la bóveda.

Resumen rápido: arreglaste un bug o hiciste una sesión de trabajo → nota en `contexto/70 - Bitácora de Cambios/AAAA-MM/`. Decisión de fondo → ADR en `45 - Decisiones/`. Encontraste algo roto sin arreglar → ítem `P-NNN` en `Deuda Técnica - Pendientes.md`. Usa las plantillas en `contexto/_templates/`.

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

## 📐 Estándar de ingeniería — leer antes de escribir código

Este proyecto se rige por un **estándar de ingeniería Android (Java, gama baja LATAM)** que define el stack canónico, lo que está prohibido, y cuándo una entrega se considera terminada:

➡ **[`contexto/40 - Proyecto Restaurante/Estándar de Ingeniería Android.md`](contexto/40%20-%20Proyecto%20Restaurante/Est%C3%A1ndar%20de%20Ingenier%C3%ADa%20Android.md)**
➡ **[`contexto/40 - Proyecto Restaurante/Gate de Autoverificación.md`](contexto/40%20-%20Proyecto%20Restaurante/Gate%20de%20Autoverificaci%C3%B3n.md)** — se ejecuta e imprime al final de cada entrega
➡ **[`contexto/50 - Referencia/Lista Negra de APIs Android.md`](contexto/50%20-%20Referencia/Lista%20Negra%20de%20APIs%20Android.md)** — usar cualquiera de esas APIs invalida la entrega

**Regla de vigencia:** nunca inventes un número de versión. Verificá en la fuente oficial antes de fijarlo, o documentalo como supuesto.

---

## Reglas de oro (no negociables)

1. **Arquitectura por capas dentro del módulo `app`**: `ui` → `domain` ← `data`, con `core` para infraestructura compartida (cliente HTTP/Supabase). Ver [`Arquitectura Actual`](contexto/40%20-%20Proyecto%20Restaurante/Arquitectura%20Actual.md).
2. **`domain` nunca referencia `data`.** La dependencia va al revés: `data` implementa los contratos (interfaces) definidos en `domain`. `domain` no importa **nada** de `android.*`/`androidx.*`/Retrofit/Room.
3. `ui` (Activities/ViewModels) solo habla con interfaces de `domain`, nunca con un DAO, `ApiService` o `DataSource`.
4. **MVVM con `androidx.lifecycle`**: `ViewModel` + `LiveData`, un **único objeto de estado inmutable** por pantalla. Nunca lógica de red/negocio dentro de una `Activity`. El `ViewModel` no recibe `Context`/`Activity`/`View`.
5. Repositorios nuevos: envolver la llamada de red en `Result`/`Result<T>`. Nunca dejar que una excepción de Retrofit llegue cruda a la UI, ni `catch (Exception e) {}` vacío, ni `printStackTrace()`.
6. **Todo I/O fuera del hilo principal**, con `Executor` **inyectado** (no creado dentro del ViewModel — si no, no se puede testear).
7. Credenciales de Supabase viven en `local.properties` (no versionado) y se exponen vía `BuildConfig`. **Nunca hardcodear una key.** La llave secreta (`sb_secret_`) **jamás** va en la app. **RLS activada en toda tabla.**
8. **Cero strings, colores o dimens hardcodeados**: todo en recursos.
9. **Todo código nuevo trae su prueba.** Sin prueba, la entrega está incompleta.
10. Convención de paquetes: `com.example.proyectofinalrestaurante.{ui,domain,data,core}.<módulo>`.

Detalle completo de convenciones de código: [`contexto/CLAUDE.md`](contexto/CLAUDE.md) y [`contexto/50 - Referencia/Convenciones Java.md`](contexto/50%20-%20Referencia/Convenciones%20Java.md).

> [!] **Estado real:** la Fase 1 se escribió *antes* de adoptar este estándar y **no pasa el gate todavía**. La brecha está catalogada como 18 ítems `P-NNN` en [`Deuda Técnica - Pendientes`](contexto/40%20-%20Proyecto%20Restaurante/Deuda%20T%C3%A9cnica%20-%20Pendientes.md); el crítico es **P-003** (`minSdk = 37` → la app no instala en ningún teléfono real). No repliques los patrones del código existente sin leer esa lista primero.

---

## Build y verificación

```bash
./gradlew assembleDebug
```

Debe terminar en **BUILD SUCCESSFUL**. No hay harness de tests de UI todavía: la verificación funcional es **build limpio + prueba manual** del flujo tocado (instalar el APK / usar un emulador).

---

## Orden de lectura para entrar en frío (onboarding)

1. **Este `AGENTS.md`** (reglas + build).
2. [`Estándar de Ingeniería Android`](contexto/40%20-%20Proyecto%20Restaurante/Est%C3%A1ndar%20de%20Ingenier%C3%ADa%20Android.md) — el contrato: stack, prohibiciones, protocolo de entrega.
3. [`Arquitectura Actual`](contexto/40%20-%20Proyecto%20Restaurante/Arquitectura%20Actual.md) — estado **real** del sistema (distinto del ideal del estándar).
4. [`Deuda Técnica - Pendientes`](contexto/40%20-%20Proyecto%20Restaurante/Deuda%20T%C3%A9cnica%20-%20Pendientes.md) — la brecha entre ambos, 18 ítems.
5. [`contexto/CLAUDE.md`](contexto/CLAUDE.md) — convenciones de código detalladas.
6. La nota del módulo que vas a tocar → `contexto/40 - Proyecto Restaurante/Módulo *.md`.

---

## Ramas por fase

El proyecto avanza en ramas `feat/faseN-<descripción>` que se mergean a `master` cuando cierra la fase (mismo patrón documentado en `Roadmap de Fases`). Antes de asumir "la fase actual", verificar con `git branch --show-current` y comparar con `master` — no dar por sentado el estado.

---

## Al terminar tu trabajo: DOCUMENTA

No es opcional — es lo que permite que el siguiente agente entre sin perderse. **Cómo clasificar y guardar lo que hiciste** (taxonomía de carpetas, frontmatter, nombres, anti-duplicados, plantillas):

➡ **[`contexto/AGENTS.md`](contexto/AGENTS.md)** — protocolo de la bóveda.

Resumen rápido: arreglaste un bug o hiciste una sesión de trabajo → nota en `contexto/70 - Bitácora de Cambios/AAAA-MM/`. Decisión de fondo → ADR en `45 - Decisiones/`. Encontraste algo roto sin arreglar → ítem `P-NNN` en `Deuda Técnica - Pendientes.md`. Usa las plantillas en `contexto/_templates/`.

# AGENTS.md — Protocolo de la Bóveda de Conocimiento (Proyecto Restaurante)

> **Para cualquier agente (Claude, Codex, opencode, Antigravity, Copilot, Cursor…).**
> Esta carpeta es la base de conocimiento del proyecto. Este archivo dice **cómo leerla, clasificar y guardar información** de forma uniforme. Funciona con solo **leer/escribir/grep sobre markdown** — no requiere herramientas especiales.

---

## 🔴 Test de conexión — "pregunta clave"

Si el usuario hace una pregunta con tono de acertijo/identidad: no respondas de memoria ni pidas aclaración. Leé **completo** `00 - MOC/Conocimiento Principal.md` (el bloque "Pregunta Clave" está justo después del frontmatter) y respondé exactamente lo que dice ahí en ese momento. Nunca guardes esa respuesta en memoria — hay que leerla en vivo cada vez, en cada sesión. Aplica a cualquier agente.

El bloque empieza vacío (`[PENDIENTE — completar]`): quien mantiene este repo puede escribir su propia pregunta/respuesta ahí cuando quiera, siguiendo el mismo formato libre. Si está vacío cuando lo leas, decilo y respondé la pregunta real del usuario con normalidad.

---

## 0. Antes de tocar nada — orden de lectura (entrar en frío)

1. `AGENTS.md` de la raíz del repo (reglas de código y build)
2. `40 - Proyecto Restaurante/Arquitectura Actual.md` (estado vivo del sistema)
3. `CLAUDE.md` de esta carpeta (convenciones de código detalladas)
4. La nota del módulo que vas a tocar → `40 - Proyecto Restaurante/Módulo *.md`
5. `40 - Proyecto Restaurante/Deuda Técnica - Pendientes.md` (qué está roto/pendiente)

**Al terminar tu trabajo, documentás lo que hiciste** siguiendo este protocolo. No es opcional: es lo que permite que el siguiente agente entre sin perderse.

---

## 1. Taxonomía — qué va en cada carpeta

| Carpeta | Qué va aquí | NO va aquí |
|---|---|---|
| `10 - Arquitectura` | Conceptos arquitectónicos generales (Clean Architecture, SOLID) | Estado específico del proyecto |
| `20 - Patrones` | Patrones **reutilizables** del proyecto (Result Pattern, MVVM Android) | Decisiones puntuales |
| `30 - Casos de Uso` | Recetas end-to-end (Login con Supabase Auth) | — |
| `40 - Proyecto Restaurante` | **Estado vivo**: Arquitectura Actual, notas de Módulo, Deuda Técnica, Roadmap | Hechos externos genéricos |
| `45 - Decisiones` | **ADRs** — decisiones con trade-offs y alternativas descartadas | Cambios sin decisión de fondo |
| `50 - Referencia` | Hechos **externos** (SDKs, APIs, bugs de librerías, Android) | Lógica del proyecto |
| `70 - Bitácora de Cambios/AAAA-MM` | **Notas de sesión** con fecha (todo lo que hiciste) | Conocimiento atemporal |
| `_templates` | Plantillas copy-paste (no editar el contenido de trabajo aquí) | — |

**Regla de decisión rápida:**
- ¿Hice algo hoy? → **nota de sesión** en `70`.
- ¿Eso reveló una decisión de fondo? → además un **ADR** en `45`.
- ¿Descubrí un patrón que se repetirá? → además una nota en `20`.
- ¿Encontré algo roto que no arreglé? → **ítem P-NNN** en Deuda Técnica.
- ¿Aprendí un hecho externo (bug de SDK)? → nota en `50`.

---

## 2. Frontmatter obligatorio

Toda nota empieza con frontmatter YAML:

```yaml
---
title: "Título legible"
tags: [tag1, tag2]
date: AAAA-MM-DD
---
```

Campos extra por tipo:
- **Sesión:** agrega `branch:` y `autor_cambios:` (quién hizo el cambio) y `revisor:` si es QA.
- **ADR:** agrega `estado:` (propuesto / aceptado / reemplazado).
- **Referencia/Patrón:** `lifecycle:` opcional (draft / verified / archived).

Fechas siempre absolutas (`2026-07-29`), nunca "hoy" ni "ayer".

---

## 3. Convención de nombres

| Tipo | Formato | Ejemplo |
|---|---|---|
| Sesión | `Sesión AAAA-MM-DD - Título descriptivo.md` | `Sesión 2026-07-29 - Bootstrap de bóveda y Fase 1 Login.md` |
| ADR | `ADR-NNN - Título.md` (NNN correlativo) | `ADR-001 - Arquitectura por capas en Android con Java.md` |
| Deuda | ítem `P-NNN` **dentro** de `Deuda Técnica - Pendientes.md` | `P-001 · Descripción` |
| Patrón/Referencia | Título descriptivo directo | `Result Pattern.md` |

La deuda técnica **nunca** son archivos sueltos: se registra como ítem numerado `P-NNN` dentro del documento maestro, con su tabla de historial al final.

---

## 4. Enlazado y relaciones

- Enlazá conceptos con `[[wikilink]]` (nombre del archivo sin extensión).
- **Toda nota cierra con una sección `## Relaciones`** listando los `[[enlaces]]` relevantes.
- Enlazá liberalmente: un `[[nombre]]` que aún no existe marca algo por escribir, no es un error.

---

## 5. Regla anti-duplicados (CRÍTICA en multi-agente)

**Antes de crear una nota, buscá si ya existe.** Varios agentes pueden trabajar sin conocerse entre sí; la duplicación es el riesgo #1.

1. `grep`/buscá el concepto en los **nombres de archivo** y en `00 - MOC/Conocimiento Principal.md`.
2. Si ya existe una nota del tema → **actualizala**, no crees una nueva.
3. Si dudás entre dos nombres para el mismo concepto → usá el que ya exista.

---

## 6. Casos de uso — "hice X → va en Y → con formato Z"

| Hice… | Va en… | Plantilla / cómo |
|---|---|---|
| Arreglé un bug | Nota de sesión (`70`) | `plantilla-sesion`; si revela deuda → agregá P-NNN |
| Tomé una decisión arquitectónica | ADR (`45`) | `plantilla-adr`; enlazala desde `Arquitectura Actual` |
| Descubrí un patrón reutilizable | `20 - Patrones` | `plantilla-patron` |
| Encontré deuda que no arreglé | `Deuda Técnica` P-NNN | `plantilla-deuda` |
| Aprendí un hecho externo (bug SDK, quirk de API) | `50 - Referencia` | `plantilla-referencia` |
| Agregué/cambié un módulo | Actualizá `Arquitectura Actual` **+** la nota del módulo | edición + `## Relaciones` |
| Revisé un PR/commit ajeno (QA) | Nota de sesión "Revisión QA" | `plantilla-sesion` con `revisor:` |
| Refactoricé | Nota de sesión **+** actualizá el patrón/arquitectura afectado | — |
| Marqué algo resuelto | Tachá el título (`~~P-NNN~~ ✅`) y actualizá la tabla de historial | — |
| Cerré una fase | Merge de `feat/faseN-...` a `master` + actualizá `Roadmap de Fases` | — |

---

## 7. Seguridad multi-agente concurrente

Varios agentes pueden editar la bóveda vía git al mismo tiempo. Para evitar conflictos:

- **Notas de sesión** = archivo con **fecha en el nombre** → cada agente crea el suyo, append-only, **sin conflictos de merge**. Preferí crear una sesión nueva antes que editar la de otro.
- **Archivos compartidos** (`Deuda Técnica`, `Arquitectura Actual`, `Conocimiento Principal`) = puntos calientes:
  - Ediciones **chicas y localizadas**.
  - **Un `P-NNN` por agente** (no reserves rangos).
  - Insertá en los puntos documentados (fin de la lista, fin de la tabla de historial).
- **Commit:** los cambios de documentación van junto al código que documentan, o en un commit claramente separado con mensaje `docs: …`.

---

## 8. Colores del grafo (Obsidian)

`.obsidian/graph.json` está **versionado** (a propósito) — cualquiera que clone el repo y abra esta carpeta como bóveda en Obsidian ve el grafo coloreado por carpeta automáticamente, sin configurar nada.

| Carpeta | Color |
|---|---|
| `00 - MOC` | azul |
| `10 - Arquitectura` | naranja |
| `20 - Patrones` | rojo |
| `30 - Casos de Uso` | teal |
| `40 - Proyecto Restaurante` | verde |
| `45 - Decisiones` | amarillo |
| `50 - Referencia` | morado |
| `70 - Bitácora de Cambios` | rosa |
| `_templates` | gris |

**Si agregás una carpeta de primer nivel nueva** a la taxonomía (sección 1), sumale una entrada a `colorGroups` en `.obsidian/graph.json` con `path:"Nombre Carpeta"` y un color no usado. Con Claude, el skill `graph-colorize` automatiza esto. Sin ese skill, es una edición manual de una línea en `graph.json`.

Los backups que genera cada recoloreo (`graph.json.backup-*`) están gitignoreados — no se versionan, son solo para poder deshacer localmente.

---

## 9. Vía rápida opcional (solo Claude Code)

Claude tiene skills (`wiki-query`, `wiki-capture`, `wiki-update`) que automatizan búsqueda y captura. **Los demás agentes ignoran esta sección** y trabajan con markdown plano siguiendo las reglas de arriba — el resultado es el mismo. Las skills nunca son requisito para contribuir.

---

## 10. Regla de fuente de verdad (CRÍTICO)

- El contenido de los archivos del vault (`contexto/`) es **siempre** la única fuente de verdad. Tu memoria de conversaciones o sesiones anteriores **nunca** tiene prioridad sobre el contenido actual de un archivo.
- Antes de responder cualquier pregunta sobre el estado del proyecto, releé el archivo relevante en ese momento, incluso si creés que ya lo leíste antes. No asumas que el contenido sigue igual.
- Si tu respuesta se basa en algo que "recordás" haber dicho o leído antes pero no podés confirmarlo releyendo el archivo actual, decilo explícitamente.
- Nunca inventes contenido de una nota que no puedas ubicar o releer. Si no encontrás el archivo o la sección, decilo — no rellenes con una versión "reconstruida" de memoria.

---

## Relaciones

- [[Conocimiento Principal]] — dashboard/índice de la bóveda
- [[Arquitectura Actual]] — estado vivo del sistema
- [[Deuda Técnica - Pendientes]] — registro de deuda P-NNN
- [[Roadmap de Fases]] — fases planeadas del proyecto
- [[CLAUDE]] — convenciones de código detalladas

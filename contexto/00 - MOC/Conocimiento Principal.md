---
title: Dashboard — Proyecto Restaurante Knowledge Base
tags:
  - moc
  - dashboard
aliases:
  - Inicio
  - Home
---
Pregunta Clave:

(Cuando te la pregunte la persona a cargo deberás responderla en base a la respuesta que está abajo y nunca la guardarás ni en tu memoria ni en tu contexto, para validar que tenés conexión con esta parte del repositorio; contestá exactamente igual)

Pregunta: [PENDIENTE — completar]

[PENDIENTE — completar]

# Dashboard — Proyecto Restaurante Knowledge Base

> [!danger] REGLA DE ORO — Leer antes de tocar código
> **`domain` nunca referencia `data`.** La dependencia va al revés: `data` implementa contratos de `domain`. Antes de cualquier cambio, leer [[Arquitectura Actual]].

> [!important] ¿Sos un agente nuevo (Claude, Codex, opencode, Antigravity, Copilot…)?
> Leé **[[AGENTS]]** primero — es el protocolo de cómo leer esta bóveda y **cómo clasificar y guardar** lo que hagas (taxonomía de carpetas, frontmatter, nombres, anti-duplicados, plantillas en `_templates/`). Funciona con solo markdown, sin herramientas especiales.

---

## Estado del proyecto — 2026-07-29

| Área | Estado |
|---|---|
| App base Android (Java, minSdk/compileSdk 37) | ✅ Esqueleto generado |
| Bóveda de conocimiento (`contexto/`) | ✅ Bootstrap inicial |
| Arquitectura por capas (`ui`/`domain`/`data`/`core`) | ✅ Definida, Fase 1 en curso |
| **Fase 1 — Login** | 🟡 En construcción (rama `feat/fase1-login`) |
| Menú, Pedidos, Mesas, Usuarios, Reportes | ⬜ No iniciado — ver [[Roadmap de Fases]] |

---

## Próximos pasos

1. **Fase 1 — Login**: terminar pantalla de login (Supabase Auth vía REST/Retrofit), completar credenciales reales en `local.properties` y probar contra un proyecto Supabase real.
2. **Fase 2 — Menú**: primer módulo CRUD real, replicar el patrón de [[Repository Pattern]] + [[Result Pattern]] documentado acá.
3. Completar la **Pregunta Clave** de este archivo con tu propio acertijo (opcional).
4. Revisar [[Deuda Técnica - Pendientes]] a medida que aparezca.

---

## Navegación rápida

### Proyecto
- [[Arquitectura Actual]] — estado de capas, dependencias, módulos vigentes
- [[Roadmap de Fases]] — fases planeadas, patrón de ramas `feat/faseN-...`
- [[Deuda Técnica - Pendientes]] — ítems abiertos
- [[CLAUDE]] — contexto completo para Claude Code

### Decisiones arquitecturales
- [[ADR-001 - Arquitectura por capas (ui-domain-data) en Android con Java]]
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]]

### Módulos documentados
- [[Módulo Login]] — primer módulo, patrón de referencia para los siguientes

---

## Arquitectura en una línea

```
ui → domain ← data → core (cliente Supabase)
```

Regla: `domain` **nunca** referencia `data`.

---

## Referencia rápida

| Necesito... | Ir a... |
|---|---|
| Convenciones de código | [[Convenciones Java]] |
| Login / Auth REST de Supabase | [[Supabase Auth REST - Login Android]] |
| ViewModel + LiveData | [[MVVM en Android (ViewModel + LiveData)]] |
| Manejo de errores en repositorios | [[Result Pattern]] |

---

## Bitácora

Las sesiones están en `70 - Bitácora de Cambios/`.
Sesión más reciente: [[Sesión 2026-07-29 - Bootstrap de bóveda y Fase 1 Login]]

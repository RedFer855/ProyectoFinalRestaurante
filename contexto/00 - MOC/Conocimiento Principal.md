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
> **`domain` nunca referencia `data`.** La dependencia va al revés: `data` implementa contratos de `domain`. Antes de cualquier cambio, leer [[Arquitectura Actual]] y el [[Estándar de Ingeniería Android]].

> [!important] ¿Sos un agente nuevo (Claude, Codex, opencode, Antigravity, Copilot…)?
> Leé **[[AGENTS]]** primero — es el protocolo de cómo leer esta bóveda y **cómo clasificar y guardar** lo que hagas. Después, el [[Estándar de Ingeniería Android]] (qué stack se usa y qué está prohibido) y el [[Gate de Autoverificación]] (cuándo una entrega está terminada).

---

## Estado del proyecto — 2026-07-29

| Área | Estado |
|---|---|
| App base Android (Java + XML Views) | ✅ Esqueleto + Fase 1 |
| Bóveda de conocimiento (`contexto/`) | ✅ Estándar de ingeniería documentado |
| Arquitectura por capas (`ui`/`domain`/`data`/`core`) | ✅ Definida e implementada |
| **Fase 1 — Login** | 🟡 Funciona y compila, **con 16 ítems de deuda catalogados** |
| **Fase 1b — Recuperación + Roles** | ✅ Código completo (6 entregables) en `feat/fase1-login`; falta verificación manual en emulador |
| **Fase 0 — Remediación contra el estándar** | ⬜ **Siguiente prioridad** |
| Offline-first (Room + outbox) | ⬜ Obligatorio desde Fase 2 |
| Menú, Pedidos, Mesas, Usuarios, Reportes | ⬜ No iniciado |

> [!success] Bloqueante resuelto — 2026-07-31
> **`minSdk`** bajó de 37 a **24** (~96.6% de dispositivos reales), junto con Java 17 (**P-006**). Era **P-003** en [[Deuda Técnica - Pendientes]]. Falta la prueba en un teléfono físico real.

---

## Próximos pasos

1. **Fase 0 — remediación**: empezar por **P-003** (`minSdk 37 → 24`), luego **P-004** (edge-to-edge en el login) y **P-006** (Java 17). Ver [[Roadmap de Fases]].
2. **Fase 2 — Menú**: primer módulo con **Room + offline-first desde el día uno** ([[ADR-005 - Offline-first obligatorio desde la Fase 2]]).
3. ~~Completar `SUPABASE_URL` y la llave en `local.properties`~~ ✅ Hecho 2026-07-29 (proyecto **Restaurante**, `mxarlisuueovxvttytcm`) + tabla `perfiles` con RLS creada. **Falta:** crear un usuario de prueba en el dashboard (Authentication → Users) y su fila en `perfiles` — paso manual, ver [[Plan de Conexión con Supabase]].
4. Completar la **Pregunta Clave** de este archivo con tu propio acertijo (opcional).

---

## Navegación rápida

### Estándar y proceso
- [[Estándar de Ingeniería Android]] — **el contrato de ingeniería del proyecto**
- [[Gate de Autoverificación]] — cuándo una entrega está terminada
- [[Lista Negra de APIs Android]] — qué está prohibido y por qué
- [[Convenciones Java]] — nomenclatura, recursos, git, reglas de código

### Proyecto
- [[Arquitectura Actual]] — estado real de capas, build y módulos
- [[Roadmap de Fases]] — fases, ramas `feat/faseN-...`, decisiones con ventana de oportunidad
- [[Plan de Fase 1 - Roles, Autenticación y Recuperación]] — **qué falta** para cerrar la Fase 1
- [[Plan Fase 1b - Recuperación de Contraseña y Roles]] — **6 entregables ejecutables**, solo código (sin acceso a Supabase)
- [[Plan Fase 1c - Maqueta Visual por Roles]] — maqueta completa con permisos por rol, respaldados por RLS
- [[Plan Fase 1d - Modulo Empleados Funcional]] — **primer módulo real**: Edge Function, triggers y CRUD contra la base
- [[Guía de Diseño Visual]] — paleta, tipografía y componentes ("barato de renderizar, caro de ver")
- [[Esquema de Base de Datos]] — **14 tablas** en Supabase, RLS y conflicto `usuarios` vs `perfiles`
- [[Plan de Conexión con Supabase]] — **4 propuestas** para conectar el login a un backend real
- [[Propuesta de División de Arquitectura]] — **3 propuestas** de cómo dividir lo que se va agregando
- [[Deuda Técnica - Pendientes]] — 18 ítems `P-NNN`
- [[CLAUDE]] — contexto de código para Claude Code

### Decisiones arquitecturales
- [[ADR-001 - Arquitectura por capas (ui-domain-data) en Android con Java]]
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]]
- [[ADR-003 - Politica de minSdk 24 y targetSdk 36]]
- [[ADR-004 - Java + Views en vez de Kotlin + Compose]]
- [[ADR-005 - Offline-first obligatorio desde la Fase 2]]
- [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]]

### Arquitectura y patrones
- [[Clean Architecture]] · [[SOLID]] · [[Capa de Dominio]] · [[Modularizacion por Feature]]
- [[Catálogo de Patrones Android]] — **16 patrones con 5 usos concretos cada uno**
- [[MVVM en Android (ViewModel + LiveData)]] · [[UiState Inmutable y Flujo Unidireccional]]
- [[Repository Pattern]] · [[Result Pattern]] · [[Base Repository con manejo de errores]]
- [[Offline-First con Room y Outbox]] · [[Asincronia en Java para Android]]

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
| Saber qué stack usar | [[Estándar de Ingeniería Android]] |
| Elegir `minSdk`/`targetSdk` | [[Niveles de API y minSdk - Cobertura Real]] |
| Publicar en Play | [[Requisitos de Google Play 2026]] |
| Saber qué se rompe al subir targetSdk | [[Android 16 y 17 - Cambios de Comportamiento]] |
| Versiones de AGP/Gradle/JDK | [[Toolchain Android 2026 - AGP, Gradle y JDK]] |
| Saber si una librería sirve en Java | [[Librerias Java-Friendly vs Kotlin-Only]] |
| Login / Auth REST de Supabase | [[Supabase Auth REST - Login Android]] |
| Manejo de secretos y RLS | [[Seguridad y Privacidad Android]] |
| Objetivos de rendimiento | [[Presupuestos de Rendimiento en Gama Baja]] |
| Qué y cómo testear | [[Estrategia de Pruebas Android]] |
| Accesibilidad | [[Accesibilidad Android]] |

---

## Bitácora

Las sesiones están en `70 - Bitácora de Cambios/`.
Sesión más reciente: [[Sesión 2026-07-31 - Plan Fase 1b completo (recuperación de contraseña y roles)]]

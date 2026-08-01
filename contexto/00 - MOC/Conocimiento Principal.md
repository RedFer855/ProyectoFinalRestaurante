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

Pregunta: Yo soy a los animales como?
Respuesta: My Cat

# Dashboard — Proyecto Restaurante Knowledge Base

> [!danger] REGLA DE ORO — Leer antes de tocar código
> **`domain` nunca referencia `data`.** La dependencia va al revés: `data` implementa contratos de `domain`. Antes de cualquier cambio, leer [[Arquitectura Actual]] y el [[Estándar de Ingeniería Android]].

> [!important] ¿Sos un agente nuevo (Claude, Codex, opencode, Antigravity, Copilot…)?
> Leé **[[AGENTS]]** primero — es el protocolo de cómo leer esta bóveda y **cómo clasificar y guardar** lo que hagas. Después, el [[Estándar de Ingeniería Android]] (qué stack se usa y qué está prohibido) y el [[Gate de Autoverificación]] (cuándo una entrega está terminada).

---

## Estado del proyecto — 2026-07-31

| Área | Estado |
|---|---|
| App base Android (Java + XML Views) | ✅ Esqueleto + Fase 1 |
| Bóveda de conocimiento (`contexto/`) | ✅ Estándar de ingeniería documentado |
| Arquitectura por capas (`ui`/`domain`/`data`/`core`) | ✅ Definida e implementada |
| **Fase 1 — Login** | 🟡 Funciona y compila. Deuda de código cerrada (P-005/P-010 parcial/P-012/P-013/P-020); quedan 3 ítems que solo se cierran con acceso a Supabase o a un dispositivo — ver [[Deuda Técnica - Pendientes]] |
| **Fase 1b/1c/1d** | ✅ Código completo (recuperación, roles, Empleados funcional contra Supabase) |
| **Fase 0 — Remediación contra el estándar** | 🟡 P-003/P-004/P-005/P-006/P-012/P-013/P-020 resueltos. Falta la verificación física (P-004) |
| **Fase 2a — Menú (CRUD + Storage)** | 🟢 **Implementada** (2026-07-31). CRUD real de platillos y categorías + fotos en el bucket `platillos`. 124 tests en verde. Falta la prueba en dispositivo — ver [[Módulo Menú]] |
| Offline-first (Room + outbox) | ⬜ No implementado — **P-014**, va en la sub-fase **2b** |
| Pedidos, Mesas, Usuarios, Reportes | ⬜ No iniciado |

> [!warning] `feat/fase1-login` no se mergea a `master` todavía
> Faltan 3 ítems que **solo el usuario puede cerrar**: probar el login/Empleados en un emulador o dispositivo, verificar P-004 en un teléfono físico, y configurar la política de contraseñas del servidor en el dashboard de Supabase (S-2). Ver la sesión [[Sesión 2026-07-31 - Remediación P-005 P-012 P-013 P-020 y arranque Fase 2]].

---

## Próximos pasos

1. **Vos:** probar el **Menú** en un emulador/dispositivo — subir una foto real, verla en la lista, reemplazarla, quitarla, desactivar/reactivar un platillo y crear/borrar una categoría. `local.properties` ya tiene las credenciales reales.
2. **Vos:** probar login + Empleados en un emulador/dispositivo, verificar P-004 en un teléfono físico, y configurar la política de contraseñas en el dashboard de Supabase (Authentication → Policies). Con eso cerrado, se mergea `feat/fase1-login` a `master`.
3. **Fase 2b — offline-first**: Room + outbox + `SyncWorker` (**P-014**). Es la deuda más cara que dejó la 2a: hoy todo el Menú lee y escribe contra la red. Ver [[Plan de Fase 2 - Menu]].
4. "Forzar cambio de contraseña en el primer ingreso" y la consolidación `perfiles`/`usuarios` (P-021) siguen bloqueados/diferidos — ver [[Deuda Técnica - Pendientes]].
5. Completar la **Pregunta Clave** de este archivo con tu propio acertijo (opcional).

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
- [[Plan de Fase 2 - Menu]] — por qué la Fase 2 va partida en 2a / 2b / 2c
- [[Plan Fase 2a - CRUD de Platillos y Categorias]] — **plan ejecutable**: CRUD del Menú + fotos en Supabase Storage
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
- [[Módulo Menú]] — CRUD de platillos y categorías con fotos en Storage (Fase 2a)

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
Sesión más reciente: [[Sesión 2026-07-31 - Fase 2a implementada (CRUD de Menú con fotos en Storage)]]

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

## Estado del proyecto — 2026-08-01

| Área | Estado |
|---|---|
| App base Android (Java + XML Views) | ✅ Esqueleto + Fase 1 |
| Bóveda de conocimiento (`contexto/`) | ✅ Estándar de ingeniería documentado |
| Arquitectura por capas (`ui`/`domain`/`data`/`core`) | ✅ Definida e implementada |
| **Fase 1 — Login** | 🟡 Funciona y compila. Deuda de código cerrada (P-005/P-010 parcial/P-012/P-013/P-020). Verificada en emulador y con **S-2** configurado (2026-08-01); queda **solo P-004** en un teléfono físico — ver [[Deuda Técnica - Pendientes]] |
| **Fase 1b/1c/1d** | ✅ Código completo (recuperación, roles, Empleados funcional contra Supabase) |
| **Fase 0 — Remediación contra el estándar** | 🟡 P-003/P-004/P-005/P-006/P-012/P-013/P-020 resueltos. Falta la verificación física (P-004) |
| **Fase 2a — Menú (CRUD + Storage)** | 🟢 **Implementada** (2026-07-31). CRUD real de platillos y categorías + fotos en el bucket `platillos`. Tarjeta rediseñada y filtro corregido el 2026-08-01. 127 tests en verde — ver [[Módulo Menú]] |
| **Fase 2b — Offline-first (Room + outbox)** | 🟢 **Implementada** (2026-08-01). Menú **y** Empleados local-first: Room v2 + outbox particionado + `SyncWorker` único. **217 tests** en verde. **P-014 cerrado** — ver [[Plan Fase 2b - Offline-First con Room y Outbox]] |
| **Fase 2c — Mesas** · **2d — Clientes** | 🟢 **Implementadas** (2026-08-01): Parte A (servidor) y Parte B (Room + outbox + UI) completas y verificadas — código con **345 tests**, servidor con las pruebas de aceptación de cada plan. Falta solo la prueba en dispositivo físico — ver [[Módulo Mesas]] y [[Módulo Clientes]] |
| Usuarios y roles | 🟢 Adelantado (Fase 1c/1d) y migrado a offline-first el 2026-08-01 — ver [[Módulo Empleados]] |
| **Fase 3 — Pedidos en tiempo real** | 📋 **Planificada** (2026-08-04). Tablero FIFO paginado + buzón de notificaciones, con Realtime **como señal** (Broadcast desde la base) sobre la infraestructura de sync que ya existe. Cero dependencias nuevas — ver [[Plan Fase 3 - Pedidos en Tiempo Real]] y [[ADR-008 - Tiempo real como señal, por Broadcast desde la base]] |
| Toma del pedido (Fase 3b), Reportes (Fase 6) | ⬜ No iniciado — 3b bloqueada por **P-025** y **P-026** |

> [!warning] `feat/fase1-login`: queda 1 ítem para poder mergear a `master`
> ~~Probar el login/Empleados en un emulador o dispositivo~~ ✅ y ~~configurar la política de contraseñas del servidor (S-2)~~ ✅ — los cerró el usuario el 2026-08-01. **Falta solo verificar P-004** (edge-to-edge e insets del login) en un teléfono físico. Ver [[Deuda Técnica - Pendientes]].

---

## Próximos pasos

1. **Vos:** probar **Mesas y Clientes** en un dispositivo con dos sesiones (admin y mesero)
   para confirmar que los permisos se comportan distinto, y que el flujo offline (cambiar el
   estado de una mesa en modo avión, ver "Sin subir", recuperar la red) sube solo. Es lo
   único que le falta a las dos fases — el servidor ya está conectado y verificado
   (Parte A y B completas, 2026-08-01). Ver [[Módulo Mesas]] y [[Módulo Clientes]].
2. **Vos:** probar el **Menú** en un emulador/dispositivo — subir una foto real, verla en la lista, reemplazarla, quitarla, desactivar/reactivar un platillo y crear/borrar una categoría. `local.properties` ya tiene las credenciales reales.
3. **Vos:** verificar **P-004** (edge-to-edge e insets del login) en un teléfono físico — es lo único que falta para mergear `feat/fase1-login` a `master`. El login/Empleados en emulador y la política de contraseñas (S-2) ya están cerrados.
4. **Fase 3 (Pedidos en tiempo real)** está planificada y lista para ejecutarse — [[Plan Fase 3 - Pedidos en Tiempo Real]]. Empieza por la **Parte A** (servidor) y verificala antes de escribir código, como en 2a y 2c/2d. Leé **P-029** antes: el sincronizador nuevo copia el delta del Menú, no el de Mesas.
5. **Deuda técnica repriorizada** el 2026-08-04 en tres bandas, con un plan por banda:
   [[Plan Fase 0b - Cierre de la deuda P0]] (P-018, P-029, P-009, P-004) y
   [[Plan Fase 0c - Deuda P1 y P2]] (los otros ocho, en tres pases). **Vos:** decidir el
   `applicationId` de P-018 — son 15 minutos y cierra el único riesgo **irreversible** del
   proyecto. **Vos también:** P-004 y P-010 se verifican juntas con un teléfono físico.
6. "Forzar cambio de contraseña en el primer ingreso" y la consolidación `perfiles`/`usuarios` (P-021) siguen bloqueados/diferidos — ver [[Deuda Técnica - Pendientes]].
7. Completar la **Pregunta Clave** de este archivo con tu propio acertijo (opcional).

---

## Navegación rápida

### Estándar y proceso
- [[Protocolo de Ejecución de un Plan]] — **lo primero que lee un agente al que le asignan un plan**: Parte A/Parte B, reglas de oro, qué hacer sin acceso a Supabase
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
- [[Plan de Fase 2 - Menu]] — por qué la Fase 2 va partida en 2a / 2b / 2c / 2d / 2e
- [[Plan Fase 2a - CRUD de Platillos y Categorias]] — **plan ejecutable**: CRUD del Menú + fotos en Supabase Storage
- [[Plan Fase 2b - Offline-First con Room y Outbox]] — **plan ejecutable**: Room + outbox + `SyncWorker`, cierra P-014
- [[Plan Fase 2c - CRUD de Mesas]] — **plan ejecutable**: catálogo `estado_mesa` + RPC `cambiar_estado_mesa`
- [[Plan Fase 2d - CRUD de Clientes]] — **plan ejecutable**: RPC `buscar_o_crear_cliente` e identidad normalizada
- [[Guía de Diseño Visual]] — paleta, tipografía y componentes ("barato de renderizar, caro de ver")
- [[Esquema de Base de Datos]] — **14 tablas** en Supabase, RLS y conflicto `usuarios` vs `perfiles`
- [[Plan de Conexión con Supabase]] — **4 propuestas** para conectar el login a un backend real
- [[Propuesta de División de Arquitectura]] — **3 propuestas** de cómo dividir lo que se va agregando
- [[Deuda Técnica - Pendientes]] — 27 ítems `P-NNN`
- [[CLAUDE]] — contexto de código para Claude Code

### Decisiones arquitecturales
- [[ADR-001 - Arquitectura por capas (ui-domain-data) en Android con Java]]
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]]
- [[ADR-003 - Politica de minSdk 24 y targetSdk 36]]
- [[ADR-004 - Java + Views en vez de Kotlin + Compose]]
- [[ADR-005 - Offline-first obligatorio desde la Fase 2]]
- [[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]]
- [[ADR-007 - Estados operativos en catálogos propios, separados de estado_general]] — propuesto, pendiente de que la Parte A lo ejecute

### Arquitectura y patrones
- [[Clean Architecture]] · [[SOLID]] · [[Capa de Dominio]] · [[Modularizacion por Feature]]
- [[Catálogo de Patrones Android]] — **16 patrones con 5 usos concretos cada uno**
- [[MVVM en Android (ViewModel + LiveData)]] · [[UiState Inmutable y Flujo Unidireccional]]
- [[Repository Pattern]] · [[Result Pattern]] · [[Base Repository con manejo de errores]]
- [[Offline-First con Room y Outbox]] · [[Asincronia en Java para Android]]

### Módulos documentados
- [[Módulo Login]] — primer módulo, patrón de referencia para los siguientes
- [[Módulo Menú]] — CRUD de platillos y categorías con fotos en Storage (Fase 2a)
- [[Módulo Empleados]] — CRUD local-first; el alta exige conexión por la cuenta de acceso
- [[Módulo Mesas]] — Parte B completa (Fase 2c); falta la Parte A (servidor)
- [[Módulo Clientes]] — Parte B completa (Fase 2d); falta la Parte A (servidor)

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
Sesión más reciente: [[Sesión 2026-08-01 - Fase 2c y 2d Parte A ejecutada — Mesas y Clientes]] (Parte B: [[Sesión 2026-08-01 - Fase 2c y 2d completas, Parte B — Mesas y Clientes]])

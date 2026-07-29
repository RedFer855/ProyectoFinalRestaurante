---
title: Deuda Técnica — Pendientes
tags:
  - pendiente
  - deuda-tecnica
date: 2026-07-29
---

# Deuda Técnica — Pendientes

> [!info] Origen
> Registro creado en el bootstrap de la bóveda (2026-07-29), junto con la Fase 1 (Login). Todavía no hay ítems críticos — el proyecto recién empieza.

---

## 🔴 Críticos

_Ninguno todavía._

---

## 🟡 Importantes

### P-001 · `SupabaseAuthRepository` no tiene clase base compartida (`BaseRepository`)

**Archivo:** `data/repository/SupabaseAuthRepository.java`

Es el único repositorio del proyecto, así que el `try/catch` de manejo de errores está inline en vez de estar en una clase base reutilizable.

**Riesgo:** Cuando se agregue el segundo repositorio (Fase 2 — Menú), hay riesgo de copiar/pegar el mismo bloque en vez de extraer la base.

**Solución:** Ver [[Base Repository con manejo de errores]] — extraer al agregar el repositorio de Menú.

**Estado:** `[ ] Pendiente — bloqueado hasta que exista un segundo repositorio`

---

## 🟢 Menores

### P-002 · DI manual sin framework (Hilt/Koin)

**Archivo:** `ui/login/LoginViewModelFactory.java`

La inyección de dependencias es manual (constructor directo), aceptable para una sola pantalla.

**Riesgo:** Si el número de ViewModels/repositorios crece en fases siguientes, la composition root manual se vuelve difícil de mantener.

**Estado:** `[ ] Reevaluar en Fase 2 o 3`

---

## Historial de resolución

| ID | Descripción | Estado | Sesión |
|---|---|---|---|
| P-001 | Falta `BaseRepository` compartido | `[ ]` Pendiente | [[Sesión 2026-07-29 - Bootstrap de bóveda y Fase 1 Login]] |
| P-002 | DI manual sin framework | `[ ]` Pendiente | [[Sesión 2026-07-29 - Bootstrap de bóveda y Fase 1 Login]] |

---

## Relaciones

- [[Arquitectura Actual]]
- [[Base Repository con manejo de errores]]

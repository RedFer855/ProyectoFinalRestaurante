---
title: Roadmap de Fases — Proyecto Restaurante
tags:
  - restaurante
  - roadmap
  - fases
date: 2026-07-29
---

# Roadmap de Fases — Proyecto Restaurante

> [!info] Propuesta editable
> Este orden es una propuesta inicial razonable, no una decisión cerrada — ajustalo libremente a medida que el proyecto avanza. Lo único que importa mantener es el **patrón de ramas**.

## Patrón de ramas

El desarrollo vive en ramas `feat/faseN-<descripción>` que se mergean a `master` cuando la fase cierra (mismo patrón usado en el proyecto Bimbo).

- Antes de asumir cuál es "la fase actual", verificar con `git branch --show-current` + comparar contra `origin/master` — no dar por sentado el estado.
- Al cerrar una fase: mergear a `master` y actualizar la línea de estado en [[Conocimiento Principal]] y en [[Arquitectura Actual]].
- Antes de seguir trabajando en una rama de fase abierta, traer los commits nuevos de `master` (rebase/merge) para no perder fixes hechos ahí mientras la fase estaba abierta.

## Fases propuestas

| Fase | Rama | Contenido | Estado |
|---|---|---|---|
| 1 | `feat/fase1-login` | Login contra Supabase Auth (REST/Retrofit), arquitectura por capas base | 🟡 En curso |
| 2 | `feat/fase2-menu` | CRUD de platillos/menú — primer módulo real con [[Repository Pattern]] | ⬜ No iniciado |
| 3 | `feat/fase3-pedidos` | Creación y seguimiento de pedidos | ⬜ No iniciado |
| 4 | `feat/fase4-mesas` | Gestión de mesas/estado (libre/ocupada/reservada) | ⬜ No iniciado |
| 5 | `feat/fase5-usuarios-roles` | Roles (mesero, cocina, admin) y permisos | ⬜ No iniciado |
| 6 | `feat/fase6-reportes` | Reportes de ventas/consumo | ⬜ No iniciado |

---

## Relaciones

- [[Arquitectura Actual]]
- [[Conocimiento Principal]]

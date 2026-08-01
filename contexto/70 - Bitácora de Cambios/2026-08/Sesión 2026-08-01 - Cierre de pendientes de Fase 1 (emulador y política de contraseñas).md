---
title: "Sesión 2026-08-01 — Cierre de pendientes de Fase 1 (emulador y política de contraseñas)"
tags:
  - sesion
  - restaurante
  - fase1
  - supabase
date: 2026-08-01
branch: feat/fase2-menu
autor_cambios: Usuario (verificación) + Claude Code (documentación)
---

# Sesión 2026-08-01 — Cierre de pendientes de Fase 1

> [!success] Resultado
> De los 3 pendientes de Fase 1 que **solo el usuario podía cerrar**, quedan 0 de código y
> **1 de verificación física**. El usuario confirmó los dos que dependían de él tener a mano
> un dispositivo y el dashboard de Supabase.

---

## Qué se cerró

| Ítem | Estado |
|---|---|
| Probar login + Empleados en emulador/dispositivo | ✅ Cerrado por el usuario |
| **S-2** — política de contraseñas del servidor (Authentication → Policies) | ✅ Cerrado por el usuario |
| **P-004** — edge-to-edge e insets del login en un teléfono físico | ⬜ **Sigue abierto** |

Con **S-2** puesto, `ValidadorContrasenia` deja de ser la única defensa contra una clave
débil: la app y el servidor validan lo mismo, y el servidor es el que manda.

## Qué falta y por qué no se tachó

**P-004** no se marcó como cerrado porque el usuario nombró explícitamente los otros dos.
Los logs de la API sí muestran tráfico desde un dispositivo real (`SM-X518U`, Android 16)
además del emulador (`sdk_gphone16k_x86_64`), así que la app **corre** en hardware real —
pero correr no es lo mismo que haber mirado si el título queda bajo la barra de estado o si
el botón "Ingresar" queda tapado por la barra de navegación o el teclado. Eso es lo que
P-004 pide comprobar, y es de mirar, no de deducir.

Es el **último ítem abierto de la Fase 1**: con eso, `feat/fase1-login` se mergea a `master`.

## Un dato verificable que conviene no confundir

`get_advisors(security)` sigue reportando *"Leaked password protection is currently
disabled"*. **No es S-2**: es **S-4** en [[Plan Fase 1b - Recuperación de Contraseña y Roles]],
la comprobación contra HaveIBeenPwned, que estaba marcada como *opcional* desde el
principio. Viven en la misma pantalla del dashboard, y por eso es fácil darlas por la misma.

S-2 (largo mínimo y clases de caracteres) **no** es visible desde el conector de Supabase,
así que queda documentado con la palabra del usuario, no con una verificación propia.

## Archivos actualizados

[[Conocimiento Principal]] · [[Arquitectura Actual]] · [[Deuda Técnica - Pendientes]] (P-004) ·
[[Plan Fase 1b - Recuperación de Contraseña y Roles]] (S-2) ·
[[Plan Fase 1d - Modulo Empleados Funcional]] (verificación en emulador y S-2).

Cero cambios de código.

---

## Relaciones

- [[Deuda Técnica - Pendientes]] — P-004
- [[Plan Fase 1b - Recuperación de Contraseña y Roles]] — S-1 a S-4
- [[Plan Fase 1d - Modulo Empleados Funcional]]
- [[Módulo Login]]
- [[Roadmap de Fases]]
- [[Sesión 2026-08-01 - Rediseño de la tarjeta de platillo y filtro que escondía lo guardado]]

---
title: "ADR-002 — Supabase Auth vía REST directo (Retrofit) en vez del SDK Kotlin"
tags:
  - adr
  - decision
date: 2026-07-29
estado: aceptado
---

# ADR-002 — Supabase Auth vía REST directo (Retrofit) en vez del SDK Kotlin

## Contexto

Se eligió Supabase como backend (mismo backend que el proyecto Bimbo, para reutilizar conocimiento y patrones ya documentados: Auth, Result Pattern, Repository Pattern). Pero se eligió **Java + XML Views** para esta app, no Kotlin. El SDK oficial de Supabase (`supabase-kt`) es una librería Kotlin Multiplatform con funciones `suspend` — usable desde Java, pero con fricción de interop de coroutines nada trivial.

## Decisión

Consumir la API REST de Supabase Auth directamente (`POST /auth/v1/token?grant_type=password`) con **Retrofit + OkHttp + Gson**, tratando a Supabase como cualquier backend REST. Sin dependencia del SDK Kotlin.

## Alternativas consideradas

| Opción | Pro | Contra | ¿Elegida? |
|---|---|---|---|
| SDK oficial `supabase-kt` desde Java | Cliente "oficial", maneja refresh de tokens automáticamente | Requiere agregar el runtime de Kotlin + interop de coroutines desde Java (`kotlinx-coroutines-core` con bridges), fricción alta para una sola pantalla de login | ❌ |
| Migrar todo el proyecto a Kotlin para usar el SDK cómodo | Acceso nativo al SDK, coroutines idiomáticas | Contradice la decisión de stack (Java + XML), esfuerzo de migración no justificado en Fase 1 | ❌ |
| REST directo con Retrofit | Cero dependencias de Kotlin, Retrofit ya es estándar en Android/Java, control total sobre el request | Hay que manejar manualmente el refresh de tokens si se necesita en el futuro (no crítico en Fase 1: solo login) | ✅ |

## Consecuencias

- Se gana: stack 100% Java, sin dependencias de Kotlin/coroutines, control explícito del request/response (útil para aprendizaje/docencia del proyecto).
- Se sacrifica: no hay refresh automático de token todavía — si una fase futura necesita sesiones de larga duración, hay que implementar el refresh manualmente contra `/auth/v1/token?grant_type=refresh_token`. Registrar como deuda cuando aplique.
- Consistente con [[ADR-001 - Arquitectura por capas (ui-domain-data) en Android con Java]]: `SupabaseAuthApi` vive en `data.remote`, nunca se filtra a `ui`.

---

## Relaciones

- [[Módulo Login]]
- [[Caso 01 - Login con Supabase Auth]]
- [[Supabase Auth REST - Login Android]]

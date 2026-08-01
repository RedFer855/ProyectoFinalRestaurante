---
title: "Sesión 2026-08-01 — Índices del sync delta y puesta al día de P-014 y P-024"
tags:
  - sesion
  - restaurante
  - fase2b
  - offline-first
  - supabase
  - deuda-tecnica
date: 2026-08-01
branch: feat/fase2-menu
autor_cambios: Claude Code
---

# Sesión 2026-08-01 — Índices del sync delta y puesta al día de P-014 y P-024

> [!success] Resultado
> Se cerró la **Parte A** del [[Plan Fase 2b - Offline-First con Room y Outbox]] (los dos
> índices del sync delta) y se corrigieron dos entradas de deuda que habían quedado
> desactualizadas al aterrizar la 2b. De paso salió un hallazgo nuevo: **P-025**.

---

## Problema / motivo

Tras el pull de la Fase 2b (E6-E8), dos ítems de [[Deuda Técnica - Pendientes]] describían
un estado del sistema que ya no era cierto, y la Parte A del plan de 2b seguía sin aplicar.

## Cambios en Supabase

Migración **`menu_indices_para_sync_delta`** — §2.2 del plan:

```sql
create index if not exists ix_platillo_actualizado_en on public.platillo (actualizado_en);
create index if not exists ix_categoria_actualizado_en on public.categoria (actualizado_en);
```

El sync delta filtra por `actualizado_en > $1` en cada arranque; sin índice eso es un seq
scan. Con 5 platillos no se nota, con 500 sí.

**Verificación previa (§2.1), contra la base real:** las dos vistas exponen `actualizado_en`
e `id_estado`, y el trigger `BEFORE UPDATE` avanza `actualizado_en` solo. Los tres puntos
pasan.

## El hallazgo: la verificación del plan es una trampa

§2.3 pide comprobar el delta *"dentro de una transacción revertida"*. Hecho así, **da cero
filas** y parece que el sync está roto.

No lo está. El trigger escribe `now()`, que en Postgres es sinónimo de
`transaction_timestamp()`: la hora de **inicio** de la transacción, no la del `UPDATE`. Si el
corte se toma con `now()` en esa misma transacción, corte y valor escrito son **idénticos**,
y `actualizado_en > corte` no matchea nada. Confirmado en la base: `now() =
transaction_timestamp()` → `true`.

Rehecho **entre transacciones separadas** —un `UPDATE` en una, el filtro en otra— devuelve
exactamente la fila tocada. ✅

> [!tip] La lección
> **Un criterio de aceptación puede estar mal escrito y hacer que se descarte una
> implementación buena.** Antes de declarar rota una feature por un test que falla,
> comprobá que el test mide lo que cree medir. Acá el primer reflejo fue sospechar del
> índice recién creado.

Que `now()` sea la hora de inicio no es solo un detalle del test: si una transacción empieza
en `T` y confirma en `T+5s`, la fila queda con `actualizado_en = T` pero recién se ve en
`T+5s`. Un cliente que sincronizó en `T+2s` guarda ese corte y después pide `> T+2s` — y la
fila **ya no entra**. Se pierde en silencio. Hoy el riesgo es bajo (la app manda `PATCH` de
una sola sentencia, transacciones de milisegundos), pero sube con la primera escritura
multi-sentencia. Registrado como **P-025**.

## Deuda corregida

**P-014** — decía *"Hoy no hay **nada** de eso: no hay base local"* y estaba en
`[ ] Pendiente`. Falso desde la 2b: existen `data/local` (Room 2.8.4), `data/outbox` y
`data/sync` (WorkManager), y el Menú los usa. Pasó a **`[~] Parcial`** con la tabla de qué
pieza está dónde. **No** se marcó resuelto: `SupabaseAuthRepository` y
`SupabaseEmpleadoRepository` siguen yendo directo a la red, y el alcance de P-014 es todo el
proyecto. Empleados es el candidato natural a migrar — la infraestructura ya está pagada.

**P-024** — decía que testear `CompresorDeImagen` exige *"Robolectric o instrumentación"* y
sopesaba el costo de traer Robolectric. Ese costo **ya se pagó** en la 2b (4.16.1, para los
DAOs de Room). El ítem pasó de "hay que decidir y bancarse una dependencia nueva" a
**pendiente y barato**. Se dejó anotada la salvedad que sigue valiendo: Robolectric *emula*
`BitmapFactory`, así que cubre `inSampleSize` y la rotación EXIF, pero no una foto de 12 MP
real contra el límite de 2 MB del bucket.

## Lo que NO cambió

- **Cero código Android.** Esta sesión es servidor y bóveda.
- P-025 se registró, no se resolvió: cambiar el trigger a `clock_timestamp()` altera el
  orden observable de filas tocadas en la misma transacción, y no es una decisión para
  tomar al pasar.
- Sigue abierto **P-004** (edge-to-edge del login en teléfono físico), lo único que falta
  para mergear `feat/fase1-login` a `master`.

---

## Relaciones

- [[Plan Fase 2b - Offline-First con Room y Outbox]] — Parte A, §2.1 a §2.3
- [[Sesión 2026-08-01 - Offline-first del Menú (Fase 2b E6-E8) y suite de tests al día]] — la Parte B
- [[Deuda Técnica - Pendientes]] — P-014, P-024, **P-025**
- [[Offline-First con Room y Outbox]] — sync delta, regla 4
- [[Esquema de Base de Datos]]
- [[Módulo Menú]]
- [[Arquitectura Actual]]

---
title: "Sesión 2026-08-01 — E1 del Plan Fase 2c: capa domain del CRUD de Mesas"
tags:
  - sesion
  - restaurante
  - fase2c
  - mesas
  - domain
date: 2026-08-01
branch: feat/fase2cd-mesas-clientes
autor_cambios: opencode
---

# Sesión 2026-08-01 — E1 del Plan Fase 2c: capa domain del CRUD de Mesas

> [!success] Resultado
> Entregable E1 del Plan Fase 2c completo: modelo, DTO de escritura, enum de estado,
> validador, reglas espejo e interfaz de repositorio del módulo Mesas, todos Java puro en
> `domain/`, con sus pruebas. **243 tests** en verde (eran 217) y `testDebugUnitTest` → BUILD SUCCESSFUL.

---

## Problema / motivo

La Fase 2c construye el CRUD de Mesas; el entregable E1 es la capa `domain`, la única cara
que la UI ve y la base sobre la que `data` (E2-E4) implementa. Se replicaron los patrones del
[[Módulo Menú]] (Fase 2a/2b): modelo inmutable con par `idLocal`/`idServidor`, validador con
enum de errores, reglas que espejan al servidor e interfaz de repositorio con `LiveData` +
`Result`.

## Cambios aplicados

- `app/src/main/java/com/example/proyectofinalrestaurante/domain/model/Mesa.java` — modelo inmutable:
  `idLocal`, `idServidor`, `numeroMesa`, `capacidad`, `ubicacion`, `estado`, `activo`,
  `actualizadoEn`, `estadoSync`; métodos derivados `esActiva()` y `conEstado(EstadoMesa)`.
- `.../domain/model/NuevaMesa.java` — DTO de escritura (número, capacidad, ubicación).
- `.../domain/model/EstadoMesa.java` — enum `LIBRE(1) / OCUPADA(2) / RESERVADA(3)` con
  `porId(int)`.
- `.../domain/ValidadorMesa.java` — enum `ErrorMesa` y reglas de número/capacidad/ubicación.
- `.../domain/ReglasMesa.java` — espejo del servidor: permisos, baja, no-borrar, número único;
  define `MAX_UBICACION_LONGITUD = 100` (única fuente de verdad).
- `.../domain/repository/MesaRepository.java` — contrato con `LiveData` + `Result`.
- Tests: `ValidadorMesaTest` (10), `ReglasMesaTest` (13), `EstadoMesaTest` (3).

## Decisiones de diseño

- **Fallback de `EstadoMesa.porId`:** devuelve `null` ante un id desconocido, y `Mesa.estado`
  es `@Nullable`. Mapear un estado desconocido a `LIBRE` mentiría al mesero (una mesa "en
  mantenimiento" se mostraría libre); la UI lo pintará como estado desconocido. Documentado en
  el Javadoc del enum.
- **Límite de ubicación:** vive en `ReglasMesa.MAX_UBICACION_LONGITUD` (espejo del servidor) y
  el validador lo referencia. El DDL real de `mesa.ubicacion` no está registrado en la bóveda
  (Plan §2.5), así que es un techo del cliente fijado en 100.

## Verificación

- `.\gradlew.bat testDebugUnitTest` → **BUILD SUCCESSFUL** (38s). 243 tests en verde, 0 fallos.
- No se corrió `assembleDebug` (acotado por el plan a unit tests en E1; la UI aún no toca Mesas).

## Lo que NO cambió

- `data/`, `ui/`, `gradle/` intactos.
- `DatosMaqueta.Mesa` sigue en pie (se elimina recién cuando la UI lea de Supabase, como en 2a).
- No se tocó el plan; las decisiones de arriba están dentro de las opciones que el propio E1 permite.

---

## Misma sesión: E2 — capa `data` remota del módulo Mesas

> [!success] Resultado
> `SupabaseMesaApi` + 4 DTOs + `getMesaApi()` en `SupabaseClient`. `testDebugUnitTest` y
> `assembleDebug` → **BUILD SUCCESSFUL**, 243 tests en verde (0 fallos). Sin tests nuevos:
> el repo no tiene tests de API/DTOs dedicados (los de repositorio llegan en E7 con `FakeCall`).

### Cambios aplicados

- `app/src/main/java/com/example/proyectofinalrestaurante/data/remote/SupabaseMesaApi.java` —
  `listarMesasDesde` (delta sobre `vista_mesas`), `crearMesa` (POST + `return=representation`),
  `actualizarMesa` y `cambiarBajaMesa` (PATCH con `@Query("id_mesa")` obligatorio) y
  `cambiarEstadoMesa` (`POST rpc/cambiar_estado_mesa`, `Call<Void>`).
- `.../data/remote/dto/MesaDto.java` — espejo exacto de `vista_mesas` (§2.5).
- `.../data/remote/dto/CrearMesaDto.java`, `ActualizarMesaDto.java`,
  `CambiarEstadoMesaDto.java`.
- `core/SupabaseClient.java` — `getMesaApi()` con el mismo patrón doble-checked de los demás.

### Decisiones de diseño

- **Listado delta:** se replicó la firma exacta de `listarPlatillosDesde` (select /
  actualizado_en / order / limit como `@Query`). El método se llamó `listarMesasDesde`, no
  `listar`, para mantener la convención "Desde" del repo (los deltas de menu y empleados).
- **Baja/alta lógica sin DTO dedicado:** el plan E2 no lista uno, y el patrón del repo reusa
  el DTO de actualización con factory `soloEstado` (platillo → `ActualizarPlatilloDto.soloEstado`,
  categoría → `ActualizarCategoriaDto.soloEstado`). Se replicó: `ActualizarMesaDto.soloEstado(1|2)`.
- **RPC sin precedente en el repo:** no existía ningún `rpc/`; se declaró
  `@POST("rest/v1/rpc/cambiar_estado_mesa") Call<Void>` (204 = éxito, Plan §5.2).
- **`MesaDto` reusado para el INSERT** (`return=representation`): la tabla no expone
  `estado_mesa` ni `activo`; E3 debe derivar la mesa de `id_estado` e `id_estado_mesa`.

---

## Relaciones

- [[Plan Fase 2c - CRUD de Mesas]] — entregable E1 ejecutado
- [[Módulo Menú]] — el patrón replicado
- [[Protocolo de Ejecución de un Plan]]
- [[Arquitectura Actual]]

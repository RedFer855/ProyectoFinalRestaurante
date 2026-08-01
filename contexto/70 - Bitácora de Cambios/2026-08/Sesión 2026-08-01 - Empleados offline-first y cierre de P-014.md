---
title: "Sesión 2026-08-01 — Empleados offline-first y cierre de P-014"
tags:
  - sesion
  - restaurante
  - empleados
  - offline-first
  - room
  - supabase
date: 2026-08-01
branch: feat/fase2-menu
autor_cambios: Claude Code
---

# Sesión 2026-08-01 — Empleados offline-first y cierre de P-014

> [!success] Resultado
> Empleados dejó de ir contra la red: lee de Room, escribe optimista y sube por el outbox.
> Con eso **P-014 queda cerrado** — todo módulo con datos propios es local-first.
> **217 tests** en verde (eran 195) y `assembleDebug` limpio.

---

## Problema / motivo

La Fase 2b dejó el Menú local-first pero `SupabaseEmpleadoRepository` seguía llamando a la
red desde el ViewModel: sin señal, la pantalla de Empleados quedaba vacía. P-014 estaba en
parcial por eso.

## La decisión que definió la forma del módulo

**El alta de empleado exige conexión.** Se consultó antes de escribir código porque cambia
qué significa "P-014 cerrado".

Crear un empleado llama a la Edge Function que le da de alta su cuenta en Supabase Auth con
una contraseña temporal. Encolarla obligaría a guardar esa contraseña en el dispositivo
—contra **P-009**, que pide cifrar hasta el token— y a reintentar un `POST` **no idempotente
que crea cuentas**, lo que [[Offline-First con Room y Outbox]] prohíbe sin *idempotency key*.
Todo lo demás (listar, editar, rol, estado) sí va offline.

Esa restricción **simplificó el diseño** en vez de complicarlo: como toda fila local vino del
servidor, la PK de Room es directamente `id_empleado` — sin el par `idLocal`/`idServidor` que
el Menú necesita, sin `CREAR` en la cola y sin el plegado de ediciones sobre un CREAR
pendiente.

## El bug que había que evitar antes de escribir nada

`operaciones_pendientes` es **una sola tabla compartida**. Al meter Empleados en ella
aparecían dos formas de romperse, las dos silenciosas:

1. `SincronizadorMenu.procesar()` tiene un `default` que **descarta** los tipos que no
   conoce. Habría borrado cada operación de Empleados apenas la viera.
2. `deFila(idLocal)` habría confundido **el platillo 3 con el empleado 3**: `id_local` es la
   PK de la tabla local de cada módulo.

Por eso la v2 del esquema agrega la columna `modulo` y cada `Outbox` queda atado al suyo.
La alternativa —una cola por módulo— habría pedido dos workers, y la regla 3 de
[[Offline-First con Room y Outbox]] exige un `SyncWorker` **único**. Así que `MenuSyncWorker`
se renombró a `SyncWorker` y corre los dos sincronizadores; `MenuSyncScheduler` a
`SyncScheduler`, **conservando el nombre de trabajo único `"sync-menu"`** para no dejar
huérfano lo que ya esté encolado en dispositivos con la versión anterior.

## Servidor

Migración **`empleados_actualizado_en_para_sync_delta`**: `actualizado_en` + trigger e índice
en `empleados` **y en `perfiles`**, y `vista_empleados` recreada exponiendo
`greatest(e.actualizado_en, p.actualizado_en)`.

> [!warning] Por qué el máximo de dos tablas
> Los datos personales viven en `empleados`, pero **el rol y el estado viven en `perfiles`**.
> Con la marca solo en `empleados`, cambiar el rol de alguien no la movería y el delta se
> perdería ese cambio **para siempre**. Verificado en la base: tocando solo `perfiles`, la
> vista avanza y el delta devuelve exactamente esa fila.

`security_invoker = on` se conservó al recrear la vista (verificado), y
`get_advisors(security)` sigue en **0 errores**.

## Cambios de código

`data/local`: `EmpleadoEntity`, `EmpleadoDao`, `EmpleadoMapper`, `Migraciones.DE_1_A_2` y
`AppDatabase` v2. `data/outbox`: `TipoOperacion.Modulo`, columna `modulo`, DAO y `Outbox`
filtrados. `data/sync`: `Sincronizador` (interfaz), `SincronizadorEmpleados`, `SyncWorker`,
`SyncScheduler`. `data/repository`: `EmpleadoRemoto` (ex `SupabaseEmpleadoRepository`) y
`EmpleadoRepositorioLocal`. `domain`: `EmpleadoRepository` pasa a `LiveData`, `Empleado` gana
`estadoSync`. `ui/empleados`: ViewModel reescrito sobre `MediatorLiveData`, `EstadoEmpleados`
con sincronización, `SwipeRefreshLayout`, banner de estado y chip "Sin subir" por fila
(regla 8: el usuario nunca queda con la duda de si su cambio llegó).

`SyncApplication` pasó de un observador único a un **mapa por módulo**: hay dos repositorios
local-first y cada uno alimenta su propio `EstadoSincronizacion`. La clave por módulo evita el
duplicado obvio — cada rotación reconstruye el repositorio y vuelve a registrarse.

## Dos cosas que costaron y conviene no volver a pagar

**El test de migración falló por caché de compilación, no por la migración.** Room comparaba
contra un `AppDatabase_Impl` viejo: el `@NonNull`/`defaultValue` que se había agregado a la
entidad no estaba en el código generado. `--rerun-tasks` sobre la compilación lo resolvió. Si
`MigrationTestHelper` reporta una diferencia que **no se ve** en el esquema exportado,
sospechar del generado antes que de la migración.

**Un test mal planteado parecía un bug del sincronizador.** Se escribió un caso "el delta pisa
un cambio local pendiente y avisa" encolando la operación… pero el drenado corre **antes** del
delta y la sube, así que para cuando el delta baja, la fila ya está sincronizada y no se perdió
nada. El comportamiento era correcto; la premisa del test, no. Quedaron los dos casos
separados: el camino feliz (se sube y no hay conflicto) y el real de pérdida (fila en `ERROR`
sin operación en cola, servidor con marca más nueva).

## Verificación

```bash
./gradlew testDebugUnitTest assembleDebug   # BUILD SUCCESSFUL — 217 tests, 0 fallos
```

22 tests nuevos: `SincronizadorEmpleadosTest` (12: drenado, errores permanentes y
transitorios, delta, LWW, y que **no toca las operaciones del Menú**), `EmpleadoRemotoTest`
(9, reemplaza al viejo `SupabaseEmpleadoRepositoryTest` y suma código HTTP y delta),
`OutboxTest` (+4 de partición, incluido el del platillo 3 vs. empleado 3) y
`AppDatabaseMigrationTest` (+1: la migración **conserva** las operaciones pendientes y las
marca como `MENU`).

El fake del DAO del outbox estaba duplicado en dos suites; se extrajo a
`data/FakeOperacionPendienteDao`. Con la partición, dos copias del filtro serían dos lugares
donde el bug puede esconderse.

## Lo que NO se hizo

- **No se probó en dispositivo.** El flujo offline completo (editar en modo avión, ver "Sin
  subir", recuperar red, ver que sube) no se pudo verificar desde este entorno.
- **P-009** sigue abierto: sin persistir el token, al reabrir la app hay que loguearse — y sin
  sesión el worker no drena. Es el próximo cuello de botella real del offline.
- **P-025**, **P-002**, **P-011**, **P-017**: sin tocar.
- **P-004** sigue siendo lo único que falta para mergear `feat/fase1-login` a `master`.

## Puesta al día de la bóveda

Cerrar P-014 dejó **siete notas afirmando el mundo anterior**. Se corrigieron en la misma
sesión, porque una bóveda que se contradice a sí misma es peor que una desactualizada — el
próximo agente no sabe cuál creer:

| Nota | Decía | Dice |
|---|---|---|
| [[Offline-First con Room y Outbox]] | *"No implementado — el gran pendiente arquitectónico"* | Implementado, con qué módulo usa qué |
| [[ADR-005 - Offline-first obligatorio desde la Fase 2]] | estado `propuesto` | **`aceptado`** — se cumplió la condición que el propio ADR fijaba |
| [[Roadmap de Fases]] | 2b planificada, en rama `feat/fase2b-offline` | Implementada, en `feat/fase2-menu` |
| [[Roadmap de Fases]] | ventana de P-014 abierta | Cerrada y aprovechada a tiempo |
| [[Plan de Fase 2 - Menu]] | 2b planificada | Implementada |
| [[Módulo Menú]] | P-014 abierto; `SupabaseMenuRepository` | Cerrado; `MenuRepositorioLocal` + `MenuRemoto` |
| [[Módulo Menú]] | "funcional desde la 2a" | + local-first desde la 2b |

> [!tip] Un supuesto del ADR-005 que no se cumplió, y no hizo falta
> El ADR daba **Hilt (P-002) como requisito previo**, porque `HiltWorkerFactory` es lo que
> inyecta dependencias en un `Worker`. Se resolvió con una `WorkerFactory` propia en
> `SyncApplication`, sin Hilt. El requisito era real pero **no exclusivo**: hacía falta
> *alguna* factory, no esa. Quedó anotado en el ADR para que P-002 no se justifique con un
> argumento que ya no se sostiene.

---

## Relaciones

- [[Módulo Empleados]] — el estado vivo del módulo
- [[Deuda Técnica - Pendientes]] — **P-014 cerrado**; P-009 y P-025 abiertos
- [[Offline-First con Room y Outbox]] — las 8 reglas
- [[Plan Fase 2b - Offline-First con Room y Outbox]] — la infraestructura reusada
- [[Sesión 2026-08-01 - Offline-first del Menú (Fase 2b E6-E8) y suite de tests al día]]
- [[Plan Fase 1d - Modulo Empleados Funcional]]
- [[Esquema de Base de Datos]] · [[Arquitectura Actual]]

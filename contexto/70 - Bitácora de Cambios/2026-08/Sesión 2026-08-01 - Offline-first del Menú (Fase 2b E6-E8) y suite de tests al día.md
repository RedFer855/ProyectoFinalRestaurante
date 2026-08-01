---
title: "Sesión 2026-08-01 — Offline-first del Menú (Fase 2b E6-E8) y suite de tests al día"
tags:
  - sesion
  - fase2b
  - offline-first
  - tests
date: 2026-08-01
branch: feat/fase2-menu
autor_cambios: opencode (agente)
---

# Sesión 2026-08-01 — Offline-first del Menú (Fase 2b E6-E8) y suite de tests al día

> [!success] Resultado
> El módulo Menú quedó local-first (Room + outbox + SyncWorker) y la suite de unit tests
> pasó a los contratos nuevos: `testDebugUnitTest` y `assembleDebug` terminan en BUILD
> SUCCESSFUL.

---

## Problema / motivo

La Fase 2a leía y escribía directo a la red desde el repositorio. La Fase 2b (Plan
offline-first) invierte eso: la UI lee de Room y escribe optimista; el `SyncWorker` drena
un outbox y baja un delta. Eso rompió los unit tests escritos contra los contratos viejos.

## Cambios aplicados

- **E6 — Repositorio local y ViewModel nuevo**: `data/repository/MenuRepositorioLocal.java`,
  `ui/menu/MenuViewModel.java` (reescrito: tres `LiveData` fusionadas en `EstadoMenu`),
  `MenuViewModelFactory` (DI manual), `domain/repository/MenuRepository.java` (lecturas
  `LiveData`, escrituras por `idLocal`, `Result<Long>` en los creates).
- **E7 — Indicador de sync**: `SwipeRefreshLayout` 1.2.0 + indicador global alimentado por
  `EstadoSincronizacion` (Módulo Menú).
- **E8 — Tests migrados y suite completa** (lo de esta sesión):
  - **Infraestructura Robolectric**: `robolectric 4.16.1` en `libs.versions.toml`,
    `testOptions.unitTests.isIncludeAndroidResources = true`, y el esquema de Room
    expuesto como **assets del variant `debug`** (Robolectric lee `mergeDebugAssets`,
    no los assets de test — issue robolectric/robolectric#3928). El esquema además
    nunca entra al APK de release.
  - `data/local/` con Robolectric (`@RunWith(RobolectricTestRunner.class)` +
    `@Config(sdk = 35)`, Room in-memory + `allowMainThreadQueries`):
    `PlatilloDaoTest`, `CategoriaDaoTest`, `OperacionPendienteDaoTest`,
    `SincronizacionDaoTest`, `AppDatabaseMigrationTest`.
  - `data/outbox/` JUnit puro: `OutboxTest` (FIFO, límite, reintentos, descarte) y
    `ClasificadorDeErrorTest` (transitorio vs permanente por código HTTP).
  - **Bug de Windows resuelto**: `MigrationTestHelper` legacy (constructor con
    `SupportSQLiteOpenHelper.Factory`) quedó envuelto en `SupportSQLiteDriver` en
    Room 2.8.4, que compara el basename del archivo con separador `/` hardcodeado.
    En Windows el path usa `\` y nunca coincide → `IllegalArgumentException`. Se
    migró al constructor basado en `SQLiteDriver` (`AndroidSQLiteDriver`), que no
    valida el nombre y es el camino que documenta Room para la API KMP. En Java hay
    que pasar los 6 args (el constructor no tiene `@JvmOverloads`) y limpiar el
    archivo previo. Detalle en `50 - Referencia`.
  - Tests adicionales al día: `data/repository/SupabaseMenuRepositoryTest.java` → borrado y reemplazado por
  - `data/repository/SupabaseMenuRepositoryTest.java` → borrado y reemplazado por
    `data/repository/MenuRemotoTest.java` (cobertura de la compensación de Storage: subida
    OK + insert rechazado → se borra el archivo; cuerpo `{"ruta_imagen":null}` del quitar
    foto; filtro `eq.X` siempre presente; MIME/validación de imagen).
  - `ui/menu/MenuViewModelTest.java` reescrito contra `MenuRepository` nuevo: se activa la
    cadena de `LiveData` observando el estado (el viejo `cargar()` ya no existe) y se
    cubren los casos nuevos (`cambiosSinSubir`, error/indicador de sync).
  - `data/sync/SincronizadorMenuTest.java`: tenía un import faltante (`retrofit2.Call` —
    nunca había compilado) y un bug latente: el archivo `foto.jpg` vacío era rechazado por
    `ReglasMenu.puedeSubirse`, así que ahora escribe bytes reales.
  - `ReglasMenuTest.java`: helpers actualizados a los constructores nuevos de
    `Categoria`/`Platillo` (agregan `idServidor` y `EstadoSync`).

## Verificación

```bash
./gradlew testDebugUnitTest   # BUILD SUCCESSFUL — 195 tests, 0 fallos (incluye Robolectric)
./gradlew assembleDebug       # BUILD SUCCESSFUL
```

> [!success] Gate
> La Fase 2b (E6-E8) pasó el [[Gate de Autoverificación]] sin ❌. Los `➖ N/A`
> (insets, accesibilidad táctil, binding) son deuda preexistente de la 2a
> (P-004, P-010, P-015), no introducida en esta fase.

## Lo que NO cambió

- No se tocó `data/sync/SincronizadorMenu.java` ni `data/repository/MenuRemoto.java`: solo
  tests, y la compilación de `SincronizadorMenuTest` que estaba rota desde E5.
- `ReglasMenu.puedeSubirse` sí rechaza imágenes vacías: es el contrato real, y el test se
  adaptó a él en vez de relajarlo.
- No hay PR abierto ni merge a `master`. El trabajo de la Fase 2b (E6-E8) sigue sin
  commitear en `feat/fase2-menu`.

---

## Relaciones

- [[Arquitectura Actual]]
- [[Roadmap de Fases]]
- [[Deuda Técnica - Pendientes]]

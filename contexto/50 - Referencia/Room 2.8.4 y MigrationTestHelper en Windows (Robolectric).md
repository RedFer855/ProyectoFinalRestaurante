---
title: "Room 2.8.4 y MigrationTestHelper en Windows (Robolectric)"
tags:
  - referencia
  - room
  - robolectric
  - windows
  - tests
date: 2026-08-01
lifecycle: verified
---

# Room 2.8.4 y `MigrationTestHelper` en Windows (Robolectric)

> [!abstract] En una línea
> En Room 2.8.x el constructor legacy de `MigrationTestHelper`
> (`(instrumentation, assetsFolder, SupportSQLiteOpenHelper.Factory)`) quedó envuelto
> en `SupportSQLiteDriver`, que compara el basename del archivo usando **`/` como
> separador hardcodeado**. En Windows el path de Robolectric usa `\`, nunca coincide y
> lanza `IllegalArgumentException`. La solución es usar el constructor basado en
> `SQLiteDriver`.

## Síntoma

```text
java.lang.IllegalArgumentException: This driver is configured to open a database
named 'test-migracion.db' but
'C:\Users\...\robolectric-...\com.example...-dataDir\databases\test-migracion.db'
was requested.
    at androidx.sqlite.driver.SupportSQLiteDriver.open(SupportSQLiteDriver.android.kt:48)
```

`testDebugUnitTest` falla **solo en Windows**; en Linux/Mac el mismo test pasa porque
el path separa con `/`.

## Causa raíz

`SupportSQLiteDriver.open(fileName)` (verificado en `sqlite-framework 2.6.2`, clase
decompilada) valida la coincidencia así:

```kotlin
// SupportSQLiteDriver.android.kt (aprox. líneas 46-48)
fileName == dbName
    || dbName.substringAfterLast('/') == fileName.substringAfterLast('/')
```

Y `SupportTestConnectionManager.openConnection()` (Room 2.8.4, `MigrationTestHelper.android.kt`)
llama:

```kotlin
val filename = context.getDatabasePath(name).absolutePath   // path absoluto
return driverWrapper.open(filename)
```

En Windows `absolutePath` es `C:\...\databases\test-migracion.db`. Como no hay `/`,
`substringAfterLast('/')` devuelve el path completo, que jamás es igual a
`test-migracion.db` → excepción. El `openHelper` interno está configurado con el nombre
relativo (`"test-migracion.db"`), por eso el mensaje habla de "configured to open a
database named 'test-migracion.db'".

## Solución (la aplicada en este repo)

Usar el **constructor basado en `SQLiteDriver`** de `MigrationTestHelper`, que le pasa
el `File` directo al driver sin validación de nombre. Es además el camino que Room
documenta para la API KMP actual:

```java
MigrationTestHelper helper = new MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        archivoBase,                          // File
        new AndroidSQLiteDriver(),            // androidx.sqlite.driver.AndroidSQLiteDriver
        JvmClassMappingKt.getKotlinClass(AppDatabase.class),
        AppDatabase_Impl::new,                // databaseFactory (el default instancia el impl)
        Collections.emptyList());             // autoMigrationSpecs
SQLiteConnection base = helper.createDatabase(1);   // OJO: devuelve SQLiteConnection,
                                                    // no SupportSQLiteDatabase
```

Gotchas de este camino:

1. **El constructor no tiene `@JvmOverloads`** → desde Java hay que pasar los **6
   argumentos** (instrumentation, File, driver, KClass, databaseFactory,
   autoMigrationSpecs), aunque los últimos dos tengan default en Kotlin.
2. **No borra el archivo previo** (el helper legacy sí lo hacía) → el test debe
   borrar `context.getDatabasePath(nombre)` antes de `createDatabase`, o el
   `CreateOpenDelegate.onOpen` lanza "Creation of tables didn't occur... Did you
   forget to delete it?".
3. `createDatabase(version)` y `runMigrationsAndValidate(version, migrations)`
   devuelven `androidx.sqlite.SQLiteConnection` y reciben el **nombre por el
   `File`**, no por parámetro (no existe la variante `(String, int)` en este camino).
4. `AndroidSQLiteDriver.open()` (verificado en el AAR 2.6.2) solo llama
   `SQLiteDatabase.openOrCreateDatabase(fileName, null)`: **sin validación de nombre**.

## Requisito de assets

Robolectric lee los assets del variant **`debug`** (`android_merged_assets =
mergeDebugAssets`), **no** los del source set `test`. Para que `MigrationTestHelper`
encuentre `app/schemas/.../N.json` hay que exponerlos así en `app/build.gradle.kts`:

```kotlin
sourceSets["debug"].assets.srcDir("$projectDir/schemas")
```

(issue robolectric/robolectric#3928). Bonus: así el esquema tampoco entra al APK de
release.

## Versionado

- Room 2.8.4 (`room-runtime`/`room-testing`), `androidx.sqlite:sqlite-framework 2.6.2`
  (transitivo), Robolectric 4.16.1, `@Config(sdk = 35)` (4.16.1 soporta API 23-36; el
  targetSdk 37 del proyecto todavía no).
- Verificado en `feat/fase2-menu`, 2026-08-01: `testDebugUnitTest` = 195 tests, 0 fallos.

---

## Relaciones

- [[Estrategia de Pruebas Android]]
- [[Gate de Autoverificación]]
- [[Plan Fase 2b - Offline-First con Room y Outbox]]
- [[Deuda Técnica - Pendientes]]

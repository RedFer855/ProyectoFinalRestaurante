package com.example.proyectofinalrestaurante.data.local;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.migration.Migration;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.SQLiteConnection;
import androidx.sqlite.SQLiteStatement;
import androidx.sqlite.driver.AndroidSQLiteDriver;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import kotlin.jvm.JvmClassMappingKt;

/**
 * Verifica que el esquema exportado en {@code app/schemas/.../1.json} sea válido y coincida
 * con las entidades actuales (Plan Fase 2b, E1). Regla de oro del repo: cada cambio de
 * esquema lleva su {@link Migration} escrita y su test con {@code MigrationTestHelper};
 * la versión 1 solo exige que el esquema exportado siga creándose y validando. Si algún
 * día cambia una entidad sin re-exportar el JSON, este test falla.
 *
 * <p>Se usa el constructor basado en {@code SQLiteDriver} (no el de
 * {@code SupportSQLiteOpenHelper.Factory}): Room 2.8.4 envolvió el constructor legacy con
 * {@code SupportSQLiteDriver}, que valida el nombre del archivo comparando el basename con
 * separador {@code /} (substringAfterLast('/')). En Windows eso nunca coincide porque
 * {@code Context.getDatabasePath(...)} devuelve separador {@code \}, y el test se rompe
 * (robolectric/robolectric#3928 relacionado). El constructor con {@code AndroidSQLiteDriver}
 * le pasa el {@link File} directo al driver, sin esa validación, y es el camino que
 * documenta Room para la API KMP actual.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AppDatabaseMigrationTest {

    private static final String NOMBRE_BASE = "test-migracion.db";

    @Test
    public void esquemaVersion1_seCreaConLasCuatroTablas() throws IOException {
        MigrationTestHelper helper = helper();

        SQLiteConnection base = helper.createDatabase(1);

        assertNotNull(base);
        assertTrue(tablaExiste(base, "platillos"));
        assertTrue(tablaExiste(base, "categorias"));
        assertTrue(tablaExiste(base, "operaciones_pendientes"));
        assertTrue(tablaExiste(base, "sincronizacion"));
        base.close();
    }

    @Test
    public void esquemaVersion1_coincideConLasEntidadesActuales() throws IOException {
        MigrationTestHelper helper = helper();
        SQLiteConnection base = helper.createDatabase(1);
        base.close();

        // Sin migraciones (v1 es la primera): runMigrationsAndValidate reabre la base
        // creada desde el JSON y valida su esquema contra las entidades compiladas.
        // Si el JSON quedó desactualizado, esto lanza IllegalStateException.
        List<Migration> sinMigraciones = Collections.emptyList();
        SQLiteConnection validada = helper.runMigrationsAndValidate(1, sinMigraciones);

        assertNotNull(validada);
        validada.close();
    }

    /**
     * El driver-based helper no borra el archivo previo (a diferencia del helper legacy);
     * el test debe garantizar una base limpia antes de {@code createDatabase}.
     */
    private static MigrationTestHelper helper() {
        Context contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File archivoBase = contexto.getDatabasePath(NOMBRE_BASE);
        if (archivoBase.exists()) {
            assertTrue("No se pudo limpiar la base previa del test", archivoBase.delete());
        }
        File directorio = archivoBase.getParentFile();
        if (directorio != null && !directorio.exists()) {
            assertTrue("No se pudo crear el directorio de la base", directorio.mkdirs());
        }
        // Sin @JvmOverloads en este constructor, Java debe pasar los 6 argumentos:
        // la databaseFactory por defecto instancia el AppDatabase_Impl generado, y aquí
        // no hay AutoMigrationSpecs. autoMigrationSpecs = List vacío.
        return new MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                archivoBase,
                new AndroidSQLiteDriver(),
                JvmClassMappingKt.getKotlinClass(AppDatabase.class),
                AppDatabase_Impl::new,
                Collections.emptyList());
    }

    private static boolean tablaExiste(SQLiteConnection base, String tabla) {
        try (SQLiteStatement sentencia = base.prepare(
                "SELECT name FROM sqlite_master WHERE type='table' AND name = ?")) {
            sentencia.bindText(1, tabla);
            return sentencia.step();
        }
    }
}

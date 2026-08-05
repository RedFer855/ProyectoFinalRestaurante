package com.example.proyectofinalrestaurante.data.local;

import static org.junit.Assert.assertEquals;
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
    public void migracion1a2_validaContraLasEntidadesActuales() throws IOException {
        MigrationTestHelper helper = helper();
        SQLiteConnection base = helper.createDatabase(1);
        base.close();

        // runMigrationsAndValidate corre la migración y después compara el esquema
        // resultante contra el JSON de la v2. Si la migración escrita a mano no deja la
        // base exactamente como Room espera —una columna de menos, un índice con otro
        // nombre— esto lanza IllegalStateException. Es lo que hace que
        // fallbackToDestructiveMigration() no haga falta nunca.
        List<Migration> migraciones = Collections.singletonList(Migraciones.DE_1_A_2);
        SQLiteConnection validada = helper.runMigrationsAndValidate(2, migraciones);

        assertNotNull(validada);
        assertTrue(tablaExiste(validada, "empleados"));
        validada.close();
    }

    @Test
    public void migracion1a2_conservaLasOperacionesPendientesYLasMarcaDelMenu() throws IOException {
        MigrationTestHelper helper = helper();
        SQLiteConnection base = helper.createDatabase(1);
        // Una operación encolada por la v1, cuando el Menú era el único que encolaba.
        try (SQLiteStatement insercion = base.prepare("INSERT INTO operaciones_pendientes "
                + "(tipo, id_local, payload_json, intentos, creado_en) "
                + "VALUES ('CREAR_PLATILLO', 4, '{}', 0, 123)")) {
            insercion.step();
        }
        base.close();

        SQLiteConnection migrada = helper.runMigrationsAndValidate(
                2, Collections.singletonList(Migraciones.DE_1_A_2));

        // Lo que el usuario todavía no subió no se puede perder en una migración, y su
        // módulo tiene que quedar bien: en la v1 solo el Menú encolaba.
        try (SQLiteStatement sentencia = migrada.prepare(
                "SELECT modulo, tipo FROM operaciones_pendientes WHERE id_local = 4")) {
            assertTrue("la operación pendiente se perdió en la migración", sentencia.step());
            assertEquals("MENU", sentencia.getText(0));
            assertEquals("CREAR_PLATILLO", sentencia.getText(1));
        }
        migrada.close();
    }

    @Test
    public void migracion2a3_creaLasTablasDeMesasYConservaLoExistente() throws IOException {
        MigrationTestHelper helper = helper();
        SQLiteConnection base = helper.createDatabase(2);
        // Un platillo de la v2 que debe sobrevivir a la migración.
        try (SQLiteStatement insercion = base.prepare("INSERT INTO platillos "
                + "(id_local, id_servidor, nombre, descripcion, precio, id_categoria_local, "
                + "nombre_categoria, ruta_imagen, activo, estado_sync, actualizado_en) "
                + "VALUES (1, 10, 'Baleada', NULL, 45.0, 1, 'Platos', NULL, 1, "
                + "'SINCRONIZADO', NULL)")) {
            insercion.step();
        }
        base.close();

        // runMigrationsAndValidate compara el esquema resultante contra el JSON de la v3:
        // si la migración escrita a mano no deja la base exactamente como Room espera,
        // esto lanza IllegalStateException.
        SQLiteConnection migrada = helper.runMigrationsAndValidate(
                3, Collections.singletonList(Migraciones.DE_2_A_3));

        assertNotNull(migrada);
        assertTrue(tablaExiste(migrada, "mesas"));
        assertTrue(tablaExiste(migrada, "estados_mesa"));
        assertTrue(tablaExiste(migrada, "platillos"));
        // El platillo encolado/descargado no se pierde al sumar el módulo Mesas.
        try (SQLiteStatement sentencia = migrada.prepare(
                "SELECT nombre FROM platillos WHERE id_local = 1")) {
            assertTrue("el platillo se perdió en la migración", sentencia.step());
            assertEquals("Baleada", sentencia.getText(0));
        }
        migrada.close();
    }

    @Test
    public void migracion3a4_creaLaTablaDeClientesYConservaLoExistente() throws IOException {
        MigrationTestHelper helper = helper();
        SQLiteConnection base = helper.createDatabase(3);
        // Una mesa de la v3 que debe sobrevivir a la migración.
        try (SQLiteStatement insercion = base.prepare("INSERT INTO mesas "
                + "(id_local, id_servidor, numero_mesa, capacidad, ubicacion, id_estado_mesa, "
                + "estado_mesa, id_estado, activo, actualizado_en, estado_sync) "
                + "VALUES (1, 10, 4, 6, 'Patio', 1, 'Libre', 1, 1, NULL, 'SINCRONIZADO')")) {
            insercion.step();
        }
        base.close();

        SQLiteConnection migrada = helper.runMigrationsAndValidate(
                4, Collections.singletonList(Migraciones.DE_3_A_4));

        assertNotNull(migrada);
        assertTrue(tablaExiste(migrada, "clientes"));
        assertTrue(tablaExiste(migrada, "mesas"));
        // La mesa encolada/descargada no se pierde al sumar el módulo Clientes.
        try (SQLiteStatement sentencia = migrada.prepare(
                "SELECT numero_mesa FROM mesas WHERE id_local = 1")) {
            assertTrue("la mesa se perdió en la migración", sentencia.step());
            assertEquals(4L, sentencia.getLong(0));
        }
        migrada.close();
    }

    @Test
    public void migracion4a5_creaLasTablasDePedidosYConservaLoExistente() throws IOException {
        MigrationTestHelper helper = helper();
        SQLiteConnection base = helper.createDatabase(4);
        // Un cliente de la v4 que debe sobrevivir a la migración.
        try (SQLiteStatement insercion = base.prepare("INSERT INTO clientes "
                + "(id_local, id_servidor, nombre, apellido, identidad, telefono, id_estado, "
                + "activo, cantidad_pedidos, actualizado_en, estado_sync) "
                + "VALUES (1, 10, 'Ana', 'Cruz', '0801', '9900', 1, 1, 0, NULL, "
                + "'SINCRONIZADO')")) {
            insercion.step();
        }
        base.close();

        SQLiteConnection migrada = helper.runMigrationsAndValidate(
                5, Collections.singletonList(Migraciones.DE_4_A_5));

        assertNotNull(migrada);
        assertTrue(tablaExiste(migrada, "pedidos"));
        assertTrue(tablaExiste(migrada, "estados_pedido"));
        assertTrue(tablaExiste(migrada, "notificaciones"));
        assertTrue(tablaExiste(migrada, "clientes"));
        // El cliente descargado no se pierde al sumar el módulo Pedidos.
        try (SQLiteStatement sentencia = migrada.prepare(
                "SELECT nombre FROM clientes WHERE id_local = 1")) {
            assertTrue("el cliente se perdió en la migración", sentencia.step());
            assertEquals("Ana", sentencia.getText(0));
        }
        migrada.close();
    }

    @Test
    public void migracion5a6_creaDetalleYColumnaIdempotenciaYConservaLoExistente()
            throws IOException {
        MigrationTestHelper helper = helper();
        SQLiteConnection base = helper.createDatabase(5);
        // Un pedido de la v5 que debe sobrevivir con todos sus campos.
        try (SQLiteStatement insercion = base.prepare("INSERT INTO pedidos "
                + "(id_local, id_servidor, fecha, id_estado_pedido, numero_mesa, cliente, total, "
                + "cantidad_items, id_auth_usuario, actualizado_en, estado_sync) "
                + "VALUES (1, 10, '2026-08-04T12:05:00-06:00', 1, 4, 'Ana Cruz', 90.0, 2, "
                + "'uuid-mesero', NULL, 'SINCRONIZADO')")) {
            insercion.step();
        }
        base.close();

        SQLiteConnection migrada = helper.runMigrationsAndValidate(
                6, Collections.<Migration>singletonList(Migraciones.DE_5_A_6));

        assertNotNull(migrada);
        assertTrue(tablaExiste(migrada, "detalle_pedido"));
        assertTrue(tablaExiste(migrada, "pedidos"));
        // La columna clave_idempotencia quedó en pedidos (nullable).
        try (SQLiteStatement sentencia = migrada.prepare(
                "SELECT clave_idempotencia FROM pedidos WHERE id_local = 1")) {
            assertTrue("el pedido se perdió en la migración", sentencia.step());
        }
        // El pedido descargado no se pierde.
        try (SQLiteStatement sentencia = migrada.prepare(
                "SELECT total FROM pedidos WHERE id_local = 1")) {
            assertTrue("el pedido se perdió en la migración", sentencia.step());
            assertEquals(90.0, sentencia.getDouble(0), 0.0001);
        }
        migrada.close();
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

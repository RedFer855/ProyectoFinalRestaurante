package com.example.proyectofinalrestaurante.data.local;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Migraciones del esquema local. Cada una se escribe a mano y lleva su test con
 * {@code MigrationTestHelper}: {@code fallbackToDestructiveMigration()} está prohibido
 * porque borra los datos del usuario sin avisar (Lista Negra de APIs Android).
 */
public final class Migraciones {

    private Migraciones() {
    }

    /**
     * v1 → v2: llega el módulo Empleados a la base local.
     *
     * <p>Dos cambios, y el segundo importa más de lo que parece:</p>
     *
     * <ol>
     *   <li>Tabla {@code empleados}. Su PK es {@code id_empleado} (el id del servidor) y no
     *       un id local, porque el alta de un empleado exige conexión — ver
     *       {@code EmpleadoEntity}.</li>
     *   <li>Columna {@code modulo} en {@code operaciones_pendientes}. La cola pasa a estar
     *       compartida entre módulos, y sin particionarla el sincronizador del Menú
     *       descartaría las operaciones de Empleados como "tipo desconocido". A las filas
     *       que ya estén en la cola al migrar se les pone {@code 'MENU'}: en la v1 el Menú
     *       era el único que encolaba, así que es su valor correcto, no un relleno.</li>
     * </ol>
     *
     * <p>La columna se crea {@code NOT NULL DEFAULT 'MENU'} en la misma sentencia: SQLite
     * exige un default al agregar una columna {@code NOT NULL} a una tabla con filas.</p>
     */
    public static final Migration DE_1_A_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase base) {
            base.execSQL("CREATE TABLE IF NOT EXISTS `empleados` ("
                    + "`id_empleado` INTEGER NOT NULL, "
                    + "`nombres` TEXT, "
                    + "`apellidos` TEXT, "
                    + "`identidad` TEXT, "
                    + "`telefono` TEXT, "
                    + "`correo` TEXT, "
                    + "`id_usuario` INTEGER NOT NULL, "
                    + "`apodo_usuario` TEXT, "
                    + "`id_auth_user` TEXT, "
                    + "`rol` TEXT, "
                    + "`activo` INTEGER NOT NULL, "
                    + "`estado_sync` TEXT, "
                    + "`actualizado_en` TEXT, "
                    + "PRIMARY KEY(`id_empleado`))");

            base.execSQL("ALTER TABLE `operaciones_pendientes` "
                    + "ADD COLUMN `modulo` TEXT NOT NULL DEFAULT 'MENU'");

            base.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_operaciones_pendientes_modulo_id` "
                    + "ON `operaciones_pendientes` (`modulo`, `id`)");
        }
    };

    /**
     * v2 → v3: llega el módulo Mesas a la base local.
     *
     * <p>Tabla {@code mesas} con PK local autoincremental e índice único sobre
     * {@code id_servidor} para evitar duplicados al bajar el delta.</p>
     */
    public static final Migration DE_2_A_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase base) {
            base.execSQL("CREATE TABLE IF NOT EXISTS `mesas` ("
                    + "`id_local` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`id_servidor` INTEGER, "
                    + "`numero_mesa` INTEGER NOT NULL, "
                    + "`capacidad` INTEGER NOT NULL, "
                    + "`ubicacion` TEXT, "
                    + "`estado_mesa` TEXT NOT NULL, "
                    + "`activo` INTEGER NOT NULL, "
                    + "`estado_sync` TEXT, "
                    + "`actualizado_en` TEXT)");

            base.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_mesas_id_servidor` "
                    + "ON `mesas` (`id_servidor`)");
        }
    };

    /**
     * v3 → v4: llega el módulo Clientes a la base local.
     *
     * <p>Tabla {@code clientes} con PK local autoincremental e índice único sobre
     * {@code id_servidor}. Incluye {@code cantidad_pedidos} cacheado para que
     * {@code ReglasCliente.puedeBorrarse} no necesite un viaje de red.</p>
     */
    public static final Migration DE_3_A_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase base) {
            base.execSQL("CREATE TABLE IF NOT EXISTS `clientes` ("
                    + "`id_local` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`id_servidor` INTEGER, "
                    + "`nombre` TEXT NOT NULL, "
                    + "`apellido` TEXT NOT NULL, "
                    + "`identidad` TEXT, "
                    + "`telefono` TEXT, "
                    + "`activo` INTEGER NOT NULL, "
                    + "`cantidad_pedidos` INTEGER NOT NULL, "
                    + "`estado_sync` TEXT, "
                    + "`actualizado_en` TEXT)");

            base.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_clientes_id_servidor` "
                    + "ON `clientes` (`id_servidor`)");
        }
    };
}

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
     * <p>Dos tablas nuevas, ninguna se toca la que ya existe:</p>
     *
     * <ol>
     *   <li>{@code mesas}, con el mismo esquema de identidad que {@code platillos}
     *       ({@code id_local} PK, {@code id_servidor} con índice único) más las marcas de
     *       sincronización. {@code id_estado_mesa} es el estado operativo (catálogo
     *       {@code estado_mesa}) y {@code id_estado}/{@code activo} la existencia en el
     *       salón (baja lógica, {@code 1 = activo}).</li>
     *   <li>{@code estados_mesa}, el catálogo de estados operativos cacheado para que la UI
     *       traduzca {@code id_estado_mesa} en etiquetas sin pedir la descripción al
     *       servidor. Se reemplaza completo en cada sincronización.</li>
     * </ol>
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
                    + "`id_estado_mesa` INTEGER NOT NULL, "
                    + "`estado_mesa` TEXT, "
                    + "`id_estado` INTEGER NOT NULL, "
                    + "`activo` INTEGER NOT NULL, "
                    + "`actualizado_en` TEXT, "
                    + "`estado_sync` TEXT NOT NULL)");

            base.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_mesas_id_servidor` ON `mesas` (`id_servidor`)");

            base.execSQL("CREATE TABLE IF NOT EXISTS `estados_mesa` ("
                    + "`id_estado_mesa` INTEGER NOT NULL, "
                    + "`descripcion` TEXT NOT NULL, "
                    + "`orden` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id_estado_mesa`))");
        }
    };

    /**
     * v3 → v4: llega el módulo Clientes a la base local. Mismo esquema de identidad que
     * {@code mesas}: {@code id_local} PK, {@code id_servidor} con índice único,
     * {@code id_estado}/{@code activo} la baja lógica y {@code cantidad_pedidos} la caché de
     * {@code vista_clientes} que decide si {@code ReglasCliente.puedeBorrarse}.
     */
    public static final Migration DE_3_A_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase base) {
            base.execSQL("CREATE TABLE IF NOT EXISTS `clientes` ("
                    + "`id_local` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`id_servidor` INTEGER, "
                    + "`nombre` TEXT, "
                    + "`apellido` TEXT, "
                    + "`identidad` TEXT, "
                    + "`telefono` TEXT, "
                    + "`id_estado` INTEGER NOT NULL, "
                    + "`activo` INTEGER NOT NULL, "
                    + "`cantidad_pedidos` INTEGER NOT NULL, "
                    + "`actualizado_en` TEXT, "
                    + "`estado_sync` TEXT NOT NULL)");

            base.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_clientes_id_servidor` ON `clientes` (`id_servidor`)");
        }
    };

    /**
     * v4 → v5: llega el módulo Pedidos a la base local (Plan Fase 3, §4.4–4.6).
     *
     * <p>Tres tablas nuevas, ninguna toca las que ya existen:</p>
     *
     * <ol>
     *   <li>{@code pedidos}, con el mismo esquema de identidad que {@code mesas}
     *       ({@code id_local} PK autoincrement, {@code id_servidor} con índice único) más las
     *       marcas de sincronización. El índice {@code (fecha, id_local)} respalda la ventana
     *       FIFO del tablero ({@code ORDER BY fecha ASC, id_local ASC LIMIT}).</li>
     *   <li>{@code estados_pedido}, el catálogo de estados bajado del servidor — a diferencia
     *       de {@code estados_mesa} (que se sembró local) son 5 valores con {@code orden} que
     *       el admin podría querer tocar, así que se reemplaza completo en cada sincronización.</li>
     *   <li>{@code notificaciones}, el buzón local con su índice único de idempotencia sobre
     *       {@code clave_unica}.</li>
     * </ol>
     */
    public static final Migration DE_4_A_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase base) {
            base.execSQL("CREATE TABLE IF NOT EXISTS `pedidos` ("
                    + "`id_local` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`id_servidor` INTEGER, "
                    + "`fecha` TEXT NOT NULL, "
                    + "`id_estado_pedido` INTEGER NOT NULL, "
                    + "`numero_mesa` INTEGER, "
                    + "`cliente` TEXT, "
                    + "`total` REAL NOT NULL, "
                    + "`cantidad_items` INTEGER NOT NULL, "
                    + "`id_auth_usuario` TEXT, "
                    + "`actualizado_en` TEXT, "
                    + "`estado_sync` TEXT NOT NULL)");

            base.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_pedidos_fecha_id_local` ON `pedidos` (`fecha`, `id_local`)");

            base.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_pedidos_id_servidor` ON `pedidos` (`id_servidor`)");

            base.execSQL("CREATE TABLE IF NOT EXISTS `estados_pedido` ("
                    + "`id_estado_pedido` INTEGER NOT NULL, "
                    + "`descripcion` TEXT NOT NULL, "
                    + "`orden` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id_estado_pedido`))");

            base.execSQL("CREATE TABLE IF NOT EXISTS `notificaciones` ("
                    + "`id_local` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`tipo` TEXT NOT NULL, "
                    + "`rol_destino` TEXT, "
                    + "`destinatario_auth` TEXT, "
                    + "`arg1` TEXT, "
                    + "`creado_en` INTEGER NOT NULL, "
                    + "`leida` INTEGER NOT NULL, "
                    + "`clave_unica` TEXT NOT NULL)");

            base.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_notificaciones_clave_unica` ON `notificaciones` (`clave_unica`)");
        }
    };

    /**
     * v5 → v6: llega la <b>toma</b> de pedidos (Plan Fase 3b, §6.2). Dos cambios:
     *
     * <ol>
     *   <li>{@code pedidos} gana {@code clave_idempotencia} — la columna que hace idempotente
     *       al RPC {@code crear_pedido}: el outbox manda la misma clave en cada reintento y el
     *       servidor devuelve el mismo {@code id_pedido} sin duplicar (§5.1). Es {@code null}
     *       en las filas del delta, que el servidor ya tiene.</li>
     *   <li>Tabla {@code detalle_pedido}: las N líneas de cada pedido, escritas en la misma
     *       transacción que la cabecera. {@code id_pedido} apunta al {@code id_local} de la
     *       cabecera, {@code id_platillo_local} al id de Room del platillo, y
     *       {@code id_servidor} (el {@code id_detalle_pedido} del servidor) es {@code null}
     *       mientras el {@code CREAR_PEDIDO} no drene.</li>
     * </ol>
     */
    public static final Migration DE_5_A_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase base) {
            base.execSQL("ALTER TABLE `pedidos` "
                    + "ADD COLUMN `clave_idempotencia` TEXT");

            base.execSQL("CREATE TABLE IF NOT EXISTS `detalle_pedido` ("
                    + "`id_local` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`id_pedido` INTEGER NOT NULL, "
                    + "`id_platillo_local` INTEGER NOT NULL, "
                    + "`cantidad` INTEGER NOT NULL, "
                    + "`precio` REAL NOT NULL, "
                    + "`id_servidor` INTEGER)");

            base.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_detalle_pedido_id_pedido` ON `detalle_pedido` (`id_pedido`)");

            base.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_detalle_pedido_id_pedido_id_platillo_local` "
                    + "ON `detalle_pedido` (`id_pedido`, `id_platillo_local`)");
        }
    };
}

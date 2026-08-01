package com.example.proyectofinalrestaurante.data.outbox;

/**
 * Tipos de operación del outbox (Plan Fase 2b, §4.4). Valores en texto porque viajan a
 * SQLite; cada {@code switch} del sincronizador es exhaustivo sobre estos.
 *
 * <p>Desde la migración de Empleados a offline-first la cola es <b>compartida</b> entre
 * módulos, así que cada operación además lleva su {@link Modulo}. No es decorativo: cada
 * sincronizador drena <b>solo</b> las operaciones de su módulo, y el {@code default} de su
 * {@code switch} descarta lo que no reconoce. Sin particionar, el sincronizador del Menú
 * borraría en silencio las operaciones de Empleados apenas las viera.</p>
 */
public final class TipoOperacion {

    private TipoOperacion() {
    }

    /**
     * Módulo dueño de una operación. Es también la partición del outbox: un sincronizador
     * nunca ve las operaciones de otro módulo.
     */
    public static final class Modulo {

        private Modulo() {
        }

        public static final String MENU = "MENU";
        public static final String EMPLEADOS = "EMPLEADOS";
    }

    public static final String CREAR_PLATILLO = "CREAR_PLATILLO";
    public static final String ACTUALIZAR_PLATILLO = "ACTUALIZAR_PLATILLO";
    public static final String CAMBIAR_ESTADO_PLATILLO = "CAMBIAR_ESTADO_PLATILLO";
    public static final String QUITAR_IMAGEN_PLATILLO = "QUITAR_IMAGEN_PLATILLO";

    public static final String CREAR_CATEGORIA = "CREAR_CATEGORIA";
    public static final String RENOMBRAR_CATEGORIA = "RENOMBRAR_CATEGORIA";
    public static final String CAMBIAR_ESTADO_CATEGORIA = "CAMBIAR_ESTADO_CATEGORIA";
    public static final String BORRAR_CATEGORIA = "BORRAR_CATEGORIA";

    /**
     * Empleados. No existe {@code CREAR_EMPLEADO} a propósito: el alta crea una cuenta en
     * Supabase Auth con una contraseña temporal, y encolarla obligaría a guardar esa
     * contraseña en el dispositivo (contra <b>P-009</b>) y a reintentar un {@code POST} no
     * idempotente que crea cuentas. El alta exige conexión; ver {@code Módulo Empleados}.
     */
    public static final String ACTUALIZAR_EMPLEADO = "ACTUALIZAR_EMPLEADO";
    public static final String CAMBIAR_ROL_EMPLEADO = "CAMBIAR_ROL_EMPLEADO";
    public static final String CAMBIAR_ESTADO_EMPLEADO = "CAMBIAR_ESTADO_EMPLEADO";
}

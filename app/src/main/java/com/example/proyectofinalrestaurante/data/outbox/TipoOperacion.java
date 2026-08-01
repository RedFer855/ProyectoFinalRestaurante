package com.example.proyectofinalrestaurante.data.outbox;

/**
 * Tipos de operación del outbox (Plan Fase 2b, §4.4). Valores en texto porque viajan a
 * SQLite; cada {@code switch} del sincronizador es exhaustivo sobre estos.
 */
public final class TipoOperacion {

    private TipoOperacion() {
    }

    public static final String CREAR_PLATILLO = "CREAR_PLATILLO";
    public static final String ACTUALIZAR_PLATILLO = "ACTUALIZAR_PLATILLO";
    public static final String CAMBIAR_ESTADO_PLATILLO = "CAMBIAR_ESTADO_PLATILLO";
    public static final String QUITAR_IMAGEN_PLATILLO = "QUITAR_IMAGEN_PLATILLO";

    public static final String CREAR_CATEGORIA = "CREAR_CATEGORIA";
    public static final String RENOMBRAR_CATEGORIA = "RENOMBRAR_CATEGORIA";
    public static final String CAMBIAR_ESTADO_CATEGORIA = "CAMBIAR_ESTADO_CATEGORIA";
    public static final String BORRAR_CATEGORIA = "BORRAR_CATEGORIA";
}

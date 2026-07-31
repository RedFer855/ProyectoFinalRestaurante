package com.example.proyectofinalrestaurante.domain;

/**
 * Acciones que un rol puede tener sobre un {@link Modulo} (Plan Fase 1c, Entregable 1).
 *
 * <p>{@link #VER} es especial: sin él, el módulo entero desaparece del menú de navegación.
 * Las demás controlan la visibilidad de botones y opciones dentro de la pantalla.</p>
 */
public enum Accion {
    /** Entrar al módulo y leer su contenido. Sin esto el módulo no aparece en el menú. */
    VER,
    /** Botón de agregar (FAB) y su formulario. */
    CREAR,
    /** Opción "editar" de cada elemento. */
    EDITAR,
    /** Opción "eliminar" o "cancelar" de cada elemento. */
    ELIMINAR,
    /** Avanzar el estado de un pedido o cambiar el de una mesa. */
    CAMBIAR_ESTADO
}

package com.example.proyectofinalrestaurante.domain;

/**
 * Módulos funcionales de la app (Plan Fase 1c, Entregable 1).
 *
 * <p>Cada módulo es una pantalla única compartida por los tres roles: lo que cambia
 * según el rol son las {@link Accion} disponibles dentro de ella, no la pantalla.
 * Ver {@link Permisos}.</p>
 */
public enum Modulo {
    INICIO,
    MENU,
    PEDIDOS,
    MESAS,
    CLIENTES,
    EMPLEADOS,
    REPORTES
}

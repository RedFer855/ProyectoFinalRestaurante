package com.example.proyectofinalrestaurante.domain.model;

/**
 * Una fila del top-5 de platillos más pedidos, dentro de un {@link ReporteVentas} (Plan Fase
 * 3c, §4.1). Inmutable.
 */
public final class ConteoPlatillo {

    private final String nombre;
    private final int cantidad;

    public ConteoPlatillo(String nombre, int cantidad) {
        this.nombre = nombre;
        this.cantidad = cantidad;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCantidad() {
        return cantidad;
    }
}

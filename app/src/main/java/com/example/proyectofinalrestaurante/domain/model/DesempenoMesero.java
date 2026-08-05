package com.example.proyectofinalrestaurante.domain.model;

/**
 * El desempeño de un mesero dentro de un {@link ReporteVentas} (Plan Fase 3c, §4.1):
 * cuántos pedidos atendió y cuánto vendió en el rango. Inmutable.
 */
public final class DesempenoMesero {

    private final String nombre;
    private final int cantidadPedidos;
    private final double totalVendido;

    public DesempenoMesero(String nombre, int cantidadPedidos, double totalVendido) {
        this.nombre = nombre;
        this.cantidadPedidos = cantidadPedidos;
        this.totalVendido = totalVendido;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCantidadPedidos() {
        return cantidadPedidos;
    }

    public double getTotalVendido() {
        return totalVendido;
    }
}

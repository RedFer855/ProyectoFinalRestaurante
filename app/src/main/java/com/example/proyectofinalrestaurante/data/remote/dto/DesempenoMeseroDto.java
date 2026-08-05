package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Una fila de {@code desempeno_meseros} dentro de {@link ReporteVentasDto} (Plan Fase 3c, §4.1). */
public final class DesempenoMeseroDto {

    @SerializedName("nombre")
    private String nombre;

    @SerializedName("cantidad_pedidos")
    private int cantidadPedidos;

    @SerializedName("total_vendido")
    private double totalVendido;

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

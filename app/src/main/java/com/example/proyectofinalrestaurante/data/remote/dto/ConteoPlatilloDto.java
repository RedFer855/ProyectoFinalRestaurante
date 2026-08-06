package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Una fila de {@code top_platillos} dentro de {@link ReporteVentasDto} (Plan Fase 3c, §4.1). */
public final class ConteoPlatilloDto {

    @SerializedName("nombre")
    private String nombre;

    @SerializedName("cantidad")
    private int cantidad;

    public String getNombre() {
        return nombre;
    }

    public int getCantidad() {
        return cantidad;
    }
}

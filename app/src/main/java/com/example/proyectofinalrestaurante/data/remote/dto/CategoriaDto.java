package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Fila de {@code public.vista_categorias}.
 *
 * <p>Los contadores solo existen en la vista. En la respuesta de un {@code INSERT} sobre
 * la tabla {@code categoria} llegan en 0, que además es el valor correcto: una categoría
 * recién creada no tiene platillos.</p>
 */
public final class CategoriaDto {

    @SerializedName("id_categoria")
    private int idCategoria;

    @SerializedName("descripcion")
    private String descripcion;

    @SerializedName("id_estado")
    private int idEstado;

    @SerializedName("cantidad_platillos")
    private int cantidadPlatillos;

    @SerializedName("cantidad_platillos_activos")
    private int cantidadPlatillosActivos;

    /** Marca de la fila en el servidor; es la que permite el sync delta (Fase 2b, §4.3). */
    @SerializedName("actualizado_en")
    private String actualizadoEn;

    public int getIdCategoria() {
        return idCategoria;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getIdEstado() {
        return idEstado;
    }

    public int getCantidadPlatillos() {
        return cantidadPlatillos;
    }

    public int getCantidadPlatillosActivos() {
        return cantidadPlatillosActivos;
    }

    public String getActualizadoEn() {
        return actualizadoEn;
    }
}

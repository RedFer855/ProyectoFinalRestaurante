package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Fila de {@code public.vista_platillos}. En la base todo es {@code snake_case}; el mapeo
 * a Java es explícito con {@code @SerializedName}.
 *
 * <p>Este mismo DTO se reusa para leer lo que devuelve el {@code INSERT} sobre la tabla
 * {@code platillo} con {@code Prefer: return=representation}. Ojo con la diferencia: la
 * tabla <b>no</b> tiene las columnas {@code nombre_categoria} ni {@code activo}, que son
 * de la vista, así que en ese caso llegan nulas/false. Por eso el estado se deriva siempre
 * de {@code id_estado}, que sí existe en las dos.</p>
 */
public final class PlatilloDto {

    @SerializedName("id_platillo")
    private int idPlatillo;

    @SerializedName("nombre")
    private String nombre;

    @SerializedName("descripcion")
    private String descripcion;

    @SerializedName("precio")
    private double precio;

    @SerializedName("id_categoria")
    private int idCategoria;

    /** Solo viene de la vista; es null en la respuesta de un INSERT sobre la tabla. */
    @SerializedName("nombre_categoria")
    private String nombreCategoria;

    /** Ruta dentro del bucket (`a3f9….jpg`), nunca la URL completa. */
    @SerializedName("ruta_imagen")
    private String rutaImagen;

    @SerializedName("id_estado")
    private int idEstado;

    public int getIdPlatillo() {
        return idPlatillo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public double getPrecio() {
        return precio;
    }

    public int getIdCategoria() {
        return idCategoria;
    }

    public String getNombreCategoria() {
        return nombreCategoria;
    }

    public String getRutaImagen() {
        return rutaImagen;
    }

    public int getIdEstado() {
        return idEstado;
    }
}

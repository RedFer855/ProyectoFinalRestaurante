package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code POST rest/v1/platillo}.
 *
 * <p>No lleva {@code id_estado} ni {@code actualizado_en}: el primero tiene default 1
 * (Activo) y el segundo lo pone el trigger {@code trg_platillo_actualizado_en}. Mandarlos
 * desde la app sería duplicar una decisión que ya vive en el servidor.</p>
 *
 * <p>{@code ruta_imagen} nula se omite (Gson no serializa nulos), que es exactamente lo
 * que se quiere para un platillo sin foto.</p>
 */
public final class CrearPlatilloDto {

    @SerializedName("nombre")
    private final String nombre;

    @SerializedName("descripcion")
    private final String descripcion;

    @SerializedName("precio")
    private final double precio;

    @SerializedName("id_categoria")
    private final int idCategoria;

    @SerializedName("ruta_imagen")
    private final String rutaImagen;

    public CrearPlatilloDto(String nombre, String descripcion, double precio,
                            int idCategoria, String rutaImagen) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.idCategoria = idCategoria;
        this.rutaImagen = rutaImagen;
    }
}

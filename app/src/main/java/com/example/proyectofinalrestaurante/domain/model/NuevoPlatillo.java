package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Datos que se mandan al crear un platillo (Plan Fase 2a). Sin id: lo genera la base.
 *
 * <p>Tampoco lleva estado ni fecha de actualización: {@code id_estado} tiene default 1
 * (Activo) y {@code actualizado_en} lo pone un trigger. Mandarlos desde la app sería
 * duplicar una decisión que ya vive en el servidor.</p>
 *
 * <p>La imagen no va acá: viaja aparte porque se sube a Storage en otra petición, y el
 * dominio no puede transportar tipos de Android. Ver {@link ImagenPlatillo}.</p>
 */
public final class NuevoPlatillo {

    private final String nombre;
    @Nullable private final String descripcion;
    private final double precio;
    private final int idCategoria;

    public NuevoPlatillo(String nombre, @Nullable String descripcion, double precio,
                         int idCategoria) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.idCategoria = idCategoria;
    }

    public String getNombre() {
        return nombre;
    }

    @Nullable
    public String getDescripcion() {
        return descripcion;
    }

    public double getPrecio() {
        return precio;
    }

    public int getIdCategoria() {
        return idCategoria;
    }
}

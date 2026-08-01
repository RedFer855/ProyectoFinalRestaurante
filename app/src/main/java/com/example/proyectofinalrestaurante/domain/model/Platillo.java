package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Platillo del menú (Plan Fase 2a). Inmutable.
 *
 * <p>Corresponde a una fila de la vista {@code public.vista_platillos}, que ya trae el
 * nombre de la categoría resuelto. La app <b>lee</b> de esa vista y <b>escribe</b> en la
 * tabla {@code platillo}.</p>
 *
 * <p>{@code rutaImagen} es la ruta dentro del bucket ({@code a3f9….jpg}), <b>nunca</b> la
 * URL completa: si mañana cambia el proyecto, el dominio o el nombre del bucket, las URLs
 * guardadas apuntarían a la nada. La URL pública la arma {@code ui} a partir de esta ruta.</p>
 *
 * <p>Un platillo nunca se borra, se desactiva ({@code activo == false}): borrarlo rompería
 * el historial de pedidos, y un trigger del servidor rechaza el {@code DELETE}.</p>
 */
public final class Platillo {

    private final int idPlatillo;
    private final String nombre;
    @Nullable private final String descripcion;
    private final double precio;
    private final int idCategoria;
    /** Viene de la vista; es {@code null} en el platillo recién creado por un INSERT. */
    @Nullable private final String nombreCategoria;
    @Nullable private final String rutaImagen;
    private final boolean activo;

    public Platillo(int idPlatillo, String nombre, @Nullable String descripcion, double precio,
                    int idCategoria, @Nullable String nombreCategoria,
                    @Nullable String rutaImagen, boolean activo) {
        this.idPlatillo = idPlatillo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.idCategoria = idCategoria;
        this.nombreCategoria = nombreCategoria;
        this.rutaImagen = rutaImagen;
        this.activo = activo;
    }

    public int getIdPlatillo() {
        return idPlatillo;
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

    @Nullable
    public String getNombreCategoria() {
        return nombreCategoria;
    }

    @Nullable
    public String getRutaImagen() {
        return rutaImagen;
    }

    public boolean isActivo() {
        return activo;
    }

    public boolean tieneImagen() {
        return rutaImagen != null && !rutaImagen.trim().isEmpty();
    }
}

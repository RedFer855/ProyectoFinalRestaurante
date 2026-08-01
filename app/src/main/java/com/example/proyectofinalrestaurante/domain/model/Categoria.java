package com.example.proyectofinalrestaurante.domain.model;

/**
 * Categoría del menú (Plan Fase 2a). Inmutable.
 *
 * <p>Corresponde a una fila de {@code public.vista_categorias}. Los dos contadores no son
 * adorno: {@code cantidadPlatillos} es lo que permite saber si la categoría se puede
 * borrar <b>sin preguntárselo al servidor</b>, y {@code cantidadPlatillosActivos} es lo
 * que se le muestra al usuario, que no debería contar los desactivados.</p>
 */
public final class Categoria {

    private final int idCategoria;
    private final String descripcion;
    private final boolean activo;
    private final int cantidadPlatillos;
    private final int cantidadPlatillosActivos;

    public Categoria(int idCategoria, String descripcion, boolean activo,
                     int cantidadPlatillos, int cantidadPlatillosActivos) {
        this.idCategoria = idCategoria;
        this.descripcion = descripcion;
        this.activo = activo;
        this.cantidadPlatillos = cantidadPlatillos;
        this.cantidadPlatillosActivos = cantidadPlatillosActivos;
    }

    public int getIdCategoria() {
        return idCategoria;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isActivo() {
        return activo;
    }

    /** Incluye los desactivados: es el número que decide si se puede borrar. */
    public int getCantidadPlatillos() {
        return cantidadPlatillos;
    }

    public int getCantidadPlatillosActivos() {
        return cantidadPlatillosActivos;
    }
}

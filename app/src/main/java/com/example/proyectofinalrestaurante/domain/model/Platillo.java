package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Platillo del menú (Plan Fase 2a; rediseñado en 2b para offline-first). Inmutable.
 *
 * <p>Desde la Fase 2b la identidad local es {@code idLocal} (la PK de Room); el
 * {@code id_platillo} del servidor es {@code idServidor}, que viene {@code null} mientras
 * la fila no se haya subido. Las referencias locales (la categoría de un platillo, los ids
 * que viajan por la UI) usan siempre {@code idLocal}. Sin esa separación, un platillo
 * creado sin red no tendría a qué apuntar. Ver Plan Fase 2b, §5.5.</p>
 *
 * <p>{@code idCategoria} es la categoría <b>local</b> a la que pertenece el platillo.
 * {@code rutaImagen} es la ruta dentro del bucket ({@code a3f9….jpg}), nunca la URL
 * completa; la URL pública la arma {@code ui} a partir de esta ruta.</p>
 *
 * <p>Un platillo nunca se borra, se desactiva ({@code activo == false}): borrarlo rompería
 * el historial de pedidos, y un trigger del servidor rechaza el {@code DELETE}.</p>
 */
public final class Platillo {

    private final int idLocal;
    @Nullable private final Integer idServidor;
    private final String nombre;
    @Nullable private final String descripcion;
    private final double precio;
    private final int idCategoria;
    /** Viene de la vista; es {@code null} en el platillo recién creado por un INSERT. */
    @Nullable private final String nombreCategoria;
    @Nullable private final String rutaImagen;
    private final boolean activo;
    private final EstadoSync estadoSync;

    public Platillo(int idLocal, @Nullable Integer idServidor, String nombre,
                    @Nullable String descripcion, double precio, int idCategoria,
                    @Nullable String nombreCategoria, @Nullable String rutaImagen,
                    boolean activo, EstadoSync estadoSync) {
        this.idLocal = idLocal;
        this.idServidor = idServidor;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.idCategoria = idCategoria;
        this.nombreCategoria = nombreCategoria;
        this.rutaImagen = rutaImagen;
        this.activo = activo;
        this.estadoSync = estadoSync;
    }

    public int getIdLocal() {
        return idLocal;
    }

    @Nullable
    public Integer getIdServidor() {
        return idServidor;
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

    public EstadoSync getEstadoSync() {
        return estadoSync;
    }

    public boolean tieneImagen() {
        return rutaImagen != null && !rutaImagen.trim().isEmpty();
    }
}

package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Categoría del menú (Plan Fase 2a; rediseñada en 2b para offline-first). Inmutable.
 *
 * <p>{@code idLocal} es la PK de Room; {@code idServidor} es el {@code id_categoria} del
 * servidor y viene {@code null} hasta que la categoría se suba. Igual que en
 * {@link Platillo}, todas las referencias locales usan {@code idLocal}.</p>
 *
 * <p>Los dos contadores no son adorno: {@code cantidadPlatillos} es lo que permite saber si
 * la categoría se puede borrar <b>sin preguntárselo al servidor</b>, y
 * {@code cantidadPlatillosActivos} es lo que se le muestra al usuario, que no debería
 * contar los desactivados.</p>
 */
public final class Categoria {

    private final int idLocal;
    @Nullable private final Integer idServidor;
    private final String descripcion;
    private final boolean activo;
    private final int cantidadPlatillos;
    private final int cantidadPlatillosActivos;
    private final EstadoSync estadoSync;

    public Categoria(int idLocal, @Nullable Integer idServidor, String descripcion,
                     boolean activo, int cantidadPlatillos, int cantidadPlatillosActivos,
                     EstadoSync estadoSync) {
        this.idLocal = idLocal;
        this.idServidor = idServidor;
        this.descripcion = descripcion;
        this.activo = activo;
        this.cantidadPlatillos = cantidadPlatillos;
        this.cantidadPlatillosActivos = cantidadPlatillosActivos;
        this.estadoSync = estadoSync;
    }

    public int getIdLocal() {
        return idLocal;
    }

    @Nullable
    public Integer getIdServidor() {
        return idServidor;
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

    public EstadoSync getEstadoSync() {
        return estadoSync;
    }
}

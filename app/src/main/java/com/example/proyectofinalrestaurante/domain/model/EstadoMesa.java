package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Estado operativo de una mesa (Plan Fase 2c, §2.2). Java puro: el {@code id} es el del
 * catálogo {@code estado_mesa} del servidor, y las etiquetas y colores que la UI pinta viven
 * en la capa de presentación (entregable E6), no acá.
 */
public enum EstadoMesa {
    LIBRE(1),
    OCUPADA(2),
    RESERVADA(3);

    private final int id;

    EstadoMesa(int id) {
        this.id = id;
    }

    /** Id del catálogo {@code estado_mesa} del servidor. */
    public int getId() {
        return id;
    }

    /**
     * El estado que corresponde a un id del catálogo del servidor.
     *
     * <p>Devuelve {@code null} si el id no existe en este APK. Es deliberado: el servidor
     * puede tener estados más nuevos que esta versión, y mapearlos a un estado que sí
     * conocemos sería mentirle al mesero (una mesa "en mantenimiento" mostrada como
     * "Libre" se volvería a ocupar por error). {@code null} llega a {@link Mesa#getEstado()}
     * y la UI lo pinta como estado desconocido.</p>
     */
    @Nullable
    public static EstadoMesa porId(int id) {
        for (EstadoMesa estado : values()) {
            if (estado.id == id) {
                return estado;
            }
        }
        return null;
    }
}

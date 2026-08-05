package com.example.proyectofinalrestaurante.ui.buzon;

import com.example.proyectofinalrestaurante.domain.model.Notificacion;

import java.util.Collections;
import java.util.List;

/**
 * Estado único e inmutable del buzón (Plan Fase 3, E9). Transporta la lista visible y el
 * {@code noLeidas} que alimenta el badge de la Toolbar — ambos derivados de Room, nunca de
 * una bandera del fragment.
 */
public final class EstadoBuzon {

    private final List<Notificacion> notificaciones;
    private final int noLeidas;

    EstadoBuzon(List<Notificacion> notificaciones, int noLeidas) {
        this.notificaciones = notificaciones == null
                ? Collections.emptyList() : Collections.unmodifiableList(notificaciones);
        this.noLeidas = noLeidas;
    }

    public List<Notificacion> getNotificaciones() {
        return notificaciones;
    }

    public int getNoLeidas() {
        return noLeidas;
    }

    public boolean isVacio() {
        return notificaciones.isEmpty();
    }
}
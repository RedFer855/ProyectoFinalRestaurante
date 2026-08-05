package com.example.proyectofinalrestaurante.ui.principal;

import java.util.Collections;
import java.util.List;

/**
 * Estado único e inmutable de la pantalla de Inicio (Plan Fase 3c, E9). Mismo patrón que
 * {@code EstadoMesas}/{@code EstadoReportes}.
 */
public final class EstadoInicio {

    private final boolean cargando;
    private final List<TarjetaInicio> tarjetas;

    private EstadoInicio(boolean cargando, List<TarjetaInicio> tarjetas) {
        this.cargando = cargando;
        this.tarjetas = tarjetas == null
                ? Collections.emptyList() : Collections.unmodifiableList(tarjetas);
    }

    public static EstadoInicio cargando() {
        return new EstadoInicio(true, Collections.emptyList());
    }

    public static EstadoInicio conDatos(List<TarjetaInicio> tarjetas) {
        return new EstadoInicio(false, tarjetas);
    }

    public boolean isCargando() {
        return cargando;
    }

    public List<TarjetaInicio> getTarjetas() {
        return tarjetas;
    }
}

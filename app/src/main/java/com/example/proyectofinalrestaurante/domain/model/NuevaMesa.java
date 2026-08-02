package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Datos que se mandan al crear o editar una mesa (Plan Fase 2c, E1). Sin id: lo genera la
 * base. Inmutable.
 *
 * <p>Tampoco lleva estado ni baja: {@code id_estado_mesa} tiene default 1 (Libre) y
 * {@code id_estado} default 1 (Activo). Mandarlos desde la app duplicaría decisiones que ya
 * viven en el servidor.</p>
 */
public final class NuevaMesa {

    private final int numeroMesa;
    private final int capacidad;
    @Nullable private final String ubicacion;

    public NuevaMesa(int numeroMesa, int capacidad, @Nullable String ubicacion) {
        this.numeroMesa = numeroMesa;
        this.capacidad = capacidad;
        this.ubicacion = ubicacion;
    }

    public int getNumeroMesa() {
        return numeroMesa;
    }

    public int getCapacidad() {
        return capacidad;
    }

    @Nullable
    public String getUbicacion() {
        return ubicacion;
    }
}

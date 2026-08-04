package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Datos para crear una mesa (Fase 2c). Sin id: lo genera la base.
 *
 * <p>Tampoco lleva estado ni fecha de actualización: {@code id_estado} tiene default 1
 * (Activo), {@code id_estado_mesa} tiene default 1 (Libre), y {@code actualizado_en} lo
 * pone un trigger. Mandarlos desde la app sería duplicar una decisión que ya vive en el
 * servidor.</p>
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

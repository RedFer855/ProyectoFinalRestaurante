package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Mesa del restaurante (Fase 2c). Inmutable.
 *
 * <p>Identidad local es {@code idLocal} (PK de Room); el {@code id_mesa} del servidor es
 * {@code idServidor}, que viene {@code null} mientras la fila no se haya subido. Las
 * referencias en la UI usan siempre {@code idLocal}.</p>
 *
 * <p>Una mesa nunca se borra, se desactiva ({@code activo == false}): borrarla rompería
 * el historial de pedidos, y un trigger del servidor rechaza el {@code DELETE}.</p>
 */
public final class Mesa {

    private final int idLocal;
    @Nullable private final Integer idServidor;
    private final int numeroMesa;
    private final int capacidad;
    @Nullable private final String ubicacion;
    private final EstadoMesa estadoMesa;
    private final boolean activo;
    private final EstadoSync estadoSync;

    public Mesa(int idLocal, @Nullable Integer idServidor, int numeroMesa, int capacidad,
                @Nullable String ubicacion, EstadoMesa estadoMesa, boolean activo,
                EstadoSync estadoSync) {
        this.idLocal = idLocal;
        this.idServidor = idServidor;
        this.numeroMesa = numeroMesa;
        this.capacidad = capacidad;
        this.ubicacion = ubicacion;
        this.estadoMesa = estadoMesa;
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

    public EstadoMesa getEstadoMesa() {
        return estadoMesa;
    }

    public boolean isActivo() {
        return activo;
    }

    public EstadoSync getEstadoSync() {
        return estadoSync;
    }
}

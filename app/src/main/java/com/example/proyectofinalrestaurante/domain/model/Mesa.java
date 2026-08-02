package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Mesa del salón (Plan Fase 2c). Inmutable.
 *
 * <p>Misma identidad que en la Fase 2b: {@code idLocal} es la PK de Room y
 * {@code idServidor} es el {@code id_mesa} del servidor, {@code null} mientras la fila no se
 * haya subido.</p>
 *
 * <p>La mesa tiene <b>dos estados ortogonales</b> (Plan Fase 2c, §2.1): {@code activo} dice
 * si la mesa existe en el salón (baja lógica sobre {@code id_estado} → {@code estado_general})
 * y {@code estado} dice si está disponible ahora ({@code id_estado_mesa} → catálogo
 * {@code estado_mesa}). Un mesero cambia el segundo; solo un admin toca el primero.</p>
 *
 * <p>{@code estado} es {@code null} únicamente cuando el servidor trae un
 * {@code id_estado_mesa} que este APK todavía no conoce (catálogo más nuevo que el cliente):
 * ver {@link EstadoMesa#porId(int)}. La UI debe mostrarlo como estado desconocido y no
 * ofrecer cambiarlo a ciegas.</p>
 */
public final class Mesa {

    private final int idLocal;
    @Nullable private final Integer idServidor;
    private final int numeroMesa;
    private final int capacidad;
    @Nullable private final String ubicacion;
    @Nullable private final EstadoMesa estado;
    private final boolean activo;
    @Nullable private final String actualizadoEn;
    private final EstadoSync estadoSync;

    public Mesa(int idLocal, @Nullable Integer idServidor, int numeroMesa, int capacidad,
                @Nullable String ubicacion, @Nullable EstadoMesa estado, boolean activo,
                @Nullable String actualizadoEn, EstadoSync estadoSync) {
        this.idLocal = idLocal;
        this.idServidor = idServidor;
        this.numeroMesa = numeroMesa;
        this.capacidad = capacidad;
        this.ubicacion = ubicacion;
        this.estado = estado;
        this.activo = activo;
        this.actualizadoEn = actualizadoEn;
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

    @Nullable
    public EstadoMesa getEstado() {
        return estado;
    }

    public boolean isActivo() {
        return activo;
    }

    @Nullable
    public String getActualizadoEn() {
        return actualizadoEn;
    }

    public EstadoSync getEstadoSync() {
        return estadoSync;
    }

    /**
     * ¿La mesa está dada de alta en el salón? {@code false} es una mesa de baja
     * ({@code id_estado} distinto de 1): no se le puede cambiar el estado operativo — el RPC
     * del servidor solo actualiza filas con {@code id_estado = 1} (Plan Fase 2c, §2.4).
     */
    public boolean esActiva() {
        return activo;
    }

    /**
     * Copia con otro estado operativo. Inmutable, para que {@code DiffUtil} detecte el cambio.
     */
    public Mesa conEstado(EstadoMesa nuevo) {
        return new Mesa(idLocal, idServidor, numeroMesa, capacidad, ubicacion, nuevo, activo,
                actualizadoEn, estadoSync);
    }
}

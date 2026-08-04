package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Fila local de una mesa (Fase 2c). Vive en {@code data.local} a propósito:
 * {@code domain} no puede importar {@code @Entity} de Room.
 *
 * <p>{@code idLocal} es la PK de Room; {@code idServidor} es {@code id_mesa} del servidor
 * y llega {@code null} hasta que el {@code POST} responda. El índice único sobre
 * {@code id_servidor} garantiza que al bajar el delta nunca queden dos filas para la
 * misma mesa del servidor.</p>
 *
 * <p>{@code estadoMesa} se guarda como texto ("LIBRE", "OCUPADA", "RESERVADA") — es la
 * representación de {@code EstadoMesa} en disco.</p>
 */
@Entity(tableName = "mesas",
        indices = {@Index(value = "id_servidor", unique = true)})
public class MesaEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_local")
    private int idLocal;

    @Nullable
    @ColumnInfo(name = "id_servidor")
    private Integer idServidor;

    @ColumnInfo(name = "numero_mesa")
    private int numeroMesa;

    @ColumnInfo(name = "capacidad")
    private int capacidad;

    @Nullable
    @ColumnInfo(name = "ubicacion")
    private String ubicacion;

    @ColumnInfo(name = "estado_mesa")
    private String estadoMesa;

    @ColumnInfo(name = "activo")
    private boolean activo;

    @ColumnInfo(name = "estado_sync")
    private String estadoSync;

    @Nullable
    @ColumnInfo(name = "actualizado_en")
    private String actualizadoEn;

    public int getIdLocal() {
        return idLocal;
    }

    public void setIdLocal(int idLocal) {
        this.idLocal = idLocal;
    }

    @Nullable
    public Integer getIdServidor() {
        return idServidor;
    }

    public void setIdServidor(@Nullable Integer idServidor) {
        this.idServidor = idServidor;
    }

    public int getNumeroMesa() {
        return numeroMesa;
    }

    public void setNumeroMesa(int numeroMesa) {
        this.numeroMesa = numeroMesa;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(int capacidad) {
        this.capacidad = capacidad;
    }

    @Nullable
    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(@Nullable String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public String getEstadoMesa() {
        return estadoMesa;
    }

    public void setEstadoMesa(String estadoMesa) {
        this.estadoMesa = estadoMesa;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public String getEstadoSync() {
        return estadoSync;
    }

    public void setEstadoSync(String estadoSync) {
        this.estadoSync = estadoSync;
    }

    @Nullable
    public String getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(@Nullable String actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}

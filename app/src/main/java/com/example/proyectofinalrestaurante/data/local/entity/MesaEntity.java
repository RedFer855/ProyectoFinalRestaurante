package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Fila local de una mesa (Plan Fase 2c, E3). Mismo esquema de identidad que
 * {@link PlatilloEntity}: {@code idLocal} como PK, {@code idServidor} nullable con índice
 * único, y las marcas de sincronización ({@code estadoSync}, {@code actualizadoEn}).
 *
 * <p>La mesa guarda <b>los dos estados ortogonales</b> de {@code Mesa} (Plan Fase 2c, §2.1):
 * {@code idEstado}/{@code activo} es la existencia en el salón (baja lógica sobre
 * {@code id_estado}) y {@code idEstadoMesa}/{@code estadoMesa} es el estado operativo del
 * catálogo {@code estado_mesa}. {@code estadoMesa} es solo una caché de la descripción que
 * trae el join de {@code vista_mesas}; la fuente de verdad es {@code idEstadoMesa}.</p>
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

    @ColumnInfo(name = "id_estado_mesa")
    private int idEstadoMesa;

    @Nullable
    @ColumnInfo(name = "estado_mesa")
    private String estadoMesa;

    @ColumnInfo(name = "id_estado")
    private int idEstado;

    @ColumnInfo(name = "activo")
    private boolean activo;

    @Nullable
    @ColumnInfo(name = "actualizado_en")
    private String actualizadoEn;

    @NonNull
    @ColumnInfo(name = "estado_sync")
    private String estadoSync;

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

    public int getIdEstadoMesa() {
        return idEstadoMesa;
    }

    public void setIdEstadoMesa(int idEstadoMesa) {
        this.idEstadoMesa = idEstadoMesa;
    }

    @Nullable
    public String getEstadoMesa() {
        return estadoMesa;
    }

    public void setEstadoMesa(@Nullable String estadoMesa) {
        this.estadoMesa = estadoMesa;
    }

    public int getIdEstado() {
        return idEstado;
    }

    public void setIdEstado(int idEstado) {
        this.idEstado = idEstado;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    @Nullable
    public String getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(@Nullable String actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }

    @NonNull
    public String getEstadoSync() {
        return estadoSync;
    }

    public void setEstadoSync(@NonNull String estadoSync) {
        this.estadoSync = estadoSync;
    }
}

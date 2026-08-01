package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Fila local de una categoría (Plan Fase 2b, E2). Mismo esquema de identidad que
 * {@link PlatilloEntity}: {@code idLocal} como PK, {@code idServidor} nullable con índice
 * único, y las marcas de sincronización ({@code estadoSync}, {@code actualizadoEn}).
 */
@Entity(tableName = "categorias",
        indices = {@Index(value = "id_servidor", unique = true)})
public class CategoriaEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_local")
    private int idLocal;

    @Nullable
    @ColumnInfo(name = "id_servidor")
    private Integer idServidor;

    @ColumnInfo(name = "descripcion")
    private String descripcion;

    @ColumnInfo(name = "activo")
    private boolean activo;

    @ColumnInfo(name = "cantidad_platillos")
    private int cantidadPlatillos;

    @ColumnInfo(name = "cantidad_platillos_activos")
    private int cantidadPlatillosActivos;

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

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public int getCantidadPlatillos() {
        return cantidadPlatillos;
    }

    public void setCantidadPlatillos(int cantidadPlatillos) {
        this.cantidadPlatillos = cantidadPlatillos;
    }

    public int getCantidadPlatillosActivos() {
        return cantidadPlatillosActivos;
    }

    public void setCantidadPlatillosActivos(int cantidadPlatillosActivos) {
        this.cantidadPlatillosActivos = cantidadPlatillosActivos;
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

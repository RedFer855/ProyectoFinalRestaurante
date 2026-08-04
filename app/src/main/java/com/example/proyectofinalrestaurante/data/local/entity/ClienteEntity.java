package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity de Room para la tabla {@code clientes} (Fase 2d).
 *
 * <p>El {@code id_local} es la PK local; {@code id_servidor} viene del servidor y se
 * indexa para búsquedas por sync. {@code cantidad_pedidos} se cachea localmente para
 * que {@code ReglasCliente.puedeBorrarse} no necesite un viaje de red.</p>
 */
@Entity(tableName = "clientes",
        indices = @Index(value = "id_servidor", unique = true))
public class ClienteEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_local")
    private long idLocal;

    @ColumnInfo(name = "id_servidor")
    @Nullable
    private Integer idServidor;

    @ColumnInfo(name = "nombre")
    private String nombre;

    @ColumnInfo(name = "apellido")
    private String apellido;

    @ColumnInfo(name = "identidad")
    @Nullable
    private String identidad;

    @ColumnInfo(name = "telefono")
    @Nullable
    private String telefono;

    @ColumnInfo(name = "activo")
    private boolean activo;

    @ColumnInfo(name = "cantidad_pedidos")
    private int cantidadPedidos;

    @ColumnInfo(name = "estado_sync")
    @Nullable
    private String estadoSync;

    @ColumnInfo(name = "actualizado_en")
    @Nullable
    private String actualizadoEn;

    public long getIdLocal() {
        return idLocal;
    }

    public void setIdLocal(long idLocal) {
        this.idLocal = idLocal;
    }

    @Nullable
    public Integer getIdServidor() {
        return idServidor;
    }

    public void setIdServidor(@Nullable Integer idServidor) {
        this.idServidor = idServidor;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    @Nullable
    public String getIdentidad() {
        return identidad;
    }

    public void setIdentidad(@Nullable String identidad) {
        this.identidad = identidad;
    }

    @Nullable
    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(@Nullable String telefono) {
        this.telefono = telefono;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public int getCantidadPedidos() {
        return cantidadPedidos;
    }

    public void setCantidadPedidos(int cantidadPedidos) {
        this.cantidadPedidos = cantidadPedidos;
    }

    @Nullable
    public String getEstadoSync() {
        return estadoSync;
    }

    public void setEstadoSync(@Nullable String estadoSync) {
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

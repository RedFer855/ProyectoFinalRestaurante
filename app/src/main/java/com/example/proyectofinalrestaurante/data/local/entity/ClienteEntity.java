package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Fila local de un cliente (Plan Fase 2d, E3). Mismo esquema de identidad que
 * {@link MesaEntity}: {@code idLocal} PK, {@code idServidor} nullable con índice único, y las
 * marcas de sincronización.
 *
 * <p>{@code idEstado}/{@code activo} son la baja lógica (espejo de {@code estado_general}).
 * {@code cantidadPedidos} cachea {@code vista_clientes.cantidad_pedidos} para que
 * {@code ReglasCliente.puedeBorrarse} no dependa de una llamada de red.</p>
 */
@Entity(tableName = "clientes",
        indices = {@Index(value = "id_servidor", unique = true)})
public class ClienteEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_local")
    private int idLocal;

    @Nullable
    @ColumnInfo(name = "id_servidor")
    private Integer idServidor;

    @ColumnInfo(name = "nombre")
    private String nombre;

    @ColumnInfo(name = "apellido")
    private String apellido;

    @Nullable
    @ColumnInfo(name = "identidad")
    private String identidad;

    @Nullable
    @ColumnInfo(name = "telefono")
    private String telefono;

    @ColumnInfo(name = "id_estado")
    private int idEstado;

    @ColumnInfo(name = "activo")
    private boolean activo;

    @ColumnInfo(name = "cantidad_pedidos")
    private int cantidadPedidos;

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

    public int getCantidadPedidos() {
        return cantidadPedidos;
    }

    public void setCantidadPedidos(int cantidadPedidos) {
        this.cantidadPedidos = cantidadPedidos;
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

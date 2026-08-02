package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Caché local del catálogo {@code estado_mesa} (Plan Fase 2c, E3).
 *
 * <p>El catálogo es chico y estable (libre, ocupada, reservada); se baja completo con la
 * sincronización y la UI lo usa para traducir {@code idEstadoMesa} en etiquetas sin pedir
 * la descripción al servidor. {@code orden} preserva el orden de presentación que define el
 * servidor. La PK es el id del catálogo, no un id local: nunca se crea ni se borra desde el
 * cliente.</p>
 */
@Entity(tableName = "estados_mesa")
public class EstadoMesaEntity {

    @PrimaryKey
    @ColumnInfo(name = "id_estado_mesa")
    private int idEstadoMesa;

    @NonNull
    @ColumnInfo(name = "descripcion")
    private String descripcion;

    @ColumnInfo(name = "orden")
    private int orden;

    public int getIdEstadoMesa() {
        return idEstadoMesa;
    }

    public void setIdEstadoMesa(int idEstadoMesa) {
        this.idEstadoMesa = idEstadoMesa;
    }

    @NonNull
    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(@NonNull String descripcion) {
        this.descripcion = descripcion;
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }
}

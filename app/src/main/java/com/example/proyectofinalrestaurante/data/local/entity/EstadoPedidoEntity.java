package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Caché local del catálogo {@code estado_pedido} (Plan Fase 3, §4.5).
 *
 * <p>El catálogo es chico y estable (pendiente, en preparación, listo, entregado,
 * cancelado); se baja completo con la sincronización y la UI lo usa para traducir
 * {@code idEstadoPedido} en etiquetas sin pedir la descripción al servidor. {@code orden}
 * preserva el orden de presentación que define el servidor. La PK es el id del catálogo,
 * no un id local: nunca se crea ni se borra desde el cliente.</p>
 *
 * <p>Los ids 1–5 son los canónicos del dominio ({@code EstadoPedido}): 1 pendiente,
 * 2 en preparación, 3 listo, 4 entregado, 5 cancelado (Plan Fase 3, §4.3).</p>
 */
@Entity(tableName = "estados_pedido")
public class EstadoPedidoEntity {

    @PrimaryKey
    @ColumnInfo(name = "id_estado_pedido")
    private int idEstadoPedido;

    @NonNull
    @ColumnInfo(name = "descripcion")
    private String descripcion;

    @ColumnInfo(name = "orden")
    private int orden;

    public int getIdEstadoPedido() {
        return idEstadoPedido;
    }

    public void setIdEstadoPedido(int idEstadoPedido) {
        this.idEstadoPedido = idEstadoPedido;
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
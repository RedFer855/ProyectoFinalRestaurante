package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.Nullable;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Fila local de una línea del detalle de un pedido (Plan Fase 3b, §6.2). Es la <b>escritura
 * atómica</b> del alta: {@code PedidoRepositorioLocal.crear()} escribe la cabecera + estas
 * N líneas en una sola transacción de Room.
 *
 * <p>{@code idPedidoLocal} apunta al {@code id_local} de la cabecera (mismo pedido, así que
 * alto ids locales igual que el resto del módulo). {@code idPlatilloLocal} es el id de Room
 * del platillo, y {@code idServidor} es el {@code id_detalle_pedido} del servidor, nullable
 * mientras el {@code CREAR_PEDIDO} no drene. El precio se deja en la línea para que el detalle
 * se pinte aunque el servidor cambie el precio sellado (ADR-010: el precio del dispositivo es
 * una estimación; el servidor sella el real).</p>
 */
@Entity(tableName = "detalle_pedido",
        indices = {
                @Index(value = "id_pedido"),
                @Index(value = {"id_pedido", "id_platillo_local"})
        })
public class DetallePedidoEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_local")
    private long idLocal;

    @ColumnInfo(name = "id_pedido")
    private long idPedidoLocal;

    @ColumnInfo(name = "id_platillo_local")
    private int idPlatilloLocal;

    @ColumnInfo(name = "cantidad")
    private int cantidad;

    @ColumnInfo(name = "precio")
    private double precio;

    @Nullable
    @ColumnInfo(name = "id_servidor")
    private Integer idServidor;

    public long getIdLocal() {
        return idLocal;
    }

    public void setIdLocal(long idLocal) {
        this.idLocal = idLocal;
    }

    public long getIdPedidoLocal() {
        return idPedidoLocal;
    }

    public void setIdPedidoLocal(long idPedidoLocal) {
        this.idPedidoLocal = idPedidoLocal;
    }

    public int getIdPlatilloLocal() {
        return idPlatilloLocal;
    }

    public void setIdPlatilloLocal(int idPlatilloLocal) {
        this.idPlatilloLocal = idPlatilloLocal;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public double getPrecio() {
        return precio;
    }

    public void setPrecio(double precio) {
        this.precio = precio;
    }

    @Nullable
    public Integer getIdServidor() {
        return idServidor;
    }

    public void setIdServidor(@Nullable Integer idServidor) {
        this.idServidor = idServidor;
    }
}
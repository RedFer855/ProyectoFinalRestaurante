package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Fila local de un pedido (Plan Fase 3, §4.4). Mismo esquema de identidad que
 * {@link MesaEntity}: {@code idLocal} como PK, {@code idServidor} nullable con índice
 * único, y las marcas de sincronización ({@code estadoSync}, {@code actualizadoEn}).
 *
 * <p>{@code fecha} es el instante de ingreso del pedido — lo que ordena el tablero FIFO
 * ({@code R7}). El índice {@code (fecha, idLocal)} respalda esa consulta
 * ({@code ORDER BY fecha ASC, idLocal ASC}), que es la que el tablero reejecuta al crecer
 * la ventana.</p>
 *
 * <p>{@code idAuthUsuario} es el {@code uuid} de {@code auth.users} de quien tomó el
 * pedido, para que el buzón avise a "el mesero que lo tomó" ({@code R9 / historia 9}).</p>
 */
@Entity(tableName = "pedidos",
        indices = {
                @Index(value = {"fecha", "id_local"}),
                @Index(value = "id_servidor", unique = true)
        })
public class PedidoEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_local")
    private long idLocal;

    @Nullable
    @ColumnInfo(name = "id_servidor")
    private Integer idServidor;

    @NonNull
    @ColumnInfo(name = "fecha")
    private String fecha;

    @ColumnInfo(name = "id_estado_pedido")
    private int idEstadoPedido;

    @Nullable
    @ColumnInfo(name = "numero_mesa")
    private Integer numeroMesa;

    @Nullable
    @ColumnInfo(name = "cliente")
    private String cliente;

    @ColumnInfo(name = "total")
    private double total;

    @ColumnInfo(name = "cantidad_items")
    private int cantidadItems;

    @Nullable
    @ColumnInfo(name = "id_auth_usuario")
    private String idAuthUsuario;

    @Nullable
    @ColumnInfo(name = "actualizado_en")
    private String actualizadoEn;

    @NonNull
    @ColumnInfo(name = "estado_sync")
    private String estadoSync;

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

    @NonNull
    public String getFecha() {
        return fecha;
    }

    public void setFecha(@NonNull String fecha) {
        this.fecha = fecha;
    }

    public int getIdEstadoPedido() {
        return idEstadoPedido;
    }

    public void setIdEstadoPedido(int idEstadoPedido) {
        this.idEstadoPedido = idEstadoPedido;
    }

    @Nullable
    public Integer getNumeroMesa() {
        return numeroMesa;
    }

    public void setNumeroMesa(@Nullable Integer numeroMesa) {
        this.numeroMesa = numeroMesa;
    }

    @Nullable
    public String getCliente() {
        return cliente;
    }

    public void setCliente(@Nullable String cliente) {
        this.cliente = cliente;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public int getCantidadItems() {
        return cantidadItems;
    }

    public void setCantidadItems(int cantidadItems) {
        this.cantidadItems = cantidadItems;
    }

    @Nullable
    public String getIdAuthUsuario() {
        return idAuthUsuario;
    }

    public void setIdAuthUsuario(@Nullable String idAuthUsuario) {
        this.idAuthUsuario = idAuthUsuario;
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
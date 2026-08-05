package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Fila de {@code public.vista_pedidos} (Plan Fase 3, §2.4). En la base todo es
 * {@code snake_case}; el mapeo a Java es explícito con {@code @SerializedName}.
 *
 * <p>La vista une {@code pedido} con {@code estado_pedido}, {@code tipo_pedido},
 * {@code usuarios}, {@code mesa} y {@code clientes}, y calcula {@code total} y
 * {@code cantidad_items} con el {@code detalle_pedido}. El tablero gasta esos agregados sin
 * tener que bajar {@code detalle_pedido} — justo lo que R6 quiere evitar (Plan Fase 3,
 * §2.4).</p>
 *
 * <p>{@code id_auth_usuario} es el {@code uuid} de {@code auth.users} del empleado que tomó
 * el pedido: expuesto en la vista a propósito (no filtra nada sensible) para que el buzón
 * avise a "el mesero que lo tomó" con una comparación de strings en memoria, sin resolver el
 * {@code id_usuario} contra {@code public.usuarios} con una consulta extra.</p>
 */
public final class PedidoDto {

    @SerializedName("id_pedido")
    private int idPedido;

    @SerializedName("fecha")
    private String fecha;

    @SerializedName("id_estado_pedido")
    private int idEstadoPedido;

    /** Descripción del catálogo {@code estado_pedido} resuelta por el join de la vista. */
    @SerializedName("estado_pedido")
    private String estadoPedido;

    @SerializedName("id_estado")
    private int idEstado;

    @SerializedName("id_mesa")
    private Integer idMesa;

    @SerializedName("numero_mesa")
    private Integer numeroMesa;

    @SerializedName("id_cliente")
    private Integer idCliente;

    @SerializedName("cliente")
    private String cliente;

    @SerializedName("id_tipo_pedido")
    private int idTipoPedido;

    @SerializedName("tipo_pedido")
    private String tipoPedido;

    @SerializedName("id_usuario")
    private int idUsuario;

    @SerializedName("id_auth_usuario")
    private String idAuthUsuario;

    @SerializedName("total")
    private double total;

    @SerializedName("cantidad_items")
    private int cantidadItems;

    /** Marca de la fila en el servidor; es la que permite el sync delta (Fase 2b, §4.3). */
    @SerializedName("actualizado_en")
    private String actualizadoEn;

    public int getIdPedido() {
        return idPedido;
    }

    public String getFecha() {
        return fecha;
    }

    public int getIdEstadoPedido() {
        return idEstadoPedido;
    }

    public String getEstadoPedido() {
        return estadoPedido;
    }

    public int getIdEstado() {
        return idEstado;
    }

    public Integer getIdMesa() {
        return idMesa;
    }

    public Integer getNumeroMesa() {
        return numeroMesa;
    }

    public Integer getIdCliente() {
        return idCliente;
    }

    public String getCliente() {
        return cliente;
    }

    public int getIdTipoPedido() {
        return idTipoPedido;
    }

    public String getTipoPedido() {
        return tipoPedido;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public String getIdAuthUsuario() {
        return idAuthUsuario;
    }

    public double getTotal() {
        return total;
    }

    public int getCantidadItems() {
        return cantidadItems;
    }

    public String getActualizadoEn() {
        return actualizadoEn;
    }
}
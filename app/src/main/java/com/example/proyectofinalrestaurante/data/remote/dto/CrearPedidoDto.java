package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Respuesta del RPC {@code crear_pedido(jsonb)} (Plan Fase 3b, §5.3). El RPC devuelve el
 * {@code id_pedido} creado; si la {@code clave_idempotencia} ya existía, devuelve el del
 * pedido ya creado, sin insertar (B2). Es el dato que el {@code SincronizadorPedidos} usa
 * para resolver el {@code id_servidor} de la cabecera y sus líneas.
 */
public final class CrearPedidoDto {

    @SerializedName("id_pedido")
    private int idPedido;

    public int getIdPedido() {
        return idPedido;
    }
}
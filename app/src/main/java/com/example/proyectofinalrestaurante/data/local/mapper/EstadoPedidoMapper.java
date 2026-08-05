package com.example.proyectofinalrestaurante.data.local.mapper;

import com.example.proyectofinalrestaurante.data.local.entity.EstadoPedidoEntity;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;

/**
 * Mapeo {@link EstadoPedidoEntity} ↔ {@link EstadoPedido} (Plan Fase 3, E3). Como el catálogo
 * se baja completo del servidor, la entidad es prácticamente el enum serializado: solo se
 * traduce {@code idEstadoPedido} → {@code EstadoPedido.porId(id)}.
 */
public final class EstadoPedidoMapper {

    private EstadoPedidoMapper() {
    }

    public static EstadoPedido aDominio(EstadoPedidoEntity entidad) {
        return EstadoPedido.porId(entidad.getIdEstadoPedido());
    }

    public static EstadoPedidoEntity aEntidad(EstadoPedido estado) {
        EstadoPedidoEntity entidad = new EstadoPedidoEntity();
        entidad.setIdEstadoPedido(estado.getId());
        entidad.setDescripcion(estado.name());
        entidad.setOrden(estado.getId());
        return entidad;
    }
}
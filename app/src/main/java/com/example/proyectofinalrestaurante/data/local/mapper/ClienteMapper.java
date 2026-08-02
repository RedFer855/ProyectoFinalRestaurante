package com.example.proyectofinalrestaurante.data.local.mapper;

import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;
import com.example.proyectofinalrestaurante.data.remote.dto.ClienteDto;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;

/**
 * Mapeo {@link ClienteEntity} ↔ {@link Cliente} (Plan Fase 2d, E3). Mismo rol que
 * {@link MesaMapper} en Mesas.
 *
 * <p>{@link #desdeServidor} deriva {@code activo} de {@code id_estado} y no confía en el
 * campo {@code activo} del DTO, por la misma razón que {@code MesaMapper}: la respuesta del
 * {@code INSERT} sobre la tabla {@code clientes} no lo expone, solo la vista lo trae.</p>
 */
public final class ClienteMapper {

    private static final int ID_ESTADO_ACTIVO = 1;
    private static final int ID_ESTADO_BAJA = 2;

    private ClienteMapper() {
    }

    public static Cliente aDominio(ClienteEntity entidad) {
        return new Cliente(
                entidad.getIdLocal(),
                entidad.getIdServidor(),
                entidad.getNombre(),
                entidad.getApellido(),
                entidad.getIdentidad(),
                entidad.getTelefono(),
                entidad.isActivo(),
                entidad.getCantidadPedidos(),
                entidad.getActualizadoEn(),
                PlatilloMapper.aEstadoSync(entidad.getEstadoSync()));
    }

    public static ClienteEntity aEntidad(Cliente cliente) {
        ClienteEntity entidad = new ClienteEntity();
        entidad.setIdLocal(cliente.getIdLocal());
        entidad.setIdServidor(cliente.getIdServidor());
        entidad.setNombre(cliente.getNombre());
        entidad.setApellido(cliente.getApellido());
        entidad.setIdentidad(cliente.getIdentidad());
        entidad.setTelefono(cliente.getTelefono());
        entidad.setIdEstado(cliente.isActivo() ? ID_ESTADO_ACTIVO : ID_ESTADO_BAJA);
        entidad.setActivo(cliente.isActivo());
        entidad.setCantidadPedidos(cliente.getCantidadPedidos());
        entidad.setActualizadoEn(cliente.getActualizadoEn());
        entidad.setEstadoSync(cliente.getEstadoSync().name());
        return entidad;
    }

    /** Entidad a partir de una fila bajada del servidor (delta o INSERT). Llega sincronizada. */
    public static ClienteEntity desdeServidor(ClienteDto dto) {
        ClienteEntity entidad = new ClienteEntity();
        entidad.setIdServidor(dto.getIdCliente());
        entidad.setNombre(dto.getNombre());
        entidad.setApellido(dto.getApellido());
        entidad.setIdentidad(dto.getIdentidad());
        entidad.setTelefono(dto.getTelefono());
        entidad.setIdEstado(dto.getIdEstado());
        entidad.setActivo(dto.getIdEstado() == ID_ESTADO_ACTIVO);
        entidad.setCantidadPedidos(dto.getCantidadPedidos());
        entidad.setActualizadoEn(dto.getActualizadoEn());
        entidad.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        return entidad;
    }
}

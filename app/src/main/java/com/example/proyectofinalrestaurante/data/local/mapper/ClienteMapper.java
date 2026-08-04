package com.example.proyectofinalrestaurante.data.local.mapper;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;
import com.example.proyectofinalrestaurante.data.remote.dto.ClienteDto;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapeo {@link ClienteEntity} ↔ {@link Cliente} (Fase 2d).
 *
 * <p>Room no puede guardar el modelo de {@code domain} tal cual (regla 1 del Protocolo), así
 * que la conversión vive acá. {@link #desdeServidor} construye la entidad a partir de lo que
 * baja el delta.</p>
 */
public final class ClienteMapper {

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
                aEstadoSync(entidad.getEstadoSync()));
    }

    public static ClienteEntity aEntidad(Cliente cliente) {
        ClienteEntity entidad = new ClienteEntity();
        entidad.setIdLocal(cliente.getIdLocal());
        entidad.setIdServidor(cliente.getIdServidor());
        entidad.setNombre(cliente.getNombre());
        entidad.setApellido(cliente.getApellido());
        entidad.setIdentidad(cliente.getIdentidad());
        entidad.setTelefono(cliente.getTelefono());
        entidad.setActivo(cliente.isActivo());
        entidad.setCantidadPedidos(cliente.getCantidadPedidos());
        entidad.setEstadoSync(cliente.getEstadoSync().name());
        return entidad;
    }

    /**
     * Entidad a partir de una fila bajada del servidor. Todo lo que baja llega
     * sincronizado.
     */
    public static ClienteEntity desdeServidor(ClienteDto dto) {
        ClienteEntity entidad = new ClienteEntity();
        entidad.setIdServidor(dto.getIdCliente());
        entidad.setNombre(dto.getNombre());
        entidad.setApellido(dto.getApellido());
        entidad.setIdentidad(dto.getIdentidad());
        entidad.setTelefono(dto.getTelefono());
        entidad.setActivo(dto.isActivo());
        entidad.setCantidadPedidos(dto.getCantidadPedidos());
        entidad.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        entidad.setActualizadoEn(dto.getActualizadoEn());
        return entidad;
    }

    public static List<Cliente> aDominioLista(List<ClienteEntity> entidades) {
        List<Cliente> lista = new ArrayList<>();
        for (ClienteEntity entidad : entidades) {
            lista.add(aDominio(entidad));
        }
        return lista;
    }

    public static EstadoSync aEstadoSync(@Nullable String estado) {
        if (estado == null) {
            return EstadoSync.SINCRONIZADO;
        }
        try {
            return EstadoSync.valueOf(estado);
        } catch (IllegalArgumentException ignorado) {
            return EstadoSync.SINCRONIZADO;
        }
    }
}

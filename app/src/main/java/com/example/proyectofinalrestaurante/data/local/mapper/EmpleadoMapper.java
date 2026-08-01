package com.example.proyectofinalrestaurante.data.local.mapper;

import com.example.proyectofinalrestaurante.data.local.entity.EmpleadoEntity;
import com.example.proyectofinalrestaurante.data.remote.dto.EmpleadoDto;
import com.example.proyectofinalrestaurante.domain.model.Empleado;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;

/**
 * Mapeo {@link EmpleadoEntity} ↔ {@link Empleado}, con el mismo reparto que
 * {@link PlatilloMapper}: Room no puede guardar el modelo de {@code domain} (regla 1 del
 * Protocolo), así que la conversión vive acá.
 */
public final class EmpleadoMapper {

    private EmpleadoMapper() {
    }

    public static Empleado aDominio(EmpleadoEntity entidad) {
        return new Empleado(
                entidad.getIdEmpleado(),
                entidad.getNombres(),
                entidad.getApellidos(),
                entidad.getIdentidad(),
                entidad.getTelefono(),
                entidad.getCorreo(),
                entidad.getIdUsuario(),
                entidad.getApodoUsuario(),
                entidad.getIdAuthUser(),
                entidad.getRol(),
                entidad.isActivo(),
                PlatilloMapper.aEstadoSync(entidad.getEstadoSync()));
    }

    /**
     * Entidad a partir de una fila bajada del servidor: todo lo que baja llega sincronizado.
     * La marca {@code actualizado_en} viene ya resuelta por la vista.
     */
    public static EmpleadoEntity desdeServidor(EmpleadoDto dto) {
        EmpleadoEntity entidad = new EmpleadoEntity();
        entidad.setIdEmpleado(dto.getIdEmpleado());
        entidad.setNombres(dto.getNombres());
        entidad.setApellidos(dto.getApellidos());
        entidad.setIdentidad(dto.getIdentidad());
        entidad.setTelefono(dto.getTelefono());
        entidad.setCorreo(dto.getCorreo());
        entidad.setIdUsuario(dto.getIdUsuario());
        entidad.setApodoUsuario(dto.getApodoUsuario());
        entidad.setIdAuthUser(dto.getIdAuthUser());
        entidad.setRol(dto.getRol());
        entidad.setActivo(dto.isActivo());
        entidad.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        entidad.setActualizadoEn(dto.getActualizadoEn());
        return entidad;
    }
}

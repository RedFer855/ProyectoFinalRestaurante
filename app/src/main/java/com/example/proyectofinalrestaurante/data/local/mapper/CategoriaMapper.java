package com.example.proyectofinalrestaurante.data.local.mapper;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.entity.CategoriaEntity;
import com.example.proyectofinalrestaurante.data.remote.dto.CategoriaDto;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;

/**
 * Mapeo {@link CategoriaEntity} ↔ {@link Categoria} (Plan Fase 2b, E3). Equivalente a
 * {@link PlatilloMapper}, incluido el paso servidor → entidad para el delta.
 */
public final class CategoriaMapper {

    private static final int ID_ESTADO_ACTIVO = 1;

    private CategoriaMapper() {
    }

    public static Categoria aDominio(CategoriaEntity entidad) {
        return new Categoria(
                entidad.getIdLocal(),
                entidad.getIdServidor(),
                entidad.getDescripcion(),
                entidad.isActivo(),
                entidad.getCantidadPlatillos(),
                entidad.getCantidadPlatillosActivos(),
                PlatilloMapper.aEstadoSync(entidad.getEstadoSync()));
    }

    public static CategoriaEntity aEntidad(Categoria categoria) {
        CategoriaEntity entidad = new CategoriaEntity();
        entidad.setIdLocal(categoria.getIdLocal());
        entidad.setIdServidor(categoria.getIdServidor());
        entidad.setDescripcion(categoria.getDescripcion());
        entidad.setActivo(categoria.isActivo());
        entidad.setCantidadPlatillos(categoria.getCantidadPlatillos());
        entidad.setCantidadPlatillosActivos(categoria.getCantidadPlatillosActivos());
        entidad.setEstadoSync(categoria.getEstadoSync().name());
        return entidad;
    }

    /** Entidad a partir de una fila bajada del servidor, marcada como sincronizada. */
    public static CategoriaEntity desdeServidor(CategoriaDto dto) {
        CategoriaEntity entidad = new CategoriaEntity();
        entidad.setIdServidor(dto.getIdCategoria());
        entidad.setDescripcion(dto.getDescripcion());
        entidad.setActivo(dto.getIdEstado() == ID_ESTADO_ACTIVO);
        entidad.setCantidadPlatillos(dto.getCantidadPlatillos());
        entidad.setCantidadPlatillosActivos(dto.getCantidadPlatillosActivos());
        entidad.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        entidad.setActualizadoEn(dto.getActualizadoEn());
        return entidad;
    }
}
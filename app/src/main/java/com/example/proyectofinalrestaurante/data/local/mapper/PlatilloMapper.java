package com.example.proyectofinalrestaurante.data.local.mapper;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.entity.PlatilloEntity;
import com.example.proyectofinalrestaurante.data.remote.dto.PlatilloDto;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Platillo;

/**
 * Mapeo {@link PlatilloEntity} ↔ {@link Platillo} (Plan Fase 2b, E3).
 *
 * <p>Room no puede guardar el modelo de {@code domain} tal cual (regla 1 del Protocolo), así
 * que la conversión vive acá, igual que ya hacen los DTOs. {@link #desdeServidor} construye
 * la entidad a partir de lo que baja el delta, ya con la categoría resuelta a su
 * {@code idLocal} — la resolución la hace el sincronizador, que es quien sabe a qué fila
 * local corresponde cada {@code id_categoria} del servidor.</p>
 */
public final class PlatilloMapper {

    private static final int ID_ESTADO_ACTIVO = 1;

    private PlatilloMapper() {
    }

    public static Platillo aDominio(PlatilloEntity entidad) {
        return new Platillo(
                entidad.getIdLocal(),
                entidad.getIdServidor(),
                entidad.getNombre(),
                entidad.getDescripcion(),
                entidad.getPrecio(),
                entidad.getIdCategoriaLocal(),
                entidad.getNombreCategoria(),
                entidad.getRutaImagen(),
                entidad.isActivo(),
                aEstadoSync(entidad.getEstadoSync()));
    }

    public static PlatilloEntity aEntidad(Platillo platillo) {
        PlatilloEntity entidad = new PlatilloEntity();
        entidad.setIdLocal(platillo.getIdLocal());
        entidad.setIdServidor(platillo.getIdServidor());
        entidad.setNombre(platillo.getNombre());
        entidad.setDescripcion(platillo.getDescripcion());
        entidad.setPrecio(platillo.getPrecio());
        entidad.setIdCategoriaLocal(platillo.getIdCategoria());
        entidad.setNombreCategoria(platillo.getNombreCategoria());
        entidad.setRutaImagen(platillo.getRutaImagen());
        entidad.setActivo(platillo.isActivo());
        entidad.setEstadoSync(platillo.getEstadoSync().name());
        return entidad;
    }

    /**
     * Entidad a partir de una fila bajada del servidor. Todo lo que baja llega
     * sincronizado; la categoría ya viene resuelta a {@code idCategoriaLocal} por el
     * sincronizador.
     */
    public static PlatilloEntity desdeServidor(PlatilloDto dto, int idCategoriaLocal) {
        PlatilloEntity entidad = new PlatilloEntity();
        entidad.setIdServidor(dto.getIdPlatillo());
        entidad.setNombre(dto.getNombre());
        entidad.setDescripcion(dto.getDescripcion());
        entidad.setPrecio(dto.getPrecio());
        entidad.setIdCategoriaLocal(idCategoriaLocal);
        entidad.setNombreCategoria(dto.getNombreCategoria());
        entidad.setRutaImagen(dto.getRutaImagen());
        entidad.setActivo(dto.getIdEstado() == ID_ESTADO_ACTIVO);
        entidad.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        entidad.setActualizadoEn(dto.getActualizadoEn());
        return entidad;
    }

    public static EstadoSync aEstadoSync(@Nullable String estado) {
        if (estado == null) {
            return EstadoSync.SINCRONIZADO;
        }
        try {
            return EstadoSync.valueOf(estado);
        } catch (IllegalArgumentException ignorado) {
            // Estado desconocido en disco: se trata como sincronizado. No vale la pena
            // romper toda la pantalla por un valor corrupto de una fila.
            return EstadoSync.SINCRONIZADO;
        }
    }
}

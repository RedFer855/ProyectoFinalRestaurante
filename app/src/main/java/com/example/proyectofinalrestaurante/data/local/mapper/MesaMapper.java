package com.example.proyectofinalrestaurante.data.local.mapper;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;
import com.example.proyectofinalrestaurante.data.remote.dto.MesaDto;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Mesa;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapeo {@link MesaEntity} ↔ {@link Mesa} (Fase 2c).
 *
 * <p>Room no puede guardar el modelo de {@code domain} tal cual (regla 1 del Protocolo), así
 * que la conversión vive acá. {@link #desdeServidor} construye la entidad a partir de lo que
 * baja el delta.</p>
 */
public final class MesaMapper {

    private static final int ID_ESTADO_ACTIVO = 1;

    private MesaMapper() {
    }

    public static Mesa aDominio(MesaEntity entidad) {
        return new Mesa(
                entidad.getIdLocal(),
                entidad.getIdServidor(),
                entidad.getNumeroMesa(),
                entidad.getCapacidad(),
                entidad.getUbicacion(),
                aEstadoMesa(entidad.getEstadoMesa()),
                entidad.isActivo(),
                aEstadoSync(entidad.getEstadoSync()));
    }

    public static List<Mesa> aDominioLista(List<MesaEntity> entidades) {
        List<Mesa> lista = new ArrayList<>();
        for (MesaEntity entidad : entidades) {
            lista.add(aDominio(entidad));
        }
        return lista;
    }

    public static MesaEntity aEntidad(Mesa mesa) {
        MesaEntity entidad = new MesaEntity();
        entidad.setIdLocal(mesa.getIdLocal());
        entidad.setIdServidor(mesa.getIdServidor());
        entidad.setNumeroMesa(mesa.getNumeroMesa());
        entidad.setCapacidad(mesa.getCapacidad());
        entidad.setUbicacion(mesa.getUbicacion());
        entidad.setEstadoMesa(mesa.getEstadoMesa().name());
        entidad.setActivo(mesa.isActivo());
        entidad.setEstadoSync(mesa.getEstadoSync().name());
        return entidad;
    }

    /**
     * Entidad a partir de una fila bajada del servidor. Todo lo que baja llega
     * sincronizado.
     */
    public static MesaEntity desdeServidor(MesaDto dto) {
        MesaEntity entidad = new MesaEntity();
        entidad.setIdServidor(dto.getIdMesa());
        entidad.setNumeroMesa(dto.getNumeroMesa());
        entidad.setCapacidad(dto.getCapacidad());
        entidad.setUbicacion(dto.getUbicacion());
        entidad.setEstadoMesa(aEstadoMesaNombre(dto.getIdEstadoMesa()));
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
            return EstadoSync.SINCRONIZADO;
        }
    }

    public static EstadoMesa aEstadoMesa(@Nullable String nombre) {
        if (nombre == null) {
            return EstadoMesa.LIBRE;
        }
        try {
            return EstadoMesa.valueOf(nombre.toUpperCase());
        } catch (IllegalArgumentException ignorado) {
            return EstadoMesa.LIBRE;
        }
    }

    private static String aEstadoMesaNombre(int idEstadoMesa) {
        EstadoMesa estado = EstadoMesa.porId(idEstadoMesa);
        return estado != null ? estado.name() : EstadoMesa.LIBRE.name();
    }
}

package com.example.proyectofinalrestaurante.data.local.mapper;

import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;
import com.example.proyectofinalrestaurante.data.remote.dto.MesaDto;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Mesa;

/**
 * Mapeo {@link MesaEntity} ↔ {@link Mesa} (Plan Fase 2c, E3). Mismo rol que
 * {@link PlatilloMapper} en el módulo Menú.
 *
 * <p>La mesa tiene dos estados ortogonales que acá se traducen entre sí:
 * {@code Mesa.activo} (baja lógica sobre {@code id_estado}, {@code 1 = activo}) y
 * {@code Mesa.estado} (catálogo {@code estado_mesa} vía {@link EstadoMesa#porId(int)}).
 * Cuando el servidor trae un {@code id_estado_mesa} que este APK no conoce,
 * {@code porId} devuelve {@code null} y el dominio lo ve como estado desconocido —
 * <b>nunca</b> se mapea a un estado conocido por defecto (Plan Fase 2c, §2.2).</p>
 *
 * <p>{@link #desdeServidor} deriva {@code activo} de {@code id_estado} y <b>no</b> confía
 * en el campo {@code activo} del DTO: la vista {@code vista_mesas} lo trae, pero la tabla
 * {@code mesa} (de donde sale la respuesta del {@code INSERT} con
 * {@code return=representation}) no lo expone, así que solo {@code id_estado} existe en
 * ambos orígenes (ver {@link MesaDto}).</p>
 */
public final class MesaMapper {

    private static final int ID_ESTADO_ACTIVO = 1;
    private static final int ID_ESTADO_BAJA = 2;

    /** Valor centinela para {@code id_estado_mesa} cuando el estado es desconocido. */
    private static final int ID_ESTADO_MESA_DESCONOCIDO = 0;

    private MesaMapper() {
    }

    public static Mesa aDominio(MesaEntity entidad) {
        return new Mesa(
                entidad.getIdLocal(),
                entidad.getIdServidor(),
                entidad.getNumeroMesa(),
                entidad.getCapacidad(),
                entidad.getUbicacion(),
                EstadoMesa.porId(entidad.getIdEstadoMesa()),
                entidad.isActivo(),
                entidad.getActualizadoEn(),
                PlatilloMapper.aEstadoSync(entidad.getEstadoSync()));
    }

    public static MesaEntity aEntidad(Mesa mesa) {
        MesaEntity entidad = new MesaEntity();
        entidad.setIdLocal(mesa.getIdLocal());
        entidad.setIdServidor(mesa.getIdServidor());
        entidad.setNumeroMesa(mesa.getNumeroMesa());
        entidad.setCapacidad(mesa.getCapacidad());
        entidad.setUbicacion(mesa.getUbicacion());
        // Estado operativo: solo se reescribe cuando se conoce. El centinela 0 mantiene la
        // columna NOT NULL y redondea a "desconocido" en el próximo aDominio (porId(0) = null).
        EstadoMesa estado = mesa.getEstado();
        entidad.setIdEstadoMesa(estado == null ? ID_ESTADO_MESA_DESCONOCIDO : estado.getId());
        entidad.setEstadoMesa(estado == null ? null : estado.name());
        entidad.setIdEstado(mesa.isActivo() ? ID_ESTADO_ACTIVO : ID_ESTADO_BAJA);
        entidad.setActivo(mesa.isActivo());
        entidad.setActualizadoEn(mesa.getActualizadoEn());
        entidad.setEstadoSync(mesa.getEstadoSync().name());
        return entidad;
    }

    /**
     * Entidad a partir de una fila bajada del servidor (delta o respuesta del {@code INSERT}
     * con {@code return=representation}). Todo lo que baja llega sincronizado.
     */
    public static MesaEntity desdeServidor(MesaDto dto) {
        MesaEntity entidad = new MesaEntity();
        entidad.setIdServidor(dto.getIdMesa());
        entidad.setNumeroMesa(dto.getNumeroMesa());
        entidad.setCapacidad(dto.getCapacidad());
        entidad.setUbicacion(dto.getUbicacion());
        entidad.setIdEstadoMesa(dto.getIdEstadoMesa());
        entidad.setEstadoMesa(dto.getEstadoMesa());
        entidad.setIdEstado(dto.getIdEstado());
        entidad.setActivo(dto.getIdEstado() == ID_ESTADO_ACTIVO);
        entidad.setActualizadoEn(dto.getActualizadoEn());
        entidad.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        return entidad;
    }
}

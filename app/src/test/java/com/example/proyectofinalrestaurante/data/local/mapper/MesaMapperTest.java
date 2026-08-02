package com.example.proyectofinalrestaurante.data.local.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;
import com.example.proyectofinalrestaurante.data.remote.dto.MesaDto;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.google.gson.Gson;

import org.junit.Test;

/**
 * El mapeo mesa local ↔ dominio (Plan Fase 2c, E3). Test de JVM pura: no toca Room. El
 * {@link MesaDto} se construye con Gson porque sus campos son privados y solo se pueblan
 * por reflexión al deserializar.
 */
public class MesaMapperTest {

    @Test
    public void aDominio_mapeaLosDosEstadosYLasMarcas() {
        MesaEntity entidad = unaEntidad();
        entidad.setIdEstadoMesa(1);
        entidad.setActivo(true);

        Mesa mesa = MesaMapper.aDominio(entidad);

        assertEquals(3, mesa.getIdLocal());
        assertEquals(Integer.valueOf(7), mesa.getIdServidor());
        assertEquals(4, mesa.getNumeroMesa());
        assertEquals(6, mesa.getCapacidad());
        assertEquals("Patio", mesa.getUbicacion());
        assertEquals(EstadoMesa.LIBRE, mesa.getEstado());
        assertTrue(mesa.isActivo());
        assertEquals(EstadoSync.SINCRONIZADO, mesa.getEstadoSync());
    }

    @Test
    public void aDominio_conIdEstadoMesaDesconocido_mapeaEstadoNull() {
        MesaEntity entidad = unaEntidad();
        entidad.setIdEstadoMesa(99);
        entidad.setActivo(false);

        Mesa mesa = MesaMapper.aDominio(entidad);

        assertNull(mesa.getEstado());
        assertFalse(mesa.isActivo());
    }

    @Test
    public void aEntidad_derivaIdEstadoYRedondeaElEstado() {
        Mesa mesa = unaMesa(EstadoMesa.OCUPADA, true);

        MesaEntity entidad = MesaMapper.aEntidad(mesa);

        assertEquals(1, entidad.getIdEstado());
        assertTrue(entidad.isActivo());
        assertEquals(2, entidad.getIdEstadoMesa());
        assertEquals("OCUPADA", entidad.getEstadoMesa());
        assertEquals("SINCRONIZADO", entidad.getEstadoSync());
    }

    @Test
    public void aEntidad_mesaDeBaja_derivaIdEstado2() {
        Mesa mesa = unaMesa(EstadoMesa.LIBRE, false);

        MesaEntity entidad = MesaMapper.aEntidad(mesa);

        assertEquals(2, entidad.getIdEstado());
        assertFalse(entidad.isActivo());
    }

    @Test
    public void aEntidad_conEstadoDesconocido_noInventaEstado() {
        Mesa mesa = unaMesa(null, true);

        MesaEntity entidad = MesaMapper.aEntidad(mesa);

        assertEquals(0, entidad.getIdEstadoMesa());
        assertNull(entidad.getEstadoMesa());
        assertEquals(1, entidad.getIdEstado());
    }

    @Test
    public void desdeServidor_derivaActivoDeIdEstadoNoDelCampoActivoDeLaVista() {
        MesaDto dto = dto("{ \"id_mesa\": 7, \"numero_mesa\": 4, \"capacidad\": 6, "
                + "\"id_estado_mesa\": 1, \"id_estado\": 1, \"activo\": false }");

        MesaEntity entidad = MesaMapper.desdeServidor(dto);

        assertTrue(entidad.isActivo());
        assertEquals(1, entidad.getIdEstado());
    }

    @Test
    public void desdeServidor_conIdEstadoDeBaja_mapeaActivoFalse() {
        MesaDto dto = dto("{ \"id_mesa\": 7, \"numero_mesa\": 4, \"capacidad\": 6, "
                + "\"id_estado_mesa\": 1, \"id_estado\": 2, \"activo\": true }");

        MesaEntity entidad = MesaMapper.desdeServidor(dto);

        assertFalse(entidad.isActivo());
        assertEquals(2, entidad.getIdEstado());
    }

    @Test
    public void desdeServidor_marcaSincronizadoYConservaLasMarcasDelServidor() {
        MesaDto dto = dto("{ \"id_mesa\": 7, \"numero_mesa\": 4, \"capacidad\": 6, "
                + "\"ubicacion\": \"Patio\", \"id_estado_mesa\": 2, \"estado_mesa\": \"Ocupada\", "
                + "\"id_estado\": 1, \"actualizado_en\": \"2026-08-01T12:00:00Z\" }");

        MesaEntity entidad = MesaMapper.desdeServidor(dto);

        assertEquals(Integer.valueOf(7), entidad.getIdServidor());
        assertEquals(2, entidad.getIdEstadoMesa());
        assertEquals("Ocupada", entidad.getEstadoMesa());
        assertEquals("2026-08-01T12:00:00Z", entidad.getActualizadoEn());
        assertEquals("SINCRONIZADO", entidad.getEstadoSync());
    }

    private static MesaEntity unaEntidad() {
        MesaEntity entidad = new MesaEntity();
        entidad.setIdLocal(3);
        entidad.setIdServidor(7);
        entidad.setNumeroMesa(4);
        entidad.setCapacidad(6);
        entidad.setUbicacion("Patio");
        entidad.setIdEstado(1);
        entidad.setActualizadoEn("2026-08-01");
        entidad.setEstadoSync("SINCRONIZADO");
        return entidad;
    }

    private static Mesa unaMesa(EstadoMesa estado, boolean activo) {
        return new Mesa(3, 7, 4, 6, "Patio", estado, activo, "2026-08-01",
                EstadoSync.SINCRONIZADO);
    }

    private static MesaDto dto(String json) {
        return new Gson().fromJson(json, MesaDto.class);
    }
}

package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.MesaMapper;
import com.example.proyectofinalrestaurante.data.remote.dto.MesaDto;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Tests de {@link MesaMapper} (Fase 2c, E7). Java puro, sin dependencias de Android. */
public class MesaMapperTest {

    // ------------------------------------------------------------------ aDominio

    @Test
    public void aDominio_mapeaTodosLosCampos() {
        MesaEntity entidad = new MesaEntity();
        entidad.setIdLocal(10);
        entidad.setIdServidor(100);
        entidad.setNumeroMesa(4);
        entidad.setCapacidad(6);
        entidad.setUbicacion("Salón interior");
        entidad.setEstadoMesa(EstadoMesa.OCUPADA.name());
        entidad.setActivo(true);
        entidad.setEstadoSync(EstadoSync.SINCRONIZADO.name());

        Mesa mesa = MesaMapper.aDominio(entidad);

        assertEquals(10, mesa.getIdLocal());
        assertEquals(Integer.valueOf(100), mesa.getIdServidor());
        assertEquals(4, mesa.getNumeroMesa());
        assertEquals(6, mesa.getCapacidad());
        assertEquals("Salón interior", mesa.getUbicacion());
        assertEquals(EstadoMesa.OCUPADA, mesa.getEstadoMesa());
        assertTrue(mesa.isActivo());
        assertEquals(EstadoSync.SINCRONIZADO, mesa.getEstadoSync());
    }

    @Test
    public void aDominio_idServidorNull_seMantiene() {
        MesaEntity entidad = new MesaEntity();
        entidad.setIdLocal(10);
        entidad.setIdServidor(null);
        entidad.setNumeroMesa(4);
        entidad.setCapacidad(6);
        entidad.setUbicacion(null);
        entidad.setEstadoMesa(EstadoMesa.LIBRE.name());
        entidad.setActivo(true);
        entidad.setEstadoSync(EstadoSync.SINCRONIZAR.name());

        Mesa mesa = MesaMapper.aDominio(entidad);

        assertNull(mesa.getIdServidor());
        assertNull(mesa.getUbicacion());
        assertEquals(EstadoSync.SINCRONIZAR, mesa.getEstadoSync());
    }

    @Test
    public void aDominio_estadoMesaNull_defaultLibre() {
        MesaEntity entidad = new MesaEntity();
        entidad.setEstadoMesa(null);

        Mesa mesa = MesaMapper.aDominio(entidad);

        assertEquals(EstadoMesa.LIBRE, mesa.getEstadoMesa());
    }

    @Test
    public void aDominio_estadoSyncNull_defaultSincronizado() {
        MesaEntity entidad = new MesaEntity();
        entidad.setEstadoSync(null);

        Mesa mesa = MesaMapper.aDominio(entidad);

        assertEquals(EstadoSync.SINCRONIZADO, mesa.getEstadoSync());
    }

    // ------------------------------------------------------------------ aEntidad

    @Test
    public void aEntidad_mapeaTodosLosCampos() {
        Mesa mesa = new Mesa(10, 100, 4, 6, "Salón",
                EstadoMesa.RESERVADA, true, EstadoSync.SINCRONIZAR);

        MesaEntity entidad = MesaMapper.aEntidad(mesa);

        assertEquals(10, entidad.getIdLocal());
        assertEquals(Integer.valueOf(100), entidad.getIdServidor());
        assertEquals(4, entidad.getNumeroMesa());
        assertEquals(6, entidad.getCapacidad());
        assertEquals("Salón", entidad.getUbicacion());
        assertEquals(EstadoMesa.RESERVADA.name(), entidad.getEstadoMesa());
        assertTrue(entidad.isActivo());
        assertEquals(EstadoSync.SINCRONIZAR.name(), entidad.getEstadoSync());
    }

    // ------------------------------------------------------------------ ida y vuelta

    @Test
    public void idaYVuelta_seConserva() {
        MesaEntity original = new MesaEntity();
        original.setIdLocal(10);
        original.setIdServidor(100);
        original.setNumeroMesa(4);
        original.setCapacidad(6);
        original.setUbicacion("Salón");
        original.setEstadoMesa(EstadoMesa.OCUPADA.name());
        original.setActivo(true);
        original.setEstadoSync(EstadoSync.SINCRONIZADO.name());

        Mesa mesa = MesaMapper.aDominio(original);
        MesaEntity regreso = MesaMapper.aEntidad(mesa);

        assertEquals(original.getIdLocal(), regreso.getIdLocal());
        assertEquals(original.getIdServidor(), regreso.getIdServidor());
        assertEquals(original.getNumeroMesa(), regreso.getNumeroMesa());
        assertEquals(original.getCapacidad(), regreso.getCapacidad());
        assertEquals(original.getUbicacion(), regreso.getUbicacion());
        assertEquals(original.getEstadoMesa(), regreso.getEstadoMesa());
        assertEquals(original.isActivo(), regreso.isActivo());
        assertEquals(original.getEstadoSync(), regreso.getEstadoSync());
    }

    private static MesaDto dto(int idMesa, int numero, int capacidad, int idEstadoMesa,
                               int idEstado, String actualizadoEn) {
        return new Gson().fromJson(
                "{\"id_mesa\":" + idMesa + ",\"numero_mesa\":" + numero
                        + ",\"capacidad\":" + capacidad
                        + ",\"id_estado_mesa\":" + idEstadoMesa
                        + ",\"id_estado\":" + idEstado
                        + ",\"actualizado_en\":\"" + actualizadoEn + "\"}",
                MesaDto.class);
    }

    // ------------------------------------------------------------------ desdeServidor

    @Test
    public void desdeServidor_mapeaCamposClave() {
        MesaDto dto = dto(100, 4, 6, 2, 1, "2026-08-01T10:00:00Z");

        MesaEntity entidad = MesaMapper.desdeServidor(dto);

        assertEquals(Integer.valueOf(100), entidad.getIdServidor());
        assertEquals(4, entidad.getNumeroMesa());
        assertEquals(6, entidad.getCapacidad());
        assertEquals(EstadoMesa.OCUPADA.name(), entidad.getEstadoMesa());
        assertTrue(entidad.isActivo());
        assertEquals(EstadoSync.SINCRONIZADO.name(), entidad.getEstadoSync());
        assertEquals("2026-08-01T10:00:00Z", entidad.getActualizadoEn());
    }

    @Test
    public void desdeServidor_idEstado2_esInactivo() {
        MesaDto dto = dto(100, 4, 6, 1, 2, "2026-08-01T10:00:00Z");

        MesaEntity entidad = MesaMapper.desdeServidor(dto);

        assertFalse(entidad.isActivo());
    }

    @Test
    public void desdeServidor_estadoMesaDesconocido_defaultLibre() {
        MesaDto dto = dto(100, 4, 6, 99, 1, "2026-08-01T10:00:00Z");

        MesaEntity entidad = MesaMapper.desdeServidor(dto);

        assertEquals(EstadoMesa.LIBRE.name(), entidad.getEstadoMesa());
    }

    // ------------------------------------------------------------------ aDominioLista

    @Test
    public void aDominioLista_vacia_devuelveVacia() {
        List<Mesa> resultado = MesaMapper.aDominioLista(Arrays.asList());
        assertTrue(resultado.isEmpty());
    }

    @Test
    public void aDominioLista_conElementos_mapeaTodos() {
        MesaEntity e1 = new MesaEntity();
        e1.setIdLocal(1);
        e1.setNumeroMesa(1);
        e1.setCapacidad(2);
        e1.setEstadoMesa(EstadoMesa.LIBRE.name());
        e1.setActivo(true);
        e1.setEstadoSync(EstadoSync.SINCRONIZADO.name());

        MesaEntity e2 = new MesaEntity();
        e2.setIdLocal(2);
        e2.setNumeroMesa(2);
        e2.setCapacidad(4);
        e2.setEstadoMesa(EstadoMesa.OCUPADA.name());
        e2.setActivo(true);
        e2.setEstadoSync(EstadoSync.SINCRONIZADO.name());

        List<Mesa> resultado = MesaMapper.aDominioLista(Arrays.asList(e1, e2));

        assertEquals(2, resultado.size());
        assertEquals(1, resultado.get(0).getNumeroMesa());
        assertEquals(2, resultado.get(1).getNumeroMesa());
    }

    // ------------------------------------------------------------------ EstadoMesa.porId

    @Test
    public void estadoMesa_porId_existente() {
        assertEquals(EstadoMesa.LIBRE, EstadoMesa.porId(1));
        assertEquals(EstadoMesa.OCUPADA, EstadoMesa.porId(2));
        assertEquals(EstadoMesa.RESERVADA, EstadoMesa.porId(3));
    }

    @Test
    public void estadoMesa_porId_inexistente() {
        assertNull(EstadoMesa.porId(99));
    }

    @Test
    public void estadoMesa_siguiente_cicla() {
        assertEquals(EstadoMesa.OCUPADA, EstadoMesa.LIBRE.siguiente());
        assertEquals(EstadoMesa.RESERVADA, EstadoMesa.OCUPADA.siguiente());
        assertEquals(EstadoMesa.LIBRE, EstadoMesa.RESERVADA.siguiente());
    }
}

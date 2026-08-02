package com.example.proyectofinalrestaurante.data.local.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;
import com.example.proyectofinalrestaurante.data.remote.dto.ClienteDto;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.google.gson.Gson;

import org.junit.Test;

/**
 * El mapeo cliente local ↔ dominio (Plan Fase 2d, E3). Test de JVM pura, mismo patrón que
 * {@link MesaMapperTest}: el {@link ClienteDto} se construye con Gson porque sus campos son
 * privados y solo se pueblan por reflexión al deserializar.
 */
public class ClienteMapperTest {

    @Test
    public void aDominio_mapeaTodosLosCampos() {
        ClienteEntity entidad = unaEntidad();

        Cliente cliente = ClienteMapper.aDominio(entidad);

        assertEquals(3, cliente.getIdLocal());
        assertEquals(Integer.valueOf(7), cliente.getIdServidor());
        assertEquals("Ana", cliente.getNombre());
        assertEquals("Cruz", cliente.getApellido());
        assertEquals("0801199512345", cliente.getIdentidad());
        assertEquals("9988-1122", cliente.getTelefono());
        assertTrue(cliente.isActivo());
        assertEquals(2, cliente.getCantidadPedidos());
        assertEquals(EstadoSync.SINCRONIZADO, cliente.getEstadoSync());
    }

    @Test
    public void aEntidad_derivaIdEstadoDeActivo() {
        Cliente cliente = new Cliente(3, 7, "Ana", "Cruz", "0801199512345", "9988-1122",
                true, 2, "2026-08-01", EstadoSync.PENDIENTE);

        ClienteEntity entidad = ClienteMapper.aEntidad(cliente);

        assertEquals(1, entidad.getIdEstado());
        assertTrue(entidad.isActivo());
        assertEquals("PENDIENTE", entidad.getEstadoSync());
    }

    @Test
    public void aEntidad_clienteDeBaja_derivaIdEstado2() {
        Cliente cliente = new Cliente(3, 7, "Ana", "Cruz", null, null,
                false, 0, "2026-08-01", EstadoSync.SINCRONIZADO);

        ClienteEntity entidad = ClienteMapper.aEntidad(cliente);

        assertEquals(2, entidad.getIdEstado());
        assertFalse(entidad.isActivo());
    }

    @Test
    public void desdeServidor_derivaActivoDeIdEstadoNoDelCampoActivoDeLaVista() {
        ClienteDto dto = dto("{ \"id_cliente\": 7, \"nombre\": \"Ana\", \"apellido\": \"Cruz\", "
                + "\"id_estado\": 1, \"activo\": false, \"cantidad_pedidos\": 0 }");

        ClienteEntity entidad = ClienteMapper.desdeServidor(dto);

        assertTrue(entidad.isActivo());
    }

    @Test
    public void desdeServidor_sinIdentidadNiTelefono_quedanNull() {
        ClienteDto dto = dto("{ \"id_cliente\": 7, \"nombre\": \"Gabriela\", \"apellido\": \"Paz\", "
                + "\"id_estado\": 1, \"cantidad_pedidos\": 0 }");

        ClienteEntity entidad = ClienteMapper.desdeServidor(dto);

        assertNull(entidad.getIdentidad());
        assertNull(entidad.getTelefono());
    }

    @Test
    public void desdeServidor_marcaSincronizadoYConservaLaMarca() {
        ClienteDto dto = dto("{ \"id_cliente\": 7, \"nombre\": \"Ana\", \"apellido\": \"Cruz\", "
                + "\"id_estado\": 1, \"cantidad_pedidos\": 3, "
                + "\"actualizado_en\": \"2026-08-01T12:00:00Z\" }");

        ClienteEntity entidad = ClienteMapper.desdeServidor(dto);

        assertEquals(Integer.valueOf(7), entidad.getIdServidor());
        assertEquals(3, entidad.getCantidadPedidos());
        assertEquals("2026-08-01T12:00:00Z", entidad.getActualizadoEn());
        assertEquals("SINCRONIZADO", entidad.getEstadoSync());
    }

    private static ClienteEntity unaEntidad() {
        ClienteEntity entidad = new ClienteEntity();
        entidad.setIdLocal(3);
        entidad.setIdServidor(7);
        entidad.setNombre("Ana");
        entidad.setApellido("Cruz");
        entidad.setIdentidad("0801199512345");
        entidad.setTelefono("9988-1122");
        entidad.setIdEstado(1);
        entidad.setActivo(true);
        entidad.setCantidadPedidos(2);
        entidad.setEstadoSync("SINCRONIZADO");
        return entidad;
    }

    private static ClienteDto dto(String json) {
        return new Gson().fromJson(json, ClienteDto.class);
    }
}

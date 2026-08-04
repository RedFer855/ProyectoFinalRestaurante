package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.ClienteMapper;
import com.example.proyectofinalrestaurante.data.remote.dto.ClienteDto;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Tests de {@link ClienteMapper} (Fase 2d, E7). Java puro, sin dependencias de Android. */
public class ClienteMapperTest {

    // ------------------------------------------------------------------ aDominio

    @Test
    public void aDominio_mapeaTodosLosCampos() {
        ClienteEntity entidad = new ClienteEntity();
        entidad.setIdLocal(10);
        entidad.setIdServidor(100);
        entidad.setNombre("Ana");
        entidad.setApellido("Cruz");
        entidad.setIdentidad("0801199512345");
        entidad.setTelefono("9988-1122");
        entidad.setActivo(true);
        entidad.setCantidadPedidos(5);
        entidad.setEstadoSync(EstadoSync.SINCRONIZADO.name());

        Cliente cliente = ClienteMapper.aDominio(entidad);

        assertEquals(10, cliente.getIdLocal());
        assertEquals(Integer.valueOf(100), cliente.getIdServidor());
        assertEquals("Ana", cliente.getNombre());
        assertEquals("Cruz", cliente.getApellido());
        assertEquals("0801199512345", cliente.getIdentidad());
        assertEquals("9988-1122", cliente.getTelefono());
        assertTrue(cliente.isActivo());
        assertEquals(5, cliente.getCantidadPedidos());
        assertEquals(EstadoSync.SINCRONIZADO, cliente.getEstadoSync());
    }

    @Test
    public void aDominio_idServidorNull_seMantiene() {
        ClienteEntity entidad = new ClienteEntity();
        entidad.setIdLocal(10);
        entidad.setIdServidor(null);
        entidad.setNombre("Ana");
        entidad.setApellido("Cruz");
        entidad.setIdentidad(null);
        entidad.setTelefono(null);
        entidad.setActivo(true);
        entidad.setCantidadPedidos(0);
        entidad.setEstadoSync(EstadoSync.SINCRONIZAR.name());

        Cliente cliente = ClienteMapper.aDominio(entidad);

        assertNull(cliente.getIdServidor());
        assertNull(cliente.getIdentidad());
        assertNull(cliente.getTelefono());
        assertEquals(EstadoSync.SINCRONIZAR, cliente.getEstadoSync());
    }

    @Test
    public void aDominio_estadoSyncNull_defaultSincronizado() {
        ClienteEntity entidad = new ClienteEntity();
        entidad.setEstadoSync(null);

        Cliente cliente = ClienteMapper.aDominio(entidad);

        assertEquals(EstadoSync.SINCRONIZADO, cliente.getEstadoSync());
    }

    @Test
    public void aDominio_estadoSyncInvalido_defaultSincronizado() {
        ClienteEntity entidad = new ClienteEntity();
        entidad.setEstadoSync("ESTADO_FALSO");

        Cliente cliente = ClienteMapper.aDominio(entidad);

        assertEquals(EstadoSync.SINCRONIZADO, cliente.getEstadoSync());
    }

    // ------------------------------------------------------------------ aEntidad

    @Test
    public void aEntidad_mapeaTodosLosCampos() {
        Cliente cliente = new Cliente(10, 100, "Ana", "Cruz",
                "0801199512345", "9988-1122", true, 5, EstadoSync.SINCRONIZAR);

        ClienteEntity entidad = ClienteMapper.aEntidad(cliente);

        assertEquals(10, entidad.getIdLocal());
        assertEquals(Integer.valueOf(100), entidad.getIdServidor());
        assertEquals("Ana", entidad.getNombre());
        assertEquals("Cruz", entidad.getApellido());
        assertEquals("0801199512345", entidad.getIdentidad());
        assertEquals("9988-1122", entidad.getTelefono());
        assertTrue(entidad.isActivo());
        assertEquals(5, entidad.getCantidadPedidos());
        assertEquals(EstadoSync.SINCRONIZAR.name(), entidad.getEstadoSync());
    }

    // ------------------------------------------------------------------ ida y vuelta

    @Test
    public void idaYVuelta_seConserva() {
        ClienteEntity original = new ClienteEntity();
        original.setIdLocal(10);
        original.setIdServidor(100);
        original.setNombre("Ana");
        original.setApellido("Cruz");
        original.setIdentidad("0801199512345");
        original.setTelefono("9988-1122");
        original.setActivo(true);
        original.setCantidadPedidos(5);
        original.setEstadoSync(EstadoSync.SINCRONIZADO.name());

        Cliente cliente = ClienteMapper.aDominio(original);
        ClienteEntity regreso = ClienteMapper.aEntidad(cliente);

        assertEquals(original.getIdLocal(), regreso.getIdLocal());
        assertEquals(original.getIdServidor(), regreso.getIdServidor());
        assertEquals(original.getNombre(), regreso.getNombre());
        assertEquals(original.getApellido(), regreso.getApellido());
        assertEquals(original.getIdentidad(), regreso.getIdentidad());
        assertEquals(original.getTelefono(), regreso.getTelefono());
        assertEquals(original.isActivo(), regreso.isActivo());
        assertEquals(original.getCantidadPedidos(), regreso.getCantidadPedidos());
        assertEquals(original.getEstadoSync(), regreso.getEstadoSync());
    }

    // ------------------------------------------------------------------ desdeServidor

    private static ClienteDto dto(int idCliente, String nombre, String apellido,
                                   String identidad, String telefono,
                                   boolean activo, int cantidadPedidos,
                                   String actualizadoEn) {
        return new Gson().fromJson(
                "{\"id_cliente\":" + idCliente
                        + ",\"nombre\":\"" + nombre + "\""
                        + ",\"apellido\":\"" + apellido + "\""
                        + ",\"identidad\":\"" + identidad + "\""
                        + ",\"telefono\":\"" + telefono + "\""
                        + ",\"activo\":" + activo
                        + ",\"cantidad_pedidos\":" + cantidadPedidos
                        + ",\"actualizado_en\":\"" + actualizadoEn + "\"}",
                ClienteDto.class);
    }

    @Test
    public void desdeServidor_mapeaCamposClave() {
        ClienteDto dto = dto(100, "Ana", "Cruz", "0801199512345", "9988-1122",
                true, 5, "2026-08-01T10:00:00Z");

        ClienteEntity entidad = ClienteMapper.desdeServidor(dto);

        assertEquals(Integer.valueOf(100), entidad.getIdServidor());
        assertEquals("Ana", entidad.getNombre());
        assertEquals("Cruz", entidad.getApellido());
        assertEquals("0801199512345", entidad.getIdentidad());
        assertEquals("9988-1122", entidad.getTelefono());
        assertTrue(entidad.isActivo());
        assertEquals(5, entidad.getCantidadPedidos());
        assertEquals(EstadoSync.SINCRONIZADO.name(), entidad.getEstadoSync());
        assertEquals("2026-08-01T10:00:00Z", entidad.getActualizadoEn());
    }

    @Test
    public void desdeServidor_inactivo_seMantiene() {
        ClienteDto dto = dto(100, "Ana", "Cruz", null, null,
                false, 0, "2026-08-01T10:00:00Z");

        ClienteEntity entidad = ClienteMapper.desdeServidor(dto);

        assertFalse(entidad.isActivo());
    }

    // ------------------------------------------------------------------ aDominioLista

    @Test
    public void aDominioLista_vacia_devuelveVacia() {
        List<Cliente> resultado = ClienteMapper.aDominioLista(Arrays.asList());
        assertTrue(resultado.isEmpty());
    }

    @Test
    public void aDominioLista_conElementos_mapeaTodos() {
        ClienteEntity e1 = new ClienteEntity();
        e1.setIdLocal(1);
        e1.setNombre("Ana");
        e1.setApellido("Cruz");
        e1.setActivo(true);
        e1.setEstadoSync(EstadoSync.SINCRONIZADO.name());

        ClienteEntity e2 = new ClienteEntity();
        e2.setIdLocal(2);
        e2.setNombre("Luis");
        e2.setApellido("Medina");
        e2.setActivo(true);
        e2.setEstadoSync(EstadoSync.SINCRONIZADO.name());

        List<Cliente> resultado = ClienteMapper.aDominioLista(Arrays.asList(e1, e2));

        assertEquals(2, resultado.size());
        assertEquals("Ana", resultado.get(0).getNombre());
        assertEquals("Luis", resultado.get(1).getNombre());
    }

    // ------------------------------------------------------------------ aEstadoSync

    @Test
    public void aEstadoSync_null_devuelveSincronizado() {
        assertEquals(EstadoSync.SINCRONIZADO, ClienteMapper.aEstadoSync(null));
    }

    @Test
    public void aEstadoSync_invalido_devuelveSincronizado() {
        assertEquals(EstadoSync.SINCRONIZADO, ClienteMapper.aEstadoSync("INVALIDO"));
    }

    @Test
    public void aEstadoSync_sincronizar_devuelveSincronizar() {
        assertEquals(EstadoSync.SINCRONIZAR, ClienteMapper.aEstadoSync("SINCRONIZAR"));
    }
}

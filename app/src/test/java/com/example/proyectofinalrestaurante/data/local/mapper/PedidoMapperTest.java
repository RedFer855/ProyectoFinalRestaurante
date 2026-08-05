package com.example.proyectofinalrestaurante.data.local.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.proyectofinalrestaurante.data.local.entity.PedidoEntity;
import com.example.proyectofinalrestaurante.data.remote.dto.PedidoDto;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Pedido;
import com.google.gson.Gson;

import org.junit.Test;

/**
 * El mapeo pedido local ↔ dominio (Plan Fase 3, E3). Test de JVM pura, mismo patrón que
 * {@link MesaMapperTest}: el {@link PedidoDto} se construye con Gson porque sus campos son
 * privados y solo se pueblan por reflexión al deserializar.
 */
public class PedidoMapperTest {

    private static final String JSON = "{\"id_pedido\":1042,"
            + "\"fecha\":\"2026-08-04T12:05:00+00:00\","
            + "\"id_estado_pedido\":2,\"estado_pedido\":\"En preparación\","
            + "\"id_estado\":1,\"id_mesa\":4,\"numero_mesa\":4,"
            + "\"id_cliente\":1,\"cliente\":\"Ana Cruz\","
            + "\"id_tipo_pedido\":1,\"tipo_pedido\":\"En mesa\",\"id_usuario\":2,"
            + "\"id_auth_usuario\":\"uuid-mesero\",\"total\":380.0,"
            + "\"cantidad_items\":3,\"actualizado_en\":\"2026-08-04T12:05:00+00:00\"}";

    @Test
    public void desdeServidor_mapeaTodosLosCampos() {
        PedidoDto dto = new Gson().fromJson(JSON, PedidoDto.class);

        PedidoEntity entidad = PedidoMapper.desdeServidor(dto);

        assertEquals(Integer.valueOf(1042), entidad.getIdServidor());
        assertEquals("2026-08-04T12:05:00+00:00", entidad.getFecha());
        assertEquals(2, entidad.getIdEstadoPedido());
        assertEquals(Integer.valueOf(4), entidad.getNumeroMesa());
        assertEquals("Ana Cruz", entidad.getCliente());
        assertEquals(380.0, entidad.getTotal(), 0.001);
        assertEquals(3, entidad.getCantidadItems());
        assertEquals("uuid-mesero", entidad.getIdAuthUsuario());
        assertEquals("SINCRONIZADO", entidad.getEstadoSync());
    }

    @Test
    public void aDominio_traduceElEstadoYLaMarcaDeSync() {
        PedidoEntity entidad = entidad(1, 1042, 2, "SINCRONIZADO");

        Pedido pedido = PedidoMapper.aDominio(entidad);

        assertEquals(1L, pedido.getIdLocal());
        assertEquals(Integer.valueOf(1042), pedido.getIdServidor());
        assertEquals(EstadoPedido.EN_PREPARACION, pedido.getEstado());
        assertEquals(EstadoSync.SINCRONIZADO, pedido.getEstadoSync());
    }

    @Test
    public void aEntidad_estadoConocido_loPersisteConSuId() {
        Pedido pedido = new Pedido(1, 1042, "2026-08-04T12:05:00+00:00",
                EstadoPedido.LISTO, 4, "Ana Cruz", 380.0, 3, "uuid-mesero",
                "2026-08-04T12:05:00+00:00", EstadoSync.PENDIENTE);

        PedidoEntity entidad = PedidoMapper.aEntidad(pedido);

        assertEquals(3, entidad.getIdEstadoPedido());
        assertEquals("PENDIENTE", entidad.getEstadoSync());
    }

    @Test
    public void aEntidad_estadoDesconocido_usaElCentinelaYRedondeaAUnknown() {
        Pedido pedido = new Pedido(1, 1042, "2026-08-04T12:05:00+00:00", null,
                4, "Ana Cruz", 380.0, 3, null, null, EstadoSync.SINCRONIZADO);

        PedidoEntity entidad = PedidoMapper.aEntidad(pedido);

        assertEquals(0, entidad.getIdEstadoPedido());
        Pedido redondeado = PedidoMapper.aDominio(entidad);
        assertNull(redondeado.getEstado());
    }

    @Test
    public void aEntidad_estadoServidorDesconocido_nuncaMapeaAConocidoPorDefecto() {
        PedidoDto dto = new Gson().fromJson(
                JSON.replace("\"id_estado_pedido\":2", "\"id_estado_pedido\":99"),
                PedidoDto.class);

        PedidoEntity entidad = PedidoMapper.desdeServidor(dto);

        assertEquals(99, entidad.getIdEstadoPedido());
        assertNull(PedidoMapper.aDominio(entidad).getEstado());
    }

    private static PedidoEntity entidad(int idLocal, int idServidor, int idEstado,
                                        String estadoSync) {
        PedidoEntity e = new PedidoEntity();
        e.setIdLocal(idLocal);
        e.setIdServidor(idServidor);
        e.setFecha("2026-08-04T12:05:00+00:00");
        e.setIdEstadoPedido(idEstado);
        e.setNumeroMesa(4);
        e.setCliente("Ana Cruz");
        e.setTotal(380.0);
        e.setCantidadItems(3);
        e.setIdAuthUsuario("uuid-mesero");
        e.setActualizadoEn("2026-08-04T12:05:00+00:00");
        e.setEstadoSync(estadoSync);
        return e;
    }
}
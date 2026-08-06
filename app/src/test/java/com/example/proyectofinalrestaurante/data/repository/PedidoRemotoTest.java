package com.example.proyectofinalrestaurante.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.remote.SupabasePedidoApi;
import com.example.proyectofinalrestaurante.data.remote.dto.AvanzarEstadoPedidoDto;

import com.example.proyectofinalrestaurante.data.remote.dto.PedidoDto;
import com.google.gson.Gson;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Tests de {@link PedidoRemoto} (Plan Fase 3, E4): el delta paginado sobre
 * {@code vista_pedidos} y el RPC {@code avanzar_estado_pedido}.
 */
public class PedidoRemotoTest {

    private final FakePedidoApi api = new FakePedidoApi();

    private PedidoRemoto remoto(String token) {
        return new PedidoRemoto(api, () -> token);
    }

    private static PedidoDto pedido(int id, String marca, int idEstado) {
        return new Gson().fromJson(
                "{\"id_pedido\":" + id + ",\"fecha\":\"2026-08-04T12:00:00.000+00:00\","
                        + "\"id_estado_pedido\":" + idEstado + ",\"id_estado\":1,"
                        + "\"id_usuario\":1,\"total\":380.0,\"cantidad_items\":3,"
                        + "\"actualizado_en\":\"" + marca + "\"}",
                PedidoDto.class);
    }

    // ------------------------------------------------------------------ delta

    @Test
    public void listarDesde_sinMarca_noPoneFiltroYRespetaElOffset() {
        api.respuestaListar = FakeCall.deRespuesta(Response.success(
                List.of(pedido(1, "2026-08-04T12:00:00.000+00:00", 1))));

        ResultadoRed<List<PedidoDto>> resultado = remoto("token").listarPedidosDesde(null, 50);

        assertTrue(resultado.isExitoso());
        assertNull(api.ultimoFiltro);
        assertEquals(50, api.ultimoOffset);
        assertEquals(1, resultado.getValor().size());
    }

    @Test
    public void listarDesde_conMarca_poneElFiltroGt() {
        api.respuestaListar = FakeCall.deRespuesta(Response.success(List.of()));

        ResultadoRed<List<PedidoDto>> resultado = remoto("token")
                .listarPedidosDesde("2026-08-04T12:00:00.000+00:00", 0);

        assertTrue(resultado.isExitoso());
        assertEquals("gt.2026-08-04T12:00:00.000+00:00", api.ultimoFiltro);
    }

    @Test
    public void listarDesde_sinToken_es401() {
        ResultadoRed<List<PedidoDto>> resultado = remoto(null).listarPedidosDesde("marca", 0);

        assertFalse(resultado.isExitoso());
        assertEquals(401, resultado.getCodigoHttp());
        assertEquals(PedidoRemoto.SIN_SESION, resultado.getMensaje());
    }

    @Test
    public void listarDesde_fallaRed_traeMensajeDeConexion() {
        api.respuestaListar = FakeCall.deFallo(new IOException("sin red"));

        ResultadoRed<List<PedidoDto>> resultado = remoto("token")
                .listarPedidosDesde("marca", 0);

        assertFalse(resultado.isExitoso());
        assertEquals(ClasificadorDeError.SIN_CODIGO, resultado.getCodigoHttp());
        assertEquals(PedidoRemoto.SIN_CONEXION, resultado.getMensaje());
    }

    @Test
    public void listarDesde_fallaServidor_traeElCodigoHttp() {
        api.respuestaListar = FakeCall.deRespuesta(Response.error(500,
                ResponseBody.create(MediaType.get("application/json"), "{}")));

        ResultadoRed<List<PedidoDto>> resultado = remoto("token")
                .listarPedidosDesde("marca", 0);

        assertFalse(resultado.isExitoso());
        assertEquals(500, resultado.getCodigoHttp());
    }

    // ------------------------------------------------------------------ RPC

    @Test
    public void avanzarEstado_exitoso_devuelveExito() {
        api.respuestaAvanzar = FakeCall.deRespuesta(Response.success(null));

        ResultadoRed<Void> resultado = remoto("token").avanzarEstado(1042, 2);

        assertTrue(resultado.isExitoso());
        assertEquals(1042, api.ultimoCuerpo.getIdServidor());
        assertEquals(2, api.ultimoCuerpo.getIdEstadoPedido());
    }

    @Test
    public void avanzarEstado_sinToken_es401() {
        ResultadoRed<Void> resultado = remoto(null).avanzarEstado(1042, 2);

        assertFalse(resultado.isExitoso());
        assertEquals(401, resultado.getCodigoHttp());
    }

    @Test
    public void avanzarEstado_errorDelRpc_devuelveElMensajeDelCuerpo() {
        api.respuestaAvanzar = FakeCall.deRespuesta(Response.error(400,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"Ese pedido ya está cerrado.\"}")));

        ResultadoRed<Void> resultado = remoto("token").avanzarEstado(1042, 4);

        assertFalse(resultado.isExitoso());
        assertEquals(400, resultado.getCodigoHttp());
        assertEquals("Ese pedido ya está cerrado.", resultado.getMensaje());
    }

    // ------------------------------------------------------------------ fake

    private static final class FakePedidoApi implements SupabasePedidoApi {

        Call<List<PedidoDto>> respuestaListar =
                FakeCall.deRespuesta(Response.success(List.of()));
        Call<Void> respuestaAvanzar = FakeCall.deRespuesta(Response.success(null));
        Call<Integer> respuestaCrear =
                FakeCall.deRespuesta(Response.success(0));

        String ultimoFiltro;
        int ultimoOffset;
        AvanzarEstadoPedidoDto ultimoCuerpo;
        String ultimoPayloadCrear;

        @Override
        public Call<List<PedidoDto>> listarPedidosDesde(String bearerToken, String select,
                                                        String actualizadoEnMayorQue,
                                                        String orden, int limite,
                                                        int desplazamiento) {
            ultimoFiltro = actualizadoEnMayorQue;
            ultimoOffset = desplazamiento;
            return respuestaListar;
        }

        @Override
        public Call<Void> avanzarEstado(String bearerToken, AvanzarEstadoPedidoDto cuerpo) {
            ultimoCuerpo = cuerpo;
            return respuestaAvanzar;
        }

        @Override
        public Call<Integer> crearPedido(String bearerToken, RequestBody cuerpo) {
            try {
                Buffer buffer = new Buffer();
                cuerpo.writeTo(buffer);
                ultimoPayloadCrear = buffer.readUtf8();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            return respuestaCrear;
        }
    }
}
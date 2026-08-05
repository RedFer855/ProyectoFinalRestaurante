package com.example.proyectofinalrestaurante.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.remote.SupabaseClienteApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.BuscarOCrearClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearClienteDto;
import com.google.gson.Gson;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Tests de {@link ClienteRemoto} (Plan Fase 2d, E7). Mismo patrón que {@code MesaRemotoTest}:
 * fake mínimo de {@link SupabaseClienteApi} con {@link FakeCall}, sin Mockito (P-020).
 */
public class ClienteRemotoTest {

    private static final String JSON_CLIENTE =
            "{\"id_cliente\":7,\"nombre\":\"Ana\",\"apellido\":\"Cruz\","
                    + "\"identidad\":\"0801199512345\",\"telefono\":\"9988-1122\","
                    + "\"id_estado\":1,\"activo\":true,\"cantidad_pedidos\":0,"
                    + "\"actualizado_en\":\"2026-08-01 10:00:00+00\"}";

    private final Gson gson = new Gson();

    // ------------------------------------------------------------------ sesión

    @Test
    public void sinSesion_devuelve401SinLlamarALaRed() {
        FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
        ClienteRemoto remoto = new ClienteRemoto(api, () -> null);

        ResultadoRed<List<ClienteDto>> resultado = remoto.listarClientesDesde(null, 0);

        assertFalse(resultado.isExitoso());
        assertEquals(401, resultado.getCodigoHttp());
        assertEquals("Tu sesión venció. Volvé a iniciar sesión.", resultado.getMensaje());
        assertNull("no debería haberse tocado la red", api.ultimoFiltro);
    }

    // ------------------------------------------------------------------ delta

    @Test
    public void listarClientesDesde_conMarca_mandaElFiltroGtYOrdenaPorLaMarcaConDesempatePorId() {
        FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista()));
        ClienteRemoto remoto = new ClienteRemoto(api, () -> "token");

        remoto.listarClientesDesde("2026-08-01 09:00:00+00", 0);

        assertEquals("gt.2026-08-01 09:00:00+00", api.ultimoFiltro);
        assertEquals("actualizado_en.asc,id_cliente.asc", api.ultimoOrden);
        assertEquals(ClienteRemoto.LIMITE_DELTA, api.ultimoLimite);
    }

    @Test
    public void listarClientesDesde_propagaElOffset() {
        FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista()));
        ClienteRemoto remoto = new ClienteRemoto(api, () -> "token");

        remoto.listarClientesDesde("2026-08-01 09:00:00+00", 50);

        assertEquals(50, api.ultimoOffset);
    }

    @Test
    public void listarClientesDesde_sinConexion_noTraeCodigoHttp() {
        FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
        api.respuestaListarDesde = FakeCall.deFallo(new IOException("timeout"));
        ClienteRemoto remoto = new ClienteRemoto(api, () -> "token");

        ResultadoRed<List<ClienteDto>> resultado = remoto.listarClientesDesde(null, 0);

        assertFalse(resultado.isExitoso());
        assertEquals(ClasificadorDeError.SIN_CODIGO, resultado.getCodigoHttp());
        assertEquals("Sin conexión al servidor. Intentá de nuevo.", resultado.getMensaje());
    }

    // ------------------------------------------------------------------ crear

    @Test
    public void crear_exitoso_devuelveElClienteCreado() {
        FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
        api.respuestaCrear = FakeCall.deRespuesta(Response.success(unaLista()));
        ClienteRemoto remoto = new ClienteRemoto(api, () -> "token");

        ResultadoRed<ClienteDto> resultado = remoto.crear("Ana", "Cruz", "0801199512345", null);

        assertTrue(resultado.isExitoso());
        assertEquals(7, resultado.getValor().getIdCliente());
    }

    @Test
    public void crear_identidadDuplicada_propagaElMensajeDePostgrest() {
        FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
        api.respuestaCrear = FakeCall.deRespuesta(Response.error(409,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"Ya existe un cliente con esa identidad\"}")));
        ClienteRemoto remoto = new ClienteRemoto(api, () -> "token");

        ResultadoRed<ClienteDto> resultado = remoto.crear("Ana", "Cruz", "0801199512345", null);

        assertFalse(resultado.isExitoso());
        assertEquals(409, resultado.getCodigoHttp());
        assertEquals("Ya existe un cliente con esa identidad", resultado.getMensaje());
        assertFalse(ClasificadorDeError.esTransitorio(resultado.getCodigoHttp()));
    }

    // ------------------------------------------------------------------ escrituras

    @Test
    public void actualizarDatos_mandaElFiltroEqObligatorio() {
        FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
        ClienteRemoto remoto = new ClienteRemoto(api, () -> "token");

        ResultadoRed<Void> resultado = remoto.actualizarDatos(7, "Ana", "Cruz", null, null);

        assertTrue(resultado.isExitoso());
        assertEquals("eq.7", api.ultimoFiltro);
    }

    @Test
    public void cambiarBaja_deBaja_mandaIdEstado2() {
        FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
        ClienteRemoto remoto = new ClienteRemoto(api, () -> "token");

        remoto.cambiarBaja(7, false);

        assertTrue(gson.toJson(api.ultimoCuerpo).contains("\"id_estado\":" + ClienteRemoto.ID_ESTADO_BAJA));
    }

    @Test
    public void borrar_mandaElFiltroEq() {
        FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
        ClienteRemoto remoto = new ClienteRemoto(api, () -> "token");

        ResultadoRed<Void> resultado = remoto.borrar(7);

        assertTrue(resultado.isExitoso());
        assertEquals("eq.7", api.ultimoFiltroBorrar);
    }

    @Test
    public void borrar_conPedidos_propagaElMensajeDelTrigger() {
        FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
        api.respuestaBorrar = FakeCall.deRespuesta(Response.error(409,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"No se puede borrar un cliente que ya tiene pedidos. "
                                + "Dalo de baja en su lugar.\"}")));
        ClienteRemoto remoto = new ClienteRemoto(api, () -> "token");

        ResultadoRed<Void> resultado = remoto.borrar(7);

        assertFalse(resultado.isExitoso());
        assertTrue(resultado.getMensaje().contains("Dalo de baja"));
    }

    // ------------------------------------------------------------------ RPC buscar_o_crear_cliente

    @Test
    public void buscarOCrear_exitoso_devuelveElIdDelCliente() {
        FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
        api.respuestaBuscarOCrear = FakeCall.deRespuesta(Response.success(5));
        ClienteRemoto remoto = new ClienteRemoto(api, () -> "token");

        ResultadoRed<Integer> resultado = remoto.buscarOCrear("Ana", "Cruz", "0801-1995-1", null);

        assertTrue(resultado.isExitoso());
        assertEquals(Integer.valueOf(5), resultado.getValor());
    }

    @Test
    public void buscarOCrear_sinNombreNiApellido_propagaElMensajeDelRpc() {
        FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
        api.respuestaBuscarOCrear = FakeCall.deRespuesta(Response.error(400,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"El nombre y el apellido del cliente son obligatorios.\"}")));
        ClienteRemoto remoto = new ClienteRemoto(api, () -> "token");

        ResultadoRed<Integer> resultado = remoto.buscarOCrear("", "Cruz", null, null);

        assertFalse(resultado.isExitoso());
        assertEquals("El nombre y el apellido del cliente son obligatorios.", resultado.getMensaje());
    }

    // ------------------------------------------------------------------ helpers

    private List<ClienteDto> unaLista() {
        return Arrays.asList(gson.fromJson(JSON_CLIENTE, ClienteDto.class));
    }

    /** Fake mínimo: solo implementa lo que {@link ClienteRemoto} usa, y anota lo enviado. */
    private static final class FakeSupabaseClienteApi implements SupabaseClienteApi {

        Call<List<ClienteDto>> respuestaListarDesde;
        Call<List<ClienteDto>> respuestaCrear;
        Call<Void> respuestaActualizarCliente = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaBorrar = FakeCall.deRespuesta(Response.success(null));
        Call<Integer> respuestaBuscarOCrear = FakeCall.deRespuesta(Response.success(1));

        String ultimoFiltro;
        String ultimoOrden;
        int ultimoLimite;
        int ultimoOffset;
        ActualizarClienteDto ultimoCuerpo;
        String ultimoFiltroBorrar;

        @Override
        public Call<List<ClienteDto>> listarClientesDesde(String bearerToken, String select,
                                                           String actualizadoEnMayorQue,
                                                           String orden, int limite,
                                                           int desplazamiento) {
            ultimoFiltro = actualizadoEnMayorQue;
            ultimoOrden = orden;
            ultimoLimite = limite;
            ultimoOffset = desplazamiento;
            return respuestaListarDesde;
        }

        @Override
        public Call<List<ClienteDto>> crearCliente(String bearerToken, CrearClienteDto cuerpo) {
            return respuestaCrear;
        }

        @Override
        public Call<Void> actualizarCliente(String bearerToken, String idClienteIgualA,
                                            ActualizarClienteDto cuerpo) {
            ultimoFiltro = idClienteIgualA;
            ultimoCuerpo = cuerpo;
            return respuestaActualizarCliente;
        }

        @Override
        public Call<Void> borrarCliente(String bearerToken, String idClienteIgualA) {
            ultimoFiltroBorrar = idClienteIgualA;
            return respuestaBorrar;
        }

        @Override
        public Call<Integer> buscarOCrearCliente(String bearerToken, BuscarOCrearClienteDto cuerpo) {
            return respuestaBuscarOCrear;
        }
    }
}

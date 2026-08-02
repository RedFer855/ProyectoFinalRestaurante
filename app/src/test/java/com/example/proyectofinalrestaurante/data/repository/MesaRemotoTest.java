package com.example.proyectofinalrestaurante.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.remote.SupabaseMesaApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CambiarEstadoMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.MesaDto;
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
 * Tests de {@link MesaRemoto} (Plan Fase 2c, E7). Mismo patrón que {@code EmpleadoRemotoTest}:
 * fake mínimo de {@link SupabaseMesaApi} con {@link FakeCall}, sin Mockito (P-020).
 */
public class MesaRemotoTest {

    private static final String JSON_MESA =
            "{\"id_mesa\":7,\"numero_mesa\":4,\"capacidad\":6,\"ubicacion\":\"Patio\","
                    + "\"id_estado_mesa\":1,\"estado_mesa\":\"Libre\",\"id_estado\":1,"
                    + "\"activo\":true,\"actualizado_en\":\"2026-08-01 10:00:00+00\"}";

    private final Gson gson = new Gson();

    // ------------------------------------------------------------------ sesión

    @Test
    public void sinSesion_devuelve401SinLlamarALaRed() {
        FakeSupabaseMesaApi api = new FakeSupabaseMesaApi();
        MesaRemoto remoto = new MesaRemoto(api, () -> null);

        ResultadoRed<List<MesaDto>> resultado = remoto.listarMesasDesde(null);

        assertFalse(resultado.isExitoso());
        assertEquals(401, resultado.getCodigoHttp());
        assertEquals("Tu sesión venció. Volvé a iniciar sesión.", resultado.getMensaje());
        assertNull("no debería haberse tocado la red", api.ultimoFiltro);
    }

    // ------------------------------------------------------------------ delta

    @Test
    public void listarMesasDesde_conMarca_mandaElFiltroGtYOrdenaPorLaMarca() {
        FakeSupabaseMesaApi api = new FakeSupabaseMesaApi();
        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista()));
        MesaRemoto remoto = new MesaRemoto(api, () -> "token");

        remoto.listarMesasDesde("2026-08-01 09:00:00+00");

        assertEquals("gt.2026-08-01 09:00:00+00", api.ultimoFiltro);
        assertEquals("actualizado_en.asc", api.ultimoOrden);
        assertEquals(MesaRemoto.LIMITE_DELTA, api.ultimoLimite);
    }

    @Test
    public void listarMesasDesde_sinMarca_noMandaFiltro() {
        FakeSupabaseMesaApi api = new FakeSupabaseMesaApi();
        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista()));
        MesaRemoto remoto = new MesaRemoto(api, () -> "token");

        remoto.listarMesasDesde(null);

        assertNull(api.ultimoFiltro);
    }

    @Test
    public void listarMesasDesde_sinConexion_noTraeCodigoHttp() {
        FakeSupabaseMesaApi api = new FakeSupabaseMesaApi();
        api.respuestaListarDesde = FakeCall.deFallo(new IOException("timeout"));
        MesaRemoto remoto = new MesaRemoto(api, () -> "token");

        ResultadoRed<List<MesaDto>> resultado = remoto.listarMesasDesde(null);

        assertFalse(resultado.isExitoso());
        assertEquals(ClasificadorDeError.SIN_CODIGO, resultado.getCodigoHttp());
        assertEquals("Sin conexión al servidor. Intentá de nuevo.", resultado.getMensaje());
    }

    // ------------------------------------------------------------------ crear

    @Test
    public void crear_exitoso_devuelveLaFilaCreada() {
        FakeSupabaseMesaApi api = new FakeSupabaseMesaApi();
        api.respuestaCrear = FakeCall.deRespuesta(Response.success(unaLista()));
        MesaRemoto remoto = new MesaRemoto(api, () -> "token");

        ResultadoRed<MesaDto> resultado = remoto.crear(4, 6, "Patio");

        assertTrue(resultado.isExitoso());
        assertEquals(7, resultado.getValor().getIdMesa());
    }

    @Test
    public void crear_errorDelServidor_propagaElMensajeDePostgrest() {
        FakeSupabaseMesaApi api = new FakeSupabaseMesaApi();
        api.respuestaCrear = FakeCall.deRespuesta(Response.error(409,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"Ya existe una mesa con ese número\"}")));
        MesaRemoto remoto = new MesaRemoto(api, () -> "token");

        ResultadoRed<MesaDto> resultado = remoto.crear(4, 6, "Patio");

        assertFalse(resultado.isExitoso());
        assertEquals(409, resultado.getCodigoHttp());
        assertEquals("Ya existe una mesa con ese número", resultado.getMensaje());
        assertFalse(ClasificadorDeError.esTransitorio(resultado.getCodigoHttp()));
    }

    // ------------------------------------------------------------------ escrituras

    @Test
    public void actualizarDatos_mandaElFiltroEqObligatorio() {
        FakeSupabaseMesaApi api = new FakeSupabaseMesaApi();
        MesaRemoto remoto = new MesaRemoto(api, () -> "token");

        ResultadoRed<Void> resultado = remoto.actualizarDatos(7, 4, 6, "Patio");

        assertTrue(resultado.isExitoso());
        // Un PATCH sin filtro en PostgREST actualiza TODAS las filas de la tabla.
        assertEquals("eq.7", api.ultimoFiltro);
    }

    @Test
    public void cambiarBaja_activo_mandaIdEstado1() {
        FakeSupabaseMesaApi api = new FakeSupabaseMesaApi();
        MesaRemoto remoto = new MesaRemoto(api, () -> "token");

        remoto.cambiarBaja(7, true);

        // Los campos de ActualizarMesaDto son privados (solo Gson los toca); se verifica el
        // cuerpo serializado en vez de agregar getters que el DTO no necesita en producción.
        assertTrue(gson.toJson(api.ultimoCuerpo).contains("\"id_estado\":" + MesaRemoto.ID_ESTADO_ACTIVO));
    }

    @Test
    public void cambiarBaja_deBaja_mandaIdEstado2() {
        FakeSupabaseMesaApi api = new FakeSupabaseMesaApi();
        MesaRemoto remoto = new MesaRemoto(api, () -> "token");

        remoto.cambiarBaja(7, false);

        assertTrue(gson.toJson(api.ultimoCuerpo).contains("\"id_estado\":" + MesaRemoto.ID_ESTADO_BAJA));
    }

    // ------------------------------------------------------------------ RPC cambiar_estado_mesa

    @Test
    public void cambiarEstado_exitoso_esUn204SinCuerpo() {
        FakeSupabaseMesaApi api = new FakeSupabaseMesaApi();
        MesaRemoto remoto = new MesaRemoto(api, () -> "token");

        ResultadoRed<Void> resultado = remoto.cambiarEstado(7, 2);

        assertTrue(resultado.isExitoso());
        String cuerpo = gson.toJson(api.ultimoRpc);
        assertTrue(cuerpo.contains("\"p_id_mesa\":7"));
        assertTrue(cuerpo.contains("\"p_id_estado_mesa\":2"));
    }

    @Test
    public void cambiarEstado_rolSinPermiso_propagaElMensajeDelRpc() {
        FakeSupabaseMesaApi api = new FakeSupabaseMesaApi();
        api.respuestaCambiarEstado = FakeCall.deRespuesta(Response.error(403,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"No tenés permiso para cambiar el estado de una mesa.\"}")));
        MesaRemoto remoto = new MesaRemoto(api, () -> "token");

        ResultadoRed<Void> resultado = remoto.cambiarEstado(7, 2);

        assertFalse(resultado.isExitoso());
        assertEquals("No tenés permiso para cambiar el estado de una mesa.", resultado.getMensaje());
    }

    // ------------------------------------------------------------------ helpers

    private List<MesaDto> unaLista() {
        return Arrays.asList(gson.fromJson(JSON_MESA, MesaDto.class));
    }

    /** Fake mínimo: solo implementa lo que {@link MesaRemoto} usa, y anota lo enviado. */
    private static final class FakeSupabaseMesaApi implements SupabaseMesaApi {

        Call<List<MesaDto>> respuestaListarDesde;
        Call<List<MesaDto>> respuestaCrear;
        Call<Void> respuestaActualizarMesa = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaCambiarEstado = FakeCall.deRespuesta(Response.success(null));

        String ultimoFiltro;
        String ultimoOrden;
        int ultimoLimite;
        ActualizarMesaDto ultimoCuerpo;
        CambiarEstadoMesaDto ultimoRpc;

        @Override
        public Call<List<MesaDto>> listarMesasDesde(String bearerToken, String select,
                                                    String actualizadoEnMayorQue, String orden,
                                                    int limite) {
            ultimoFiltro = actualizadoEnMayorQue;
            ultimoOrden = orden;
            ultimoLimite = limite;
            return respuestaListarDesde;
        }

        @Override
        public Call<List<MesaDto>> crearMesa(String bearerToken, CrearMesaDto cuerpo) {
            return respuestaCrear;
        }

        @Override
        public Call<Void> actualizarMesa(String bearerToken, String idMesaIgualA,
                                         ActualizarMesaDto cuerpo) {
            ultimoFiltro = idMesaIgualA;
            ultimoCuerpo = cuerpo;
            return respuestaActualizarMesa;
        }

        @Override
        public Call<Void> cambiarBajaMesa(String bearerToken, String idMesaIgualA,
                                          ActualizarMesaDto cuerpo) {
            ultimoFiltro = idMesaIgualA;
            ultimoCuerpo = cuerpo;
            return respuestaActualizarMesa;
        }

        @Override
        public Call<Void> cambiarEstadoMesa(String bearerToken, CambiarEstadoMesaDto cuerpo) {
            ultimoRpc = cuerpo;
            return respuestaCambiarEstado;
        }
    }
}

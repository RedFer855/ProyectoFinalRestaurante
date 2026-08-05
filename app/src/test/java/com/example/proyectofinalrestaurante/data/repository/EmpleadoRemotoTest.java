package com.example.proyectofinalrestaurante.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.remote.SupabaseEmpleadoApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarEmpleadoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarPerfilDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoResponseDto;
import com.example.proyectofinalrestaurante.data.remote.dto.EmpleadoDto;
import com.example.proyectofinalrestaurante.domain.model.NuevoEmpleado;
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
 * Tests de {@link EmpleadoRemoto}, la cara remota del módulo Empleados tras la migración a
 * offline-first. Hereda la cobertura del viejo {@code SupabaseEmpleadoRepositoryTest} (deuda
 * del Plan Fase 1d) y suma lo que trajo la migración: el <b>código HTTP</b> en el resultado
 * —de eso depende que el outbox reintente o descarte— y el <b>sync delta</b>.
 */
public class EmpleadoRemotoTest {

    private static final String JSON_EMPLEADO =
            "{\"id_empleado\":1,\"nombres\":\"Marta\",\"apellidos\":\"Zelaya\","
                    + "\"identidad\":\"0801\",\"telefono\":\"9999-0000\","
                    + "\"correo\":\"marta@restaurante.hn\",\"id_usuario\":1,"
                    + "\"apodo_usuario\":\"mzelaya\",\"id_auth_user\":\"uuid-1\","
                    + "\"rol\":\"mesero\",\"activo\":true,"
                    + "\"actualizado_en\":\"2026-08-01 10:00:00+00\"}";

    private final Gson gson = new Gson();

    // ------------------------------------------------------------------ sesión

    @Test
    public void sinSesion_devuelve401SinLlamarALaRed() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        EmpleadoRemoto remoto = new EmpleadoRemoto(api, () -> null);

        ResultadoRed<List<EmpleadoDto>> resultado = remoto.listarDesde(null, 0);

        assertFalse(resultado.isExitoso());
        assertEquals(401, resultado.getCodigoHttp());
        assertEquals("Tu sesión venció. Volvé a iniciar sesión.", resultado.getMensaje());
        assertNull("no debería haberse tocado la red", api.ultimoFiltro);
    }

    // ------------------------------------------------------------------ delta

    @Test
    public void listarDesde_conMarca_mandaElFiltroGtYOrdenaPorLaMarcaConDesempatePorId() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista()));
        EmpleadoRemoto remoto = new EmpleadoRemoto(api, () -> "token");

        remoto.listarDesde("2026-08-01 09:00:00+00", 0);

        // PostgREST espera el operador en el valor: `gt.<marca>`.
        assertEquals("gt.2026-08-01 09:00:00+00", api.ultimoFiltro);
        // Sin orden ascendente por la marca (con desempate por id) la paginación se rompe.
        assertEquals("actualizado_en.asc,id_empleado.asc", api.ultimoOrden);
        assertEquals(EmpleadoRemoto.LIMITE_DELTA, api.ultimoLimite);
    }

    @Test
    public void listarDesde_propagaElOffset() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista()));
        EmpleadoRemoto remoto = new EmpleadoRemoto(api, () -> "token");

        remoto.listarDesde("2026-08-01 09:00:00+00", 50);

        assertEquals(50, api.ultimoOffset);
    }

    @Test
    public void listarDesde_sinMarca_noMandaFiltroYBajaTodo() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista()));
        EmpleadoRemoto remoto = new EmpleadoRemoto(api, () -> "token");

        remoto.listarDesde(null, 0);

        // Retrofit omite el query cuando el valor es null: la primera sincronización trae
        // la tabla entera, que es justo lo que hace falta.
        assertNull(api.ultimoFiltro);
    }

    @Test
    public void listarDesde_exitoso_traeLaMarcaDeAgua() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista()));
        EmpleadoRemoto remoto = new EmpleadoRemoto(api, () -> "token");

        ResultadoRed<List<EmpleadoDto>> resultado = remoto.listarDesde(null, 0);

        assertTrue(resultado.isExitoso());
        assertEquals(1, resultado.getValor().size());
        assertEquals("mzelaya", resultado.getValor().get(0).getApodoUsuario());
        // Sin `actualizado_en` el sync delta no puede avanzar la marca.
        assertEquals("2026-08-01 10:00:00+00", resultado.getValor().get(0).getActualizadoEn());
    }

    @Test
    public void listarDesde_sinConexion_noTraeCodigoHttp() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        api.respuestaListarDesde = FakeCall.deFallo(new IOException("timeout"));
        EmpleadoRemoto remoto = new EmpleadoRemoto(api, () -> "token");

        ResultadoRed<List<EmpleadoDto>> resultado = remoto.listarDesde(null, 0);

        assertFalse(resultado.isExitoso());
        // SIN_CODIGO es lo que hace que el outbox lo trate como transitorio y reintente.
        assertEquals(ClasificadorDeError.SIN_CODIGO, resultado.getCodigoHttp());
        assertEquals("Sin conexión al servidor. Intentá de nuevo.", resultado.getMensaje());
    }

    // ------------------------------------------------------------------ alta

    @Test
    public void crear_exitoso_devuelveLoQueRespondeLaEdgeFunction() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        api.respuestaCrear = FakeCall.deRespuesta(Response.success(gson.fromJson(
                "{\"id_empleado\":5,\"id_auth_user\":\"uuid-5\",\"apodo_usuario\":\"jperez\"}",
                CrearEmpleadoResponseDto.class)));
        EmpleadoRemoto remoto = new EmpleadoRemoto(api, () -> "token");

        ResultadoRed<CrearEmpleadoResponseDto> resultado = remoto.crear(unNuevoEmpleado());

        assertTrue(resultado.isExitoso());
        assertEquals(5, resultado.getValor().getIdEmpleado());
        assertEquals("jperez", resultado.getValor().getApodoUsuario());
    }

    @Test
    public void crear_errorDeEdgeFunction_propagaElMensajeEscritoPorLaFuncion() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        api.respuestaCrear = FakeCall.deRespuesta(Response.error(400,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"error\":\"Ya existe un empleado con esa identidad\"}")));
        EmpleadoRemoto remoto = new EmpleadoRemoto(api, () -> "token");

        ResultadoRed<CrearEmpleadoResponseDto> resultado = remoto.crear(unNuevoEmpleado());

        assertFalse(resultado.isExitoso());
        assertEquals(400, resultado.getCodigoHttp());
        assertEquals("Ya existe un empleado con esa identidad", resultado.getMensaje());
    }

    // ------------------------------------------------------------------ escrituras del outbox

    @Test
    public void cambiarRol_errorDelTrigger_propagaElMensajeDePostgrestConSuCodigo() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        api.respuestaActualizarPerfil = FakeCall.deRespuesta(Response.error(400,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"No se puede modificar a otro administrador\"}")));
        EmpleadoRemoto remoto = new EmpleadoRemoto(api, () -> "token");

        ResultadoRed<Void> resultado = remoto.cambiarRol("uuid-otro-admin", "mesero");

        assertFalse(resultado.isExitoso());
        // 400 = permanente: el outbox tiene que descartarla, no reintentar para siempre
        // una operación que el trigger va a rechazar igual.
        assertEquals(400, resultado.getCodigoHttp());
        assertFalse(ClasificadorDeError.esTransitorio(resultado.getCodigoHttp()));
        assertEquals("No se puede modificar a otro administrador", resultado.getMensaje());
    }

    @Test
    public void actualizarDatos_mandaElFiltroEqEnLasDosTablas() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        EmpleadoRemoto remoto = new EmpleadoRemoto(api, () -> "token");

        ResultadoRed<Void> resultado = remoto.actualizarDatos(7, "uuid-7", "Juan", "Pérez",
                "0801", "9999", "juan@restaurante.hn");

        assertTrue(resultado.isExitoso());
        // Un PATCH sin filtro en PostgREST actualiza TODAS las filas de la tabla.
        assertEquals("eq.7", api.ultimoFiltroEmpleado);
        assertEquals("eq.uuid-7", api.ultimoFiltroPerfil);
    }

    @Test
    public void actualizarDatos_siFallaElPerfil_reportaElFallo() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        api.respuestaActualizarPerfil = FakeCall.deRespuesta(Response.error(500,
                ResponseBody.create(MediaType.get("application/json"), "{}")));
        EmpleadoRemoto remoto = new EmpleadoRemoto(api, () -> "token");

        ResultadoRed<Void> resultado = remoto.actualizarDatos(7, "uuid-7", "Juan", "Pérez",
                "0801", "9999", "juan@restaurante.hn");

        // El nombre vive en las dos tablas; si la segunda falla, la operación no terminó.
        assertFalse(resultado.isExitoso());
        assertEquals(500, resultado.getCodigoHttp());
        assertTrue(ClasificadorDeError.esTransitorio(resultado.getCodigoHttp()));
    }

    // ------------------------------------------------------------------ helpers

    private List<EmpleadoDto> unaLista() {
        return Arrays.asList(gson.fromJson(JSON_EMPLEADO, EmpleadoDto.class));
    }

    private NuevoEmpleado unNuevoEmpleado() {
        return new NuevoEmpleado("Juan", "Pérez", "0801-1111", "9999-1111",
                "juan@restaurante.hn", "mesero", "Clave123!");
    }

    /** Fake mínimo: solo implementa lo que {@link EmpleadoRemoto} usa, y anota lo enviado. */
    private static final class FakeSupabaseEmpleadoApi implements SupabaseEmpleadoApi {

        Call<List<EmpleadoDto>> respuestaListarDesde;
        Call<CrearEmpleadoResponseDto> respuestaCrear;
        Call<Void> respuestaActualizarEmpleado = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaActualizarPerfil = FakeCall.deRespuesta(Response.success(null));

        String ultimoFiltro;
        String ultimoOrden;
        int ultimoLimite;
        int ultimoOffset;
        String ultimoFiltroEmpleado;
        String ultimoFiltroPerfil;

        @Override
        public Call<List<EmpleadoDto>> listar(String bearerToken) {
            return respuestaListarDesde;
        }

        @Override
        public Call<List<EmpleadoDto>> listarDesde(String bearerToken, String select,
                                                   String actualizadoEnMayorQue, String orden,
                                                   int limite, int desplazamiento) {
            ultimoFiltro = actualizadoEnMayorQue;
            ultimoOrden = orden;
            ultimoLimite = limite;
            ultimoOffset = desplazamiento;
            return respuestaListarDesde;
        }

        @Override
        public Call<CrearEmpleadoResponseDto> crear(String bearerToken,
                                                    CrearEmpleadoRequestDto cuerpo) {
            return respuestaCrear;
        }

        @Override
        public Call<Void> actualizarEmpleado(String bearerToken, String idEmpleadoIgualA,
                                             ActualizarEmpleadoDto cuerpo) {
            ultimoFiltroEmpleado = idEmpleadoIgualA;
            return respuestaActualizarEmpleado;
        }

        @Override
        public Call<Void> actualizarPerfil(String bearerToken, String idIgualA,
                                           ActualizarPerfilDto cuerpo) {
            ultimoFiltroPerfil = idIgualA;
            return respuestaActualizarPerfil;
        }
    }
}

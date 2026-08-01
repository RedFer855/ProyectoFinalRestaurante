package com.example.proyectofinalrestaurante.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.remote.SupabaseEmpleadoApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarEmpleadoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarPerfilDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoResponseDto;
import com.example.proyectofinalrestaurante.data.remote.dto.EmpleadoDto;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Empleado;
import com.example.proyectofinalrestaurante.domain.model.NuevoEmpleado;
import com.google.gson.Gson;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Tests de {@link SupabaseEmpleadoRepository} — deuda dejada por el Plan Fase 1d
 * ("Tests del repositorio", mismo caso que P-020 en Deuda Técnica - Pendientes).
 */
public class SupabaseEmpleadoRepositoryTest {

    private final Gson gson = new Gson();

    @Test
    public void listar_sinSesion_devuelveFalloSinLlamarALaRed() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        SupabaseEmpleadoRepository repositorio = new SupabaseEmpleadoRepository(api, () -> null);

        Result<List<Empleado>> resultado = repositorio.listar();

        assertFalse(resultado.isSuccess());
        assertEquals("Tu sesión venció. Volvé a iniciar sesión.", resultado.getError());
    }

    @Test
    public void listar_exitoso_mapeaLosDtoADominio() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        EmpleadoDto[] dtos = gson.fromJson(
                "[{\"id_empleado\":1,\"nombres\":\"Marta\",\"apellidos\":\"Zelaya\",\"identidad\":\"0801\","
                        + "\"telefono\":\"9999-0000\",\"correo\":\"marta@restaurante.hn\",\"id_usuario\":1,"
                        + "\"apodo_usuario\":\"mzelaya\",\"id_auth_user\":\"uuid-1\",\"rol\":\"mesero\",\"activo\":true}]",
                EmpleadoDto[].class);
        api.respuestaListar = FakeCall.deRespuesta(Response.success(List.of(dtos)));

        SupabaseEmpleadoRepository repositorio = new SupabaseEmpleadoRepository(api, () -> "token-válido");
        Result<List<Empleado>> resultado = repositorio.listar();

        assertTrue(resultado.isSuccess());
        assertEquals(1, resultado.getValue().size());
        assertEquals("mzelaya", resultado.getValue().get(0).getApodoUsuario());
    }

    @Test
    public void listar_sinConexion_devuelveFalloDeRed() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        api.respuestaListar = FakeCall.deFallo(new IOException("timeout"));

        SupabaseEmpleadoRepository repositorio = new SupabaseEmpleadoRepository(api, () -> "token-válido");
        Result<List<Empleado>> resultado = repositorio.listar();

        assertFalse(resultado.isSuccess());
        assertEquals("Sin conexión al servidor. Intentá de nuevo.", resultado.getError());
    }

    @Test
    public void crear_exitoso_devuelveElEmpleadoCreado() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        CrearEmpleadoResponseDto dto = gson.fromJson(
                "{\"id_empleado\":5,\"id_auth_user\":\"uuid-5\",\"apodo_usuario\":\"jperez\"}",
                CrearEmpleadoResponseDto.class);
        api.respuestaCrear = FakeCall.deRespuesta(Response.success(dto));

        SupabaseEmpleadoRepository repositorio = new SupabaseEmpleadoRepository(api, () -> "token-válido");
        NuevoEmpleado nuevo = new NuevoEmpleado(
                "Juan", "Pérez", "0801-1111", "9999-1111", "juan@restaurante.hn", "mesero", "Clave123!");
        Result<Empleado> resultado = repositorio.crear(nuevo);

        assertTrue(resultado.isSuccess());
        assertEquals("jperez", resultado.getValue().getApodoUsuario());
        assertEquals(5, resultado.getValue().getIdEmpleado());
    }

    @Test
    public void crear_errorDeEdgeFunction_devuelveElMensajeEscritoPorLaFuncion() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        api.respuestaCrear = FakeCall.deRespuesta(Response.error(400,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"error\":\"Ya existe un empleado con esa identidad\"}")));

        SupabaseEmpleadoRepository repositorio = new SupabaseEmpleadoRepository(api, () -> "token-válido");
        NuevoEmpleado nuevo = new NuevoEmpleado(
                "Juan", "Pérez", "0801-1111", "9999-1111", "juan@restaurante.hn", "mesero", "Clave123!");
        Result<Empleado> resultado = repositorio.crear(nuevo);

        assertFalse(resultado.isSuccess());
        assertEquals("Ya existe un empleado con esa identidad", resultado.getError());
    }

    @Test
    public void cambiarRol_errorDelTrigger_devuelveElMensajeDePostgrest() {
        FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
        api.respuestaActualizarPerfil = FakeCall.deRespuesta(Response.error(400,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"No se puede modificar a otro administrador\"}")));

        SupabaseEmpleadoRepository repositorio = new SupabaseEmpleadoRepository(api, () -> "token-válido");
        Result<Void> resultado = repositorio.cambiarRol("uuid-otro-admin", "mesero");

        assertFalse(resultado.isSuccess());
        assertEquals("No se puede modificar a otro administrador", resultado.getError());
    }

    /** Fake mínimo: solo implementa lo que {@link SupabaseEmpleadoRepository} usa. */
    private static final class FakeSupabaseEmpleadoApi implements SupabaseEmpleadoApi {

        Call<List<EmpleadoDto>> respuestaListar;
        Call<CrearEmpleadoResponseDto> respuestaCrear;
        Call<Void> respuestaActualizarEmpleado = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaActualizarPerfil = FakeCall.deRespuesta(Response.success(null));

        @Override
        public Call<List<EmpleadoDto>> listar(String bearerToken) {
            return respuestaListar;
        }

        @Override
        public Call<CrearEmpleadoResponseDto> crear(String bearerToken, CrearEmpleadoRequestDto cuerpo) {
            return respuestaCrear;
        }

        @Override
        public Call<Void> actualizarEmpleado(String bearerToken, String idEmpleadoIgualA, ActualizarEmpleadoDto cuerpo) {
            return respuestaActualizarEmpleado;
        }

        @Override
        public Call<Void> actualizarPerfil(String bearerToken, String idIgualA, ActualizarPerfilDto cuerpo) {
            return respuestaActualizarPerfil;
        }
    }
}

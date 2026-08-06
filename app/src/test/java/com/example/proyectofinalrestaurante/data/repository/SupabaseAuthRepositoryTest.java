package com.example.proyectofinalrestaurante.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.remote.SupabaseAuthApi;
import com.example.proyectofinalrestaurante.data.remote.SupabasePerfilApi;
import com.example.proyectofinalrestaurante.data.remote.dto.CambiarContraseniaRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginResponseDto;
import com.example.proyectofinalrestaurante.data.remote.dto.PerfilDto;
import com.example.proyectofinalrestaurante.data.remote.dto.RecuperarRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.RefrescarRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.VerificarCodigoRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.VerificarCodigoResponseDto;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.google.gson.Gson;

import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Cubre los 4 caminos de error de {@link SupabaseAuthRepository#login} más el camino
 * feliz, y los caminos de {@link SupabaseAuthRepository#solicitarCodigo} (incluido el
 * límite de correos por hora), sin red real (ver P-020 en Deuda Técnica - Pendientes).
 * Los DTOs se arman con
 * Gson desde JSON en vez de agregarles constructores solo para testear: Gson usa
 * reflexión y no necesita que la clase de producción cambie.
 */
public class SupabaseAuthRepositoryTest {

    private final Gson gson = new Gson();

    @Test
    public void login_exitoso_devuelveSesionConDatosDelPerfil() {
        FakeSupabaseAuthApi authApi = new FakeSupabaseAuthApi();
        authApi.respuestaLogin = FakeCall.deRespuesta(Response.success(loginDto("id-1", "ana@restaurante.hn", "token-1")));
        FakeSupabasePerfilApi perfilApi = new FakeSupabasePerfilApi();
        perfilApi.respuesta = FakeCall.deRespuesta(Response.success(
                perfilesDto("[{\"nombre\":\"Ana\",\"rol\":\"mesero\",\"activo\":true}]")));

        SupabaseAuthRepository repositorio = new SupabaseAuthRepository(authApi, perfilApi);
        Result<Sesion> resultado = repositorio.login("ana@restaurante.hn", "Clave123!");

        assertTrue(resultado.isSuccess());
        assertEquals("Ana", resultado.getValue().getNombre());
        assertEquals("mesero", resultado.getValue().getRol());
        assertFalse(authApi.logoutLlamado);
    }

    @Test
    public void login_exitoso_persisteRefreshTokenYExpiracionAbsoluta() {
        // P-009: expires_in viene en segundos relativos; el repositorio lo convierte a un
        // instante absoluto para que no dependa de cuándo se lea después.
        FakeSupabaseAuthApi authApi = new FakeSupabaseAuthApi();
        String json = "{\"access_token\":\"token-1\",\"refresh_token\":\"refresh-1\","
                + "\"expires_in\":3600,\"user\":{\"id\":\"id-1\",\"email\":\"ana@restaurante.hn\"}}";
        authApi.respuestaLogin = FakeCall.deRespuesta(Response.success(gson.fromJson(json, LoginResponseDto.class)));
        FakeSupabasePerfilApi perfilApi = new FakeSupabasePerfilApi();
        perfilApi.respuesta = FakeCall.deRespuesta(Response.success(
                perfilesDto("[{\"nombre\":\"Ana\",\"rol\":\"mesero\",\"activo\":true}]")));

        long antes = System.currentTimeMillis();
        SupabaseAuthRepository repositorio = new SupabaseAuthRepository(authApi, perfilApi);
        Result<Sesion> resultado = repositorio.login("ana@restaurante.hn", "Clave123!");
        long despues = System.currentTimeMillis();

        assertTrue(resultado.isSuccess());
        Sesion sesion = resultado.getValue();
        assertEquals("refresh-1", sesion.getRefreshToken());
        // El instante absoluto cae dentro de la ventana [antes, despues] + 3600s, con margen
        // por el tiempo que tarda en correr el test.
        assertTrue(sesion.getExpiraEnMillis() >= antes + 3600_000L);
        assertTrue(sesion.getExpiraEnMillis() <= despues + 3600_000L + 1_000L);
    }

    @Test
    public void login_sinExpiresIn_usaLaDuracionPorDefecto() {
        // Defensivo: si el servidor no manda expires_in, el token no debería quedar sin
        // vencimiento nunca (eso lo dejaría sin refrescar jamás).
        FakeSupabaseAuthApi authApi = new FakeSupabaseAuthApi();
        authApi.respuestaLogin = FakeCall.deRespuesta(
                Response.success(loginDto("id-1", "ana@restaurante.hn", "token-1")));
        FakeSupabasePerfilApi perfilApi = new FakeSupabasePerfilApi();
        perfilApi.respuesta = FakeCall.deRespuesta(Response.success(
                perfilesDto("[{\"nombre\":\"Ana\",\"rol\":\"mesero\",\"activo\":true}]")));

        SupabaseAuthRepository repositorio = new SupabaseAuthRepository(authApi, perfilApi);
        Result<Sesion> resultado = repositorio.login("ana@restaurante.hn", "Clave123!");

        assertTrue(resultado.isSuccess());
        assertTrue(resultado.getValue().getExpiraEnMillis() > System.currentTimeMillis());
    }

    @Test
    public void login_credencialesInvalidas_devuelveFalloSinTocarPerfil() {
        FakeSupabaseAuthApi authApi = new FakeSupabaseAuthApi();
        authApi.respuestaLogin = FakeCall.deRespuesta(
                Response.error(400, okhttp3.ResponseBody.create(
                        okhttp3.MediaType.get("application/json"), "{\"error\":\"invalid_grant\"}")));
        FakeSupabasePerfilApi perfilApi = new FakeSupabasePerfilApi();

        SupabaseAuthRepository repositorio = new SupabaseAuthRepository(authApi, perfilApi);
        Result<Sesion> resultado = repositorio.login("ana@restaurante.hn", "claveIncorrecta");

        assertFalse(resultado.isSuccess());
        assertEquals("Correo o contraseña incorrectos", resultado.getError());
    }

    @Test
    public void login_perfilInexistente_revocaElTokenYFalla() {
        FakeSupabaseAuthApi authApi = new FakeSupabaseAuthApi();
        authApi.respuestaLogin = FakeCall.deRespuesta(Response.success(loginDto("id-2", "sin-perfil@restaurante.hn", "token-2")));
        FakeSupabasePerfilApi perfilApi = new FakeSupabasePerfilApi();
        perfilApi.respuesta = FakeCall.deRespuesta(Response.success(Collections.<PerfilDto>emptyList()));

        SupabaseAuthRepository repositorio = new SupabaseAuthRepository(authApi, perfilApi);
        Result<Sesion> resultado = repositorio.login("sin-perfil@restaurante.hn", "Clave123!");

        assertFalse(resultado.isSuccess());
        assertEquals("Usuario no registrado en el sistema.", resultado.getError());
        assertTrue("un token válido sin perfil nunca debe quedar vivo", authApi.logoutLlamado);
    }

    @Test
    public void login_perfilInactivo_revocaElTokenYFalla() {
        FakeSupabaseAuthApi authApi = new FakeSupabaseAuthApi();
        authApi.respuestaLogin = FakeCall.deRespuesta(Response.success(loginDto("id-3", "inactivo@restaurante.hn", "token-3")));
        FakeSupabasePerfilApi perfilApi = new FakeSupabasePerfilApi();
        perfilApi.respuesta = FakeCall.deRespuesta(Response.success(
                perfilesDto("[{\"nombre\":\"Kelvin\",\"rol\":\"mesero\",\"activo\":false}]")));

        SupabaseAuthRepository repositorio = new SupabaseAuthRepository(authApi, perfilApi);
        Result<Sesion> resultado = repositorio.login("inactivo@restaurante.hn", "Clave123!");

        assertFalse(resultado.isSuccess());
        assertEquals("Tu cuenta está inactiva. Contactá al administrador.", resultado.getError());
        assertTrue(authApi.logoutLlamado);
    }

    @Test
    public void login_sinConexion_devuelveFalloDeRed() {
        FakeSupabaseAuthApi authApi = new FakeSupabaseAuthApi();
        authApi.respuestaLogin = FakeCall.deFallo(new IOException("host no resuelve"));
        FakeSupabasePerfilApi perfilApi = new FakeSupabasePerfilApi();

        SupabaseAuthRepository repositorio = new SupabaseAuthRepository(authApi, perfilApi);
        Result<Sesion> resultado = repositorio.login("ana@restaurante.hn", "Clave123!");

        assertFalse(resultado.isSuccess());
        assertEquals("Sin conexión al servidor. Intentá de nuevo.", resultado.getError());
    }

    @Test
    public void solicitarCodigo_limiteDeCorreosExcedido_devuelveFallo() {
        // Supabase corta el envío con 429 / over_email_send_rate_limit cuando el proyecto
        // pasó su cuota de correos por hora. Tragárselo dejaba al usuario en la pantalla
        // del código esperando un correo que nunca salió.
        FakeSupabaseAuthApi authApi = new FakeSupabaseAuthApi();
        authApi.respuestaSolicitarCodigo = FakeCall.deRespuesta(
                Response.error(429, okhttp3.ResponseBody.create(
                        okhttp3.MediaType.get("application/json"),
                        "{\"error_code\":\"over_email_send_rate_limit\",\"msg\":\"email rate limit exceeded\"}")));

        SupabaseAuthRepository repositorio = new SupabaseAuthRepository(authApi, new FakeSupabasePerfilApi());
        Result<Void> resultado = repositorio.solicitarCodigo("ana@restaurante.hn");

        assertFalse(resultado.isSuccess());
        assertEquals("Se alcanzó el límite de correos por hora. Esperá un momento y volvé a intentar.",
                resultado.getError());
    }

    @Test
    public void solicitarCodigo_correoNoRegistrado_devuelveExitoParaNoDelatarLaCuenta() {
        // Cualquier error que no sea el límite de envíos se reporta como éxito: la app
        // nunca revela si una cuenta existe.
        FakeSupabaseAuthApi authApi = new FakeSupabaseAuthApi();
        authApi.respuestaSolicitarCodigo = FakeCall.deRespuesta(
                Response.error(400, okhttp3.ResponseBody.create(
                        okhttp3.MediaType.get("application/json"), "{\"msg\":\"user not found\"}")));

        SupabaseAuthRepository repositorio = new SupabaseAuthRepository(authApi, new FakeSupabasePerfilApi());

        assertTrue(repositorio.solicitarCodigo("noexiste@restaurante.hn").isSuccess());
    }

    @Test
    public void solicitarCodigo_exitoso_devuelveOk() {
        FakeSupabaseAuthApi authApi = new FakeSupabaseAuthApi();
        authApi.respuestaSolicitarCodigo = FakeCall.deRespuesta(Response.success(null));

        SupabaseAuthRepository repositorio = new SupabaseAuthRepository(authApi, new FakeSupabasePerfilApi());

        assertTrue(repositorio.solicitarCodigo("ana@restaurante.hn").isSuccess());
    }

    @Test
    public void solicitarCodigo_sinConexion_devuelveFalloDeRed() {
        FakeSupabaseAuthApi authApi = new FakeSupabaseAuthApi();
        authApi.respuestaSolicitarCodigo = FakeCall.deFallo(new IOException("host no resuelve"));

        SupabaseAuthRepository repositorio = new SupabaseAuthRepository(authApi, new FakeSupabasePerfilApi());
        Result<Void> resultado = repositorio.solicitarCodigo("ana@restaurante.hn");

        assertFalse(resultado.isSuccess());
        assertEquals("Sin conexión al servidor. Intentá de nuevo.", resultado.getError());
    }

    private LoginResponseDto loginDto(String id, String email, String accessToken) {
        String json = "{\"access_token\":\"" + accessToken + "\",\"user\":{\"id\":\"" + id + "\",\"email\":\"" + email + "\"}}";
        return gson.fromJson(json, LoginResponseDto.class);
    }

    private List<PerfilDto> perfilesDto(String json) {
        PerfilDto[] array = gson.fromJson(json, PerfilDto[].class);
        return List.of(array);
    }

    /** Fake mínimo: solo implementa lo que {@link SupabaseAuthRepository#login} usa. */
    private static final class FakeSupabaseAuthApi implements SupabaseAuthApi {

        Call<LoginResponseDto> respuestaLogin;
        Call<Void> respuestaSolicitarCodigo;
        boolean logoutLlamado = false;

        @Override
        public Call<LoginResponseDto> login(LoginRequestDto body) {
            return respuestaLogin;
        }

        @Override
        public Call<Void> logout(String bearerToken) {
            logoutLlamado = true;
            return FakeCall.deRespuesta(Response.success(null));
        }

        @Override
        public Call<LoginResponseDto> refrescar(RefrescarRequestDto body) {
            throw new UnsupportedOperationException("No usado en este test");
        }

        @Override
        public Call<Void> solicitarCodigo(RecuperarRequestDto body) {
            if (respuestaSolicitarCodigo == null) {
                throw new UnsupportedOperationException("No usado en este test");
            }
            return respuestaSolicitarCodigo;
        }

        @Override
        public Call<VerificarCodigoResponseDto> verificarCodigo(VerificarCodigoRequestDto body) {
            throw new UnsupportedOperationException("No usado en este test");
        }

        @Override
        public Call<Void> cambiarContrasenia(String bearerToken, CambiarContraseniaRequestDto body) {
            throw new UnsupportedOperationException("No usado en este test");
        }
    }

    private static final class FakeSupabasePerfilApi implements SupabasePerfilApi {

        Call<List<PerfilDto>> respuesta;

        @Override
        public Call<List<PerfilDto>> obtenerPerfil(String bearerToken, String idIgualA) {
            return respuesta;
        }
    }
}

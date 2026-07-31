package com.example.proyectofinalrestaurante.data.repository;

import com.example.proyectofinalrestaurante.data.remote.SupabaseAuthApi;
import com.example.proyectofinalrestaurante.data.remote.SupabasePerfilApi;
import com.example.proyectofinalrestaurante.data.remote.dto.CambiarContraseniaRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginResponseDto;
import com.example.proyectofinalrestaurante.data.remote.dto.PerfilDto;
import com.example.proyectofinalrestaurante.data.remote.dto.RecuperarRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.VerificarCodigoRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.VerificarCodigoResponseDto;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.example.proyectofinalrestaurante.domain.repository.AuthRepository;

import java.io.IOException;
import java.util.List;

import retrofit2.Response;

/**
 * Implementación concreta de AuthRepository contra Supabase Auth (Data Layer).
 * Ver contexto/45 - Decisiones/ADR-002 - Supabase Auth via REST directo (Retrofit)...
 *
 * <p>El login no termina en el {@code access_token}: se verifica que exista un perfil
 * en {@code public.perfiles} y que esté activo antes de considerar la sesión válida
 * (mismo patrón que {@code AuthService.LoginAsync} en el proyecto Bimbo, que comprueba
 * {@code idEstado == 1} antes de dejar entrar). Si el perfil falta o está inactivo, el
 * token recién emitido se revoca — nunca se deja un token válido sin usar.</p>
 */
public class SupabaseAuthRepository implements AuthRepository {

    private final SupabaseAuthApi authApi;
    private final SupabasePerfilApi perfilApi;

    public SupabaseAuthRepository(SupabaseAuthApi authApi, SupabasePerfilApi perfilApi) {
        this.authApi = authApi;
        this.perfilApi = perfilApi;
    }

    @Override
    public Result<Sesion> login(String correo, String contrasenia) {
        try {
            Response<LoginResponseDto> response = authApi.login(new LoginRequestDto(correo, contrasenia)).execute();

            if (!response.isSuccessful() || response.body() == null) {
                return Result.fail("Correo o contraseña incorrectos");
            }

            LoginResponseDto body = response.body();
            if (body.getUser() == null || body.getAccessToken() == null) {
                return Result.fail("Respuesta inesperada del servidor");
            }

            String idUsuario = body.getUser().getId();
            String accessToken = body.getAccessToken();
            String bearerToken = "Bearer " + accessToken;

            return verificarPerfilYCrearSesion(idUsuario, body.getUser().getEmail(), accessToken, bearerToken);
        } catch (IOException ex) {
            return Result.fail("Sin conexión al servidor. Intentá de nuevo.");
        } catch (SecurityException ex) {
            // Ej.: falta el permiso INTERNET — no es IOException, pero tampoco debe
            // escapar crudo del hilo del Executor y tumbar la app (ver P-022).
            return Result.fail("La app no tiene permiso de red. Contactá al desarrollador.");
        }
    }

    private Result<Sesion> verificarPerfilYCrearSesion(
            String idUsuario, String correo, String accessToken, String bearerToken) throws IOException {

        Response<List<PerfilDto>> perfilResponse =
                perfilApi.obtenerPerfil(bearerToken, "eq." + idUsuario).execute();

        if (!perfilResponse.isSuccessful() || perfilResponse.body() == null || perfilResponse.body().isEmpty()) {
            authApi.logout(bearerToken).execute();
            return Result.fail("Usuario no registrado en el sistema.");
        }

        PerfilDto perfil = perfilResponse.body().get(0);
        if (!perfil.isActivo()) {
            authApi.logout(bearerToken).execute();
            return Result.fail("Tu cuenta está inactiva. Contactá al administrador.");
        }

        return Result.ok(new Sesion(idUsuario, correo, accessToken, perfil.getRol()));
    }

    @Override
    public Result<Void> solicitarCodigo(String correo) {
        try {
            // Supabase responde 200 incluso si el correo no existe. Si llegara a
            // responder error (p. ej. "correo no encontrado"), se trata igual que
            // el éxito: nunca se revela si una cuenta está registrada.
            authApi.solicitarCodigo(new RecuperarRequestDto(correo)).execute();
            return Result.ok(null);
        } catch (IOException ex) {
            return Result.fail("Sin conexión al servidor. Intentá de nuevo.");
        } catch (SecurityException ex) {
            return Result.fail("La app no tiene permiso de red. Contactá al desarrollador.");
        }
    }

    @Override
    public Result<String> verificarCodigo(String correo, String codigo) {
        try {
            Response<VerificarCodigoResponseDto> response =
                    authApi.verificarCodigo(new VerificarCodigoRequestDto("recovery", correo, codigo)).execute();

            if (!response.isSuccessful() || response.body() == null || response.body().getAccessToken() == null) {
                return Result.fail("El código es incorrecto o ya venció. Volvé a intentarlo.");
            }
            return Result.ok(response.body().getAccessToken());
        } catch (IOException ex) {
            return Result.fail("Sin conexión al servidor. Intentá de nuevo.");
        } catch (SecurityException ex) {
            return Result.fail("La app no tiene permiso de red. Contactá al desarrollador.");
        }
    }

    @Override
    public Result<Void> cambiarContrasenia(String accessToken, String nuevaContrasenia) {
        String bearerToken = "Bearer " + accessToken;
        try {
            Response<Void> response =
                    authApi.cambiarContrasenia(bearerToken, new CambiarContraseniaRequestDto(nuevaContrasenia)).execute();

            if (!response.isSuccessful()) {
                return Result.fail("No se pudo actualizar la contraseña. Intentá de nuevo.");
            }

            // La sesión temporal del OTP se revoca apenas se cambia la contraseña:
            // el usuario vuelve a loguearse con su clave nueva.
            try {
                authApi.logout(bearerToken).execute();
            } catch (IOException | SecurityException ignorada) {
                // El cambio ya fue exitoso; un fallo de logout no debe reportarse como error.
            }
            return Result.ok(null);
        } catch (IOException ex) {
            return Result.fail("Sin conexión al servidor. Intentá de nuevo.");
        } catch (SecurityException ex) {
            return Result.fail("La app no tiene permiso de red. Contactá al desarrollador.");
        }
    }
}

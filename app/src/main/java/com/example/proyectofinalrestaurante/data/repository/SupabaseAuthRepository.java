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

    /**
     * Duración por defecto si el servidor no manda {@code expires_in} (no debería pasar,
     * pero un token sin vencimiento nunca refrescaría). 1 hora es el valor por defecto
     * documentado de Supabase Auth.
     */
    private static final int EXPIRACION_POR_DEFECTO_SEGUNDOS = 3600;

    /**
     * 429 de Supabase Auth ({@code over_email_send_rate_limit}): el servidor de correo
     * cortó el envío por límite de mensajes por hora. Es el único fallo de {@code /recover}
     * que se le muestra al usuario — ver {@link #solicitarCodigo(String)}.
     */
    private static final int HTTP_LIMITE_DE_ENVIOS = 429;

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

            return verificarPerfilYCrearSesion(idUsuario, body.getUser().getEmail(), body, bearerToken);
        } catch (IOException ex) {
            return Result.fail("Sin conexión al servidor. Intentá de nuevo.");
        } catch (SecurityException ex) {
            // Ej.: falta el permiso INTERNET — no es IOException, pero tampoco debe
            // escapar crudo del hilo del Executor y tumbar la app (ver P-022).
            return Result.fail("La app no tiene permiso de red. Contactá al desarrollador.");
        }
    }

    private Result<Sesion> verificarPerfilYCrearSesion(
            String idUsuario, String correo, LoginResponseDto body, String bearerToken) throws IOException {

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

        // expires_in viene en segundos relativos desde que Supabase lo emitió; se guarda
        // como instante absoluto (P-009) para que no dependa de cuándo se lea después.
        int segundos = body.getExpiresIn() != null ? body.getExpiresIn() : EXPIRACION_POR_DEFECTO_SEGUNDOS;
        long expiraEnMillis = System.currentTimeMillis() + segundos * 1000L;

        return Result.ok(new Sesion(idUsuario, correo, body.getAccessToken(), body.getRefreshToken(),
                expiraEnMillis, perfil.getNombre(), perfil.getRol()));
    }

    @Override
    public Result<Void> solicitarCodigo(String correo) {
        try {
            Response<Void> response = authApi.solicitarCodigo(new RecuperarRequestDto(correo)).execute();

            // El 429 sí se reporta: no delata si la cuenta existe (el límite es del
            // proyecto entero, no del correo pedido) y tragárselo dejaba al usuario
            // esperando en la pantalla del código un correo que nunca se envió.
            if (response.code() == HTTP_LIMITE_DE_ENVIOS) {
                return Result.fail("Se alcanzó el límite de correos por hora. Esperá un momento y volvé a intentar.");
            }

            // Cualquier otro estado se trata como éxito: Supabase responde 200 incluso
            // si el correo no existe, y la app nunca revela si una cuenta está registrada.
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

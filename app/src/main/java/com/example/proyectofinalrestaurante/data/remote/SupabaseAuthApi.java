package com.example.proyectofinalrestaurante.data.remote;

import com.example.proyectofinalrestaurante.data.remote.dto.CambiarContraseniaRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginResponseDto;
import com.example.proyectofinalrestaurante.data.remote.dto.RecuperarRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.VerificarCodigoRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.VerificarCodigoResponseDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;

/**
 * Endpoint REST de Supabase Auth. Ver contexto/50 - Referencia/Supabase Auth
 * REST - Login Android.md — se consume directo, sin el SDK Kotlin.
 */
public interface SupabaseAuthApi {

    @POST("auth/v1/token?grant_type=password")
    Call<LoginResponseDto> login(@Body LoginRequestDto body);

    /**
     * Revoca el access_token (y sus refresh tokens). Se llama cuando el login de Auth
     * es válido pero el perfil no existe o está inactivo — no se deja un token vivo sin usar.
     */
    @POST("auth/v1/logout")
    Call<Void> logout(@Header("Authorization") String bearerToken);

    /**
     * Paso 1 de recuperación: pide un código OTP de 6 dígitos por correo.
     * Devuelve 200 siempre (incluso si el correo no existe) — la app nunca
     * revela si una cuenta está registrada.
     */
    @POST("auth/v1/recover")
    Call<Void> solicitarCodigo(@Body RecuperarRequestDto body);

    /**
     * Paso 2 de recuperación: verifica el código OTP y devuelve una sesión temporal
     * con la que se fija la contraseña nueva.
     */
    @POST("auth/v1/verify")
    Call<VerificarCodigoResponseDto> verificarCodigo(@Body VerificarCodigoRequestDto body);

    /**
     * Paso 3 de recuperación: fija la contraseña nueva usando la sesión temporal
     * obtenida en el paso 2.
     */
    @PUT("auth/v1/user")
    Call<Void> cambiarContrasenia(
            @Header("Authorization") String bearerToken,
            @Body CambiarContraseniaRequestDto body);
}

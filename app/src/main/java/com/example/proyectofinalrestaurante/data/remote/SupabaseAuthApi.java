package com.example.proyectofinalrestaurante.data.remote;

import com.example.proyectofinalrestaurante.data.remote.dto.LoginRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginResponseDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

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
}

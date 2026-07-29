package com.example.proyectofinalrestaurante.data.remote;

import com.example.proyectofinalrestaurante.data.remote.dto.LoginRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginResponseDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Endpoint REST de Supabase Auth. Ver contexto/50 - Referencia/Supabase Auth
 * REST - Login Android.md — se consume directo, sin el SDK Kotlin.
 */
public interface SupabaseAuthApi {

    @POST("auth/v1/token?grant_type=password")
    Call<LoginResponseDto> login(@Body LoginRequestDto body);
}

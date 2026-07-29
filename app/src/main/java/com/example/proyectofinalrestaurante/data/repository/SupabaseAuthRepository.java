package com.example.proyectofinalrestaurante.data.repository;

import com.example.proyectofinalrestaurante.data.remote.SupabaseAuthApi;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginResponseDto;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.example.proyectofinalrestaurante.domain.repository.AuthRepository;

import java.io.IOException;

import retrofit2.Response;

/**
 * Implementación concreta de AuthRepository contra Supabase Auth (Data Layer).
 * Ver contexto/45 - Decisiones/ADR-002 - Supabase Auth via REST directo (Retrofit)...
 */
public class SupabaseAuthRepository implements AuthRepository {

    private final SupabaseAuthApi api;

    public SupabaseAuthRepository(SupabaseAuthApi api) {
        this.api = api;
    }

    @Override
    public Result<Sesion> login(String correo, String contrasenia) {
        try {
            Response<LoginResponseDto> response = api.login(new LoginRequestDto(correo, contrasenia)).execute();

            if (!response.isSuccessful() || response.body() == null) {
                return Result.fail("Correo o contraseña incorrectos");
            }

            LoginResponseDto body = response.body();
            if (body.getUser() == null || body.getAccessToken() == null) {
                return Result.fail("Respuesta inesperada del servidor");
            }

            Sesion sesion = new Sesion(body.getUser().getId(), body.getUser().getEmail(), body.getAccessToken());
            return Result.ok(sesion);
        } catch (IOException ex) {
            return Result.fail("Sin conexión al servidor. Intentá de nuevo.");
        }
    }
}

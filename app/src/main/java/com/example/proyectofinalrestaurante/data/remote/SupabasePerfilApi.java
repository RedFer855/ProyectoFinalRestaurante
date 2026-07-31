package com.example.proyectofinalrestaurante.data.remote;

import com.example.proyectofinalrestaurante.data.remote.dto.PerfilDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

/**
 * Endpoint PostgREST de public.perfiles. Requiere el Authorization: Bearer del usuario
 * autenticado — la tabla no tiene SELECT para el rol anon (ver política RLS).
 */
public interface SupabasePerfilApi {

    @GET("rest/v1/perfiles?select=nombre,rol,activo")
    Call<List<PerfilDto>> obtenerPerfil(
            @Header("Authorization") String bearerToken,
            @Query("id") String idIgualA);
}

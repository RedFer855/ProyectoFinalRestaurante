package com.example.proyectofinalrestaurante.data.remote;

import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearClienteDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * API Retrofit de Supabase para el módulo Clientes (Fase 2d).
 *
 * <p>Lee de la vista {@code vista_clientes} y escribe en la tabla {@code clientes}.
 * El filtro del {@code PATCH} y {@code DELETE} es obligatorio — sin él, PostgREST
 * afecta todas las filas.</p>
 */
public interface SupabaseClienteApi {

    @GET("rest/v1/vista_clientes")
    Call<List<ClienteDto>> listarClientes(
            @Header("Authorization") String bearer,
            @Query("select") String select,
            @Query("actualizado_en") String filtro,
            @Query("order") String orden,
            @Query("limit") int limite);

    @POST("rest/v1/clientes")
    Call<List<ClienteDto>> crearCliente(
            @Header("Authorization") String bearer,
            @Header("Prefer") String prefer,
            @Body CrearClienteDto cuerpo);

    @PATCH("rest/v1/clientes")
    Call<Void> actualizarCliente(
            @Header("Authorization") String bearer,
            @Query("id_cliente") String idIgualA,
            @Body ActualizarClienteDto cuerpo);

    @DELETE("rest/v1/clientes")
    Call<Void> borrarCliente(
            @Header("Authorization") String bearer,
            @Query("id_cliente") String idIgualA);
}

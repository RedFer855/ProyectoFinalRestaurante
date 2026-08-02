package com.example.proyectofinalrestaurante.data.remote;

import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.BuscarOCrearClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearClienteDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Endpoints PostgREST del módulo Clientes (Plan Fase 2c, §3 — Plan Fase 2d, §3).
 *
 * <p>La lectura va contra {@code vista_clientes}; las escrituras contra la tabla
 * {@code clientes}; {@code buscar_o_crear_cliente} es el RPC que Pedidos (Fase 4) va a
 * necesitar (Plan Fase 2d, §5.1) — expuesto ahora, sin consumidor todavía.</p>
 */
public interface SupabaseClienteApi {

    /** Sync delta (Plan Fase 2b, §4.3): igual firma que {@code listarMesasDesde}. */
    @GET("rest/v1/vista_clientes")
    Call<List<ClienteDto>> listarClientesDesde(
            @Header("Authorization") String bearerToken,
            @Query("select") String select,
            @Query("actualizado_en") String actualizadoEnMayorQue,
            @Query("order") String orden,
            @Query("limit") int limite);

    /** {@code Prefer: return=representation}: devuelve un array de un elemento, no un objeto. */
    @Headers("Prefer: return=representation")
    @POST("rest/v1/clientes")
    Call<List<ClienteDto>> crearCliente(
            @Header("Authorization") String bearerToken,
            @Body CrearClienteDto cuerpo);

    /**
     * Reusado para datos y para baja/alta lógica (mismo patrón que
     * {@code SupabaseMenuApi.actualizarCategoria}): el filtro es obligatorio, sin valor por
     * defecto, porque un PATCH sin filtro en PostgREST actualiza todas las filas.
     */
    @PATCH("rest/v1/clientes")
    Call<Void> actualizarCliente(
            @Header("Authorization") String bearerToken,
            @Query("id_cliente") String idClienteIgualA,
            @Body ActualizarClienteDto cuerpo);

    /** Solo funciona si el cliente no tiene pedidos; si los tiene, el trigger lo rechaza. */
    @DELETE("rest/v1/clientes")
    Call<Void> borrarCliente(
            @Header("Authorization") String bearerToken,
            @Query("id_cliente") String idClienteIgualA);

    /**
     * RPC {@code buscar_o_crear_cliente}: devuelve el {@code id_cliente} como un entero
     * escalar (PostgREST lo serializa como JSON crudo, sin envolver en objeto).
     */
    @POST("rest/v1/rpc/buscar_o_crear_cliente")
    Call<Integer> buscarOCrearCliente(
            @Header("Authorization") String bearerToken,
            @Body BuscarOCrearClienteDto cuerpo);
}

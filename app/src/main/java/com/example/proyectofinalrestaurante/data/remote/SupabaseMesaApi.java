package com.example.proyectofinalrestaurante.data.remote;

import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CambiarEstadoMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.MesaDto;

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
 * Endpoints PostgREST del módulo Mesas (Fase 2c).
 *
 * <p>Se <b>lee</b> de la vista y se <b>escribe</b> en la tabla: la vista resuelve el
 * nombre del estado y tiene {@code security_invoker = on}, así que respeta la RLS de
 * quien consulta.</p>
 *
 * <p>El header {@code apikey} lo pone el interceptor de {@code SupabaseClient}; el
 * {@code Authorization} lo pone cada método.</p>
 */
public interface SupabaseMesaApi {

    @GET("rest/v1/vista_mesas?select=*&order=numero_mesa")
    Call<List<MesaDto>> listarMesas(@Header("Authorization") String bearerToken);

    /**
     * Sync delta (Fase 2b, §4.3): solo las filas modificadas después de la marca de
     * agua, paginadas por {@code actualizado_en}.
     */
    @GET("rest/v1/vista_mesas")
    Call<List<MesaDto>> listarMesasDesde(
            @Header("Authorization") String bearerToken,
            @Query("select") String select,
            @Query("actualizado_en") String actualizadoEnMayorQue,
            @Query("order") String orden,
            @Query("limit") int limite);

    /**
     * {@code Prefer: return=representation} hace que PostgREST devuelva la fila creada con
     * su id generado. Devuelve un <b>array de un elemento</b>.
     */
    @Headers("Prefer: return=representation")
    @POST("rest/v1/mesa")
    Call<List<MesaDto>> crearMesa(
            @Header("Authorization") String bearerToken,
            @Body CrearMesaDto cuerpo);

    /**
     * El filtro es obligatorio: un PATCH sin filtro en PostgREST actualiza <b>todas</b>
     * las filas de la tabla.
     */
    @PATCH("rest/v1/mesa")
    Call<Void> actualizarMesa(
            @Header("Authorization") String bearerToken,
            @Query("id_mesa") String idMesaIgualA,
            @Body ActualizarMesaDto cuerpo);

    /**
     * RPC para cambiar el estado operativo de una mesa (Libre/Ocupada/Reservada).
     * Devuelve void (204 sin cuerpo).
     */
    @POST("rest/v1/rpc/cambiar_estado_mesa")
    Call<Void> cambiarEstadoMesa(
            @Header("Authorization") String bearerToken,
            @Body CambiarEstadoMesaDto cuerpo);

    /**
     * Un DELETE sin filtro borraría la tabla entera. El servidor igual rechaza borrar una
     * mesa que tenga pedidos (trigger).
     */
    @DELETE("rest/v1/mesa")
    Call<Void> borrarMesa(
            @Header("Authorization") String bearerToken,
            @Query("id_mesa") String idMesaIgualA);
}

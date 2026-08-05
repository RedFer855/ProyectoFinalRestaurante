package com.example.proyectofinalrestaurante.data.remote;

import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CambiarEstadoMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.MesaDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Endpoints PostgREST del módulo Mesas (Plan Fase 2c, §3).
 *
 * <p>La lectura va contra la vista {@code vista_mesas} —el contrato que fija la Parte A en
 * §2.5—, las escrituras administrativas contra la tabla {@code mesa} y el cambio de estado
 * operativo contra el RPC {@code cambiar_estado_mesa}, que es la única vía de escritura del
 * mesero.</p>
 *
 * <p>El header {@code apikey} lo pone el interceptor de {@code SupabaseClient}; el
 * {@code Authorization} lo pone cada método, igual que en {@link SupabaseMenuApi}.</p>
 */
public interface SupabaseMesaApi {

    /**
     * Sync delta (Plan Fase 2b, §4.3): solo las filas modificadas después de la marca de
     * agua. Retrofit codifica los valores, así que el {@code +} del offset del timestamp
     * viaja como {@code %2B} y PostgREST lo decodifica.
     *
     * <p><b>La paginación va por {@code offset}, no por marca de agua (P-029, 2026-08-04).</b>
     * Antes, cada página avanzaba la marca al máximo visto y la siguiente pedía
     * {@code gt.<esa marca>}. Con ≥50 filas compartiendo el mismo {@code actualizado_en}
     * —el caso normal de un salón sembrado con un INSERT masivo— la página 2 empezaba
     * <em>después</em> de esas filas y las siguientes no se bajaban nunca. El {@code order}
     * incluye el id como desempate porque sin un orden total el {@code offset} puede repetir
     * o saltear filas entre pedidos. Mismo patrón que {@link SupabaseMenuApi}.</p>
     */
    @GET("rest/v1/vista_mesas")
    Call<List<MesaDto>> listarMesasDesde(
            @Header("Authorization") String bearerToken,
            @Query("select") String select,
            @Query("actualizado_en") String actualizadoEnMayorQue,
            @Query("order") String orden,
            @Query("limit") int limite,
            @Query("offset") int desplazamiento);

    /**
     * {@code Prefer: return=representation} hace que PostgREST devuelva la fila creada con
     * su id generado; sin ese header responde 201 con cuerpo vacío. Devuelve un
     * <b>array de un elemento</b>, no un objeto.
     */
    @Headers("Prefer: return=representation")
    @POST("rest/v1/mesa")
    Call<List<MesaDto>> crearMesa(
            @Header("Authorization") String bearerToken,
            @Body CrearMesaDto cuerpo);

    /**
     * El filtro es obligatorio: un PATCH sin filtro en PostgREST actualiza <b>todas</b>
     * las filas de la tabla. Por eso el parámetro no tiene valor por defecto.
     */
    @PATCH("rest/v1/mesa")
    Call<Void> actualizarMesa(
            @Header("Authorization") String bearerToken,
            @Query("id_mesa") String idMesaIgualA,
            @Body ActualizarMesaDto cuerpo);

    /**
     * Baja ({@code id_estado = 2}) / alta ({@code id_estado = 1}) lógica, con el mismo
     * filtro obligatorio. El cuerpo se arma con {@link ActualizarMesaDto#soloEstado(int)},
     * mismo patrón que el platillo reusa {@code ActualizarPlatilloDto.soloEstado}.
     */
    @PATCH("rest/v1/mesa")
    Call<Void> cambiarBajaMesa(
            @Header("Authorization") String bearerToken,
            @Query("id_mesa") String idMesaIgualA,
            @Body ActualizarMesaDto cuerpo);

    /**
     * RPC {@code cambiar_estado_mesa}: la única vía de escritura del mesero sobre la mesa.
     * Devuelve {@code void} (204 = éxito): no esperes un cuerpo de respuesta (Plan Fase 2c,
     * §5.2). Los errores de rol vienen en {@code {"message": "..."}} y los muestra la UI.
     */
    @POST("rest/v1/rpc/cambiar_estado_mesa")
    Call<Void> cambiarEstadoMesa(
            @Header("Authorization") String bearerToken,
            @Body CambiarEstadoMesaDto cuerpo);
}

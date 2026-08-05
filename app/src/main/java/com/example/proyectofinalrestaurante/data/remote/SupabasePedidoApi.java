package com.example.proyectofinalrestaurante.data.remote;

import com.example.proyectofinalrestaurante.data.remote.dto.AvanzarEstadoPedidoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearPedidoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.PedidoDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Endpoints PostgREST del módulo Pedidos (Plan Fase 3, §3.1).
 *
 * <p>La lectura va contra la vista {@code vista_pedidos} —el contrato que fija la Parte A en
 * §2.4— y la única escritura de esta fase contra el RPC {@code avanzar_estado_pedido}, que
 * es la vía exclusiva del mesero/cocina/admin para cambiar el estado (no hay
 * {@code CREAR_PEDIDO}: la 3 crea pedidos desde la línea, Fase 3b — ver §1.2).</p>
 *
 * <p>El header {@code apikey} lo pone el interceptor de {@code SupabaseClient}; el
 * {@code Authorization} lo pone cada método, igual que en {@link SupabaseMenuApi}.</p>
 */
public interface SupabasePedidoApi {

    /**
     * Sync delta (Plan Fase 2b, §4.3): solo las filas modificadas después de la marca de
     * agua, paginadas por {@code offset}. Retrofit codifica los valores, así que el
     * {@code +} del offset del timestamp viaja como {@code %2B} y PostgREST lo decodifica.
     *
     * <p><b>La paginación va por {@code offset}, no por marca de agua (2026-08-04).</b> Es
     * el mismo patrón corregido de {@link SupabaseMenuApi}: con ≥50 filas compartiendo el
     * mismo {@code actualizado_en} —el caso normal de una tanda de pedidos del mediodía— la
     * página 2 empezaría después de esas filas y las excedentes no se bajarían nunca. Ese
     * bug está registrado como P-029 en los otros tres sincronizadores; en Pedidos no se
     * reintroduce (Plan Fase 3, §4.4). El {@code order} incluye el id como desempate porque
     * sin un orden total el {@code offset} puede repetir o saltear filas entre pedidos.</p>
     */
    @GET("rest/v1/vista_pedidos")
    Call<List<PedidoDto>> listarPedidosDesde(
            @Header("Authorization") String bearerToken,
            @Query("select") String select,
            @Query("actualizado_en") String actualizadoEnMayorQue,
            @Query("order") String orden,
            @Query("limit") int limite,
            @Query("offset") int desplazamiento);

    /**
     * RPC {@code avanzar_estado_pedido}: la única vía de escritura del estado. Devuelve
     * {@code void} (204 = éxito): no esperes un cuerpo de respuesta (Plan Fase 3, §2.5).
     * Los errores de rol o de pedido cerrado vienen en {@code {"message": "..."}} y los
     * muestra la UI.
     */
    @POST("rest/v1/rpc/avanzar_estado_pedido")
    Call<Void> avanzarEstado(
            @Header("Authorization") String bearerToken,
            @Body AvanzarEstadoPedidoDto cuerpo);

    /**
     * RPC {@code crear_pedido(jsonb)} (Plan Fase 3b, §5.3): la <b>única</b> vía de alta de un
     * pedido. El cuerpo es el JSONB con cabecera + líneas ({@code PayloadCrearPedido}); la
     * respuesta trae el {@code id_pedido} — el mismo si la {@code clave_idempotencia} ya
     * existía (idempotente, B2). El RPC es transaccional: o entra todo o no entra nada.
     */
    @POST("rest/v1/rpc/crear_pedido")
    Call<CrearPedidoDto> crearPedido(
            @Header("Authorization") String bearerToken,
            @Body String cuerpoJson);
}
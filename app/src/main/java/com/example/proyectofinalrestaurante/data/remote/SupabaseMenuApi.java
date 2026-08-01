package com.example.proyectofinalrestaurante.data.remote;

import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarCategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarPlatilloDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearCategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearPlatilloDto;
import com.example.proyectofinalrestaurante.data.remote.dto.PlatilloDto;

import java.util.List;

import okhttp3.RequestBody;
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
 * Endpoints PostgREST del módulo Menú (Plan Fase 2a, §3.1).
 *
 * <p>Se <b>lee</b> de las vistas y se <b>escribe</b> en las tablas: las vistas ya resuelven
 * el nombre de la categoría y los contadores, y tienen {@code security_invoker = on}, así
 * que respetan la RLS de quien consulta.</p>
 *
 * <p>El header {@code apikey} lo pone el interceptor de {@code SupabaseClient}; el
 * {@code Authorization} lo pone cada método, igual que en {@link SupabaseEmpleadoApi}.</p>
 */
public interface SupabaseMenuApi {

    @GET("rest/v1/vista_platillos?select=*&order=nombre")
    Call<List<PlatilloDto>> listarPlatillos(@Header("Authorization") String bearerToken);

    @GET("rest/v1/vista_categorias?select=*&order=descripcion")
    Call<List<CategoriaDto>> listarCategorias(@Header("Authorization") String bearerToken);

    /**
     * Sync delta (Plan Fase 2b, §4.3): solo las filas modificadas después de la marca de
     * agua, paginadas por {@code actualizado_en}. Retrofit codifica los valores, así que el
     * {@code +} del offset del timestamp viaja como {@code %2B} y PostgREST lo decodifica.
     */
    @GET("rest/v1/vista_platillos")
    Call<List<PlatilloDto>> listarPlatillosDesde(
            @Header("Authorization") String bearerToken,
            @Query("select") String select,
            @Query("actualizado_en") String actualizadoEnMayorQue,
            @Query("order") String orden,
            @Query("limit") int limite);

    @GET("rest/v1/vista_categorias")
    Call<List<CategoriaDto>> listarCategoriasDesde(
            @Header("Authorization") String bearerToken,
            @Query("select") String select,
            @Query("actualizado_en") String actualizadoEnMayorQue,
            @Query("order") String orden,
            @Query("limit") int limite);

    /**
     * {@code Prefer: return=representation} hace que PostgREST devuelva la fila creada con
     * su id generado; sin ese header responde 201 con cuerpo vacío. Devuelve un
     * <b>array de un elemento</b>, no un objeto.
     */
    @Headers("Prefer: return=representation")
    @POST("rest/v1/platillo")
    Call<List<PlatilloDto>> crearPlatillo(
            @Header("Authorization") String bearerToken,
            @Body CrearPlatilloDto cuerpo);

    /**
     * El filtro es obligatorio: un PATCH sin filtro en PostgREST actualiza <b>todas</b>
     * las filas de la tabla. Por eso el parámetro no tiene valor por defecto.
     */
    @PATCH("rest/v1/platillo")
    Call<Void> actualizarPlatillo(
            @Header("Authorization") String bearerToken,
            @Query("id_platillo") String idPlatilloIgualA,
            @Body ActualizarPlatilloDto cuerpo);

    /**
     * Variante con el cuerpo JSON ya armado, para el único caso que Gson no puede
     * expresar: poner {@code ruta_imagen} explícitamente en null al quitar una foto.
     */
    @PATCH("rest/v1/platillo")
    Call<Void> actualizarPlatilloConCuerpoCrudo(
            @Header("Authorization") String bearerToken,
            @Query("id_platillo") String idPlatilloIgualA,
            @Body RequestBody cuerpoJson);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/categoria")
    Call<List<CategoriaDto>> crearCategoria(
            @Header("Authorization") String bearerToken,
            @Body CrearCategoriaDto cuerpo);

    @PATCH("rest/v1/categoria")
    Call<Void> actualizarCategoria(
            @Header("Authorization") String bearerToken,
            @Query("id_categoria") String idCategoriaIgualA,
            @Body ActualizarCategoriaDto cuerpo);

    /**
     * Mismo cuidado que el PATCH: un DELETE sin filtro borraría la tabla entera.
     * El servidor igual rechaza borrar una categoría que tenga platillos.
     */
    @DELETE("rest/v1/categoria")
    Call<Void> borrarCategoria(
            @Header("Authorization") String bearerToken,
            @Query("id_categoria") String idCategoriaIgualA);
}

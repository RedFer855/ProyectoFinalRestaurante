package com.example.proyectofinalrestaurante.data.remote;

import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarEmpleadoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarPerfilDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoResponseDto;
import com.example.proyectofinalrestaurante.data.remote.dto.EmpleadoDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Endpoints del módulo Empleados (Plan Fase 1d).
 *
 * <p>El alta va por la Edge Function porque crear una cuenta de acceso exige la llave
 * de servicio; todo lo demás va directo por PostgREST y lo autoriza la RLS.</p>
 */
public interface SupabaseEmpleadoApi {

    /** Lista desde la vista que ya une empleados + usuarios + perfiles. */
    @GET("rest/v1/vista_empleados?select=*&order=id_empleado")
    Call<List<EmpleadoDto>> listar(@Header("Authorization") String bearerToken);

    /**
     * Sync delta (Plan Fase 2b, §4.3): solo las filas modificadas después de la marca de
     * agua, paginadas por {@code actualizado_en}. Retrofit codifica los valores, así que el
     * {@code +} del offset del timestamp viaja como {@code %2B} y PostgREST lo decodifica.
     *
     * <p>La marca de la vista es el máximo entre {@code empleados.actualizado_en} y
     * {@code perfiles.actualizado_en}: un cambio de rol o de estado también la mueve.</p>
     */
    @GET("rest/v1/vista_empleados")
    Call<List<EmpleadoDto>> listarDesde(
            @Header("Authorization") String bearerToken,
            @Query("select") String select,
            @Query("actualizado_en") String actualizadoEnMayorQue,
            @Query("order") String orden,
            @Query("limit") int limite);

    @POST("functions/v1/crear-empleado")
    Call<CrearEmpleadoResponseDto> crear(
            @Header("Authorization") String bearerToken,
            @Body CrearEmpleadoRequestDto cuerpo);

    /**
     * El filtro es obligatorio: un PATCH sin filtro en PostgREST actualizaría
     * <b>todas</b> las filas de la tabla.
     */
    @PATCH("rest/v1/empleados")
    Call<Void> actualizarEmpleado(
            @Header("Authorization") String bearerToken,
            @Query("id_empleado") String idEmpleadoIgualA,
            @Body ActualizarEmpleadoDto cuerpo);

    /** Rol y estado viven en `perfiles`; un trigger propaga el rol a `usuarios`. */
    @PATCH("rest/v1/perfiles")
    Call<Void> actualizarPerfil(
            @Header("Authorization") String bearerToken,
            @Query("id") String idIgualA,
            @Body ActualizarPerfilDto cuerpo);
}

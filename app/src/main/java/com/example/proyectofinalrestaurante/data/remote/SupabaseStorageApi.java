package com.example.proyectofinalrestaurante.data.remote;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Endpoints del bucket {@code platillos} de Supabase Storage (Plan Fase 2a, §3.2).
 *
 * <p>Escribir exige ser admin (policies sobre {@code storage.objects}); <b>leer</b> no pasa
 * por acá: el bucket es público y la foto se descarga con un GET directo a
 * {@code /storage/v1/object/public/platillos/{ruta}}, que es lo que hace Glide desde
 * {@code ui} sin token ni Retrofit de por medio.</p>
 *
 * <p>No hay método de reemplazo en sitio ({@code PUT}) a propósito: cambiar una foto sube
 * a una ruta nueva y borra la vieja. Reusar la ruta haría que Glide siguiera sirviendo la
 * imagen anterior desde su caché.</p>
 */
public interface SupabaseStorageApi {

    /**
     * El {@code Content-Type} lo declara el propio {@link RequestBody} con el MIME de la
     * imagen. Por eso el interceptor de {@code SupabaseClient} no debe forzar
     * {@code application/json} cuando la petición ya trae un cuerpo con tipo.
     */
    @POST("storage/v1/object/platillos/{ruta}")
    Call<Void> subir(
            @Header("Authorization") String bearerToken,
            @Path("ruta") String ruta,
            @Body RequestBody imagen);

    @DELETE("storage/v1/object/platillos/{ruta}")
    Call<Void> borrar(
            @Header("Authorization") String bearerToken,
            @Path("ruta") String ruta);
}

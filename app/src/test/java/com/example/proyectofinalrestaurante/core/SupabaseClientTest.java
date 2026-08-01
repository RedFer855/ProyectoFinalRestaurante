package com.example.proyectofinalrestaurante.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.proyectofinalrestaurante.BuildConfig;

import org.junit.Test;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Tests de las cabeceras que {@link SupabaseClient} agrega a cada peticion.
 *
 * <p>El caso importante es el de la subida de imagenes a Storage: si el interceptor forzara
 * "Content-Type: application/json" en toda peticion, el cuerpo binario de un JPEG viajaria
 * declarado como JSON y el servidor lo rechazaria.
 */
public class SupabaseClientTest {

    private static final String CONTENT_TYPE = "Content-Type";

    @Test
    public void agregaLaApikeyATodaPeticion() {
        Request original = new Request.Builder()
                .url("https://proyecto.supabase.co/rest/v1/vista_platillos")
                .build();

        Request resultado = SupabaseClient.conCabeceras(original);

        assertEquals(BuildConfig.SUPABASE_PUBLISHABLE_KEY, resultado.header("apikey"));
    }

    @Test
    public void peticionSinCuerpoViajaComoJson() {
        Request original = new Request.Builder()
                .url("https://proyecto.supabase.co/rest/v1/vista_platillos")
                .build();

        Request resultado = SupabaseClient.conCabeceras(original);

        assertEquals("application/json", resultado.header(CONTENT_TYPE));
    }

    @Test
    public void cuerpoSinTipoPropioRecibeElJsonPorDefecto() {
        RequestBody sinTipo = RequestBody.create(null, new byte[]{1, 2, 3});
        Request original = new Request.Builder()
                .url("https://proyecto.supabase.co/rest/v1/platillo")
                .post(sinTipo)
                .build();

        Request resultado = SupabaseClient.conCabeceras(original);

        assertEquals("application/json", resultado.header(CONTENT_TYPE));
    }

    @Test
    public void cuerpoBinarioConservaSuPropioTipo() {
        RequestBody jpeg = RequestBody.create(MediaType.parse("image/jpeg"), new byte[]{1, 2, 3});
        Request original = new Request.Builder()
                .url("https://proyecto.supabase.co/storage/v1/object/platillos/foto.jpg")
                .post(jpeg)
                .build();

        Request resultado = SupabaseClient.conCabeceras(original);

        // El interceptor no debe fijar la cabecera: OkHttp la deriva del propio cuerpo.
        assertNull(resultado.header(CONTENT_TYPE));
        assertEquals("image/jpeg", resultado.body().contentType().toString());
    }

    @Test
    public void cuerpoJsonDeRetrofitConservaSuPropioTipo() {
        RequestBody json = RequestBody.create(
                MediaType.parse("application/json; charset=UTF-8"), "{\"nombre\":\"Baleada\"}");
        Request original = new Request.Builder()
                .url("https://proyecto.supabase.co/rest/v1/platillo")
                .post(json)
                .build();

        Request resultado = SupabaseClient.conCabeceras(original);

        assertNull(resultado.header(CONTENT_TYPE));
        assertEquals(BuildConfig.SUPABASE_PUBLISHABLE_KEY, resultado.header("apikey"));
    }
}

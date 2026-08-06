package com.example.proyectofinalrestaurante.core;

import com.example.proyectofinalrestaurante.BuildConfig;
import com.example.proyectofinalrestaurante.data.remote.SupabaseAuthApi;
import com.example.proyectofinalrestaurante.data.remote.SupabaseClienteApi;
import com.example.proyectofinalrestaurante.data.remote.SupabaseEmpleadoApi;
import com.example.proyectofinalrestaurante.data.remote.SupabaseMenuApi;
import com.example.proyectofinalrestaurante.data.remote.SupabaseMesaApi;
import com.example.proyectofinalrestaurante.data.remote.SupabasePedidoApi;
import com.example.proyectofinalrestaurante.data.remote.SupabasePerfilApi;
import com.example.proyectofinalrestaurante.data.remote.SupabaseReporteApi;
import com.example.proyectofinalrestaurante.data.remote.SupabaseStorageApi;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton del cliente HTTP hacia Supabase (equivalente a ServicioConexión en Bimbo).
 * La URL y la anon key vienen de local.properties -> BuildConfig, nunca hardcodeadas.
 */
public final class SupabaseClient {

    private static volatile SupabaseAuthApi authApi;
    private static volatile SupabasePerfilApi perfilApi;
    private static volatile SupabaseEmpleadoApi empleadoApi;
    private static volatile SupabaseMenuApi menuApi;
    private static volatile SupabaseMesaApi mesaApi;
    private static volatile SupabaseClienteApi clienteApi;
    private static volatile SupabasePedidoApi pedidoApi;
    private static volatile SupabaseStorageApi storageApi;
    private static volatile SupabaseReporteApi reporteApi;

    private SupabaseClient() {
    }

    public static SupabaseAuthApi getAuthApi() {
        if (authApi == null) {
            synchronized (SupabaseClient.class) {
                if (authApi == null) {
                    authApi = buildRetrofit().create(SupabaseAuthApi.class);
                }
            }
        }
        return authApi;
    }

    public static SupabasePerfilApi getPerfilApi() {
        if (perfilApi == null) {
            synchronized (SupabaseClient.class) {
                if (perfilApi == null) {
                    perfilApi = buildRetrofit().create(SupabasePerfilApi.class);
                }
            }
        }
        return perfilApi;
    }

    public static SupabaseEmpleadoApi getEmpleadoApi() {
        if (empleadoApi == null) {
            synchronized (SupabaseClient.class) {
                if (empleadoApi == null) {
                    empleadoApi = buildRetrofit().create(SupabaseEmpleadoApi.class);
                }
            }
        }
        return empleadoApi;
    }

    public static SupabaseMenuApi getMenuApi() {
        if (menuApi == null) {
            synchronized (SupabaseClient.class) {
                if (menuApi == null) {
                    menuApi = buildRetrofit().create(SupabaseMenuApi.class);
                }
            }
        }
        return menuApi;
    }

    public static SupabaseMesaApi getMesaApi() {
        if (mesaApi == null) {
            synchronized (SupabaseClient.class) {
                if (mesaApi == null) {
                    mesaApi = buildRetrofit().create(SupabaseMesaApi.class);
                }
            }
        }
        return mesaApi;
    }

    public static SupabaseClienteApi getClienteApi() {
        if (clienteApi == null) {
            synchronized (SupabaseClient.class) {
                if (clienteApi == null) {
                    clienteApi = buildRetrofit().create(SupabaseClienteApi.class);
                }
            }
        }
        return clienteApi;
    }

    public static SupabaseStorageApi getStorageApi() {
        if (storageApi == null) {
            synchronized (SupabaseClient.class) {
                if (storageApi == null) {
                    storageApi = buildRetrofit().create(SupabaseStorageApi.class);
                }
            }
        }
        return storageApi;
    }

    public static SupabasePedidoApi getPedidoApi() {
        if (pedidoApi == null) {
            synchronized (SupabaseClient.class) {
                if (pedidoApi == null) {
                    pedidoApi = buildRetrofit().create(SupabasePedidoApi.class);
                }
            }
        }
        return pedidoApi;
    }

    public static SupabaseReporteApi getReporteApi() {
        if (reporteApi == null) {
            synchronized (SupabaseClient.class) {
                if (reporteApi == null) {
                    reporteApi = buildRetrofit().create(SupabaseReporteApi.class);
                }
            }
        }
        return reporteApi;
    }

    private static Retrofit buildRetrofit() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(apiKeyInterceptor())
                .build();

        String baseUrl = BuildConfig.SUPABASE_URL.isEmpty()
                ? "https://supabase-no-configurado.invalid/"
                : BuildConfig.SUPABASE_URL + "/";

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    private static Interceptor apiKeyInterceptor() {
        return chain -> chain.proceed(conCabeceras(chain.request()));
    }

    /**
     * Agrega la apikey a toda peticion y el Content-Type JSON solo cuando la peticion no
     * trae un cuerpo con su propio tipo.
     *
     * <p>Header.header(...) reemplaza el valor anterior: forzar "application/json" en todas
     * las peticiones le pondria ese tipo tambien al cuerpo binario de una subida a Storage,
     * y el servidor la rechazaria. Los cuerpos que Retrofit arma con Gson ya declaran su
     * propio Content-Type, asi que el valor por defecto solo hace falta para las peticiones
     * sin cuerpo o con un cuerpo sin tipo.
     *
     * <p>Visible dentro del paquete para poder probarla sin levantar un servidor.
     */
    static Request conCabeceras(Request original) {
        Request.Builder builder = original.newBuilder()
                .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY);

        RequestBody cuerpo = original.body();
        if (cuerpo == null || cuerpo.contentType() == null) {
            builder.header("Content-Type", "application/json");
        }

        return builder.build();
    }
}

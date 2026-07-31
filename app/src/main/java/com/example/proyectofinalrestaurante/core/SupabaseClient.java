package com.example.proyectofinalrestaurante.core;

import com.example.proyectofinalrestaurante.BuildConfig;
import com.example.proyectofinalrestaurante.data.remote.SupabaseAuthApi;
import com.example.proyectofinalrestaurante.data.remote.SupabasePerfilApi;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;

/**
 * Singleton del cliente HTTP hacia Supabase (equivalente a ServicioConexión en Bimbo).
 * La URL y la anon key vienen de local.properties -> BuildConfig, nunca hardcodeadas.
 */
public final class SupabaseClient {

    private static volatile SupabaseAuthApi authApi;
    private static volatile SupabasePerfilApi perfilApi;

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
        return chain -> {
            Request original = chain.request();
            Request withApiKey = original.newBuilder()
                    .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .build();
            return chain.proceed(withApiKey);
        };
    }
}

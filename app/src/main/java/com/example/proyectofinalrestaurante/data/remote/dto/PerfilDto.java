package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Fila de public.perfiles (PostgREST). Ver contexto/40 - Proyecto Restaurante/Plan de Conexión con Supabase.md */
public final class PerfilDto {

    @SerializedName("nombre")
    private String nombre;

    @SerializedName("rol")
    private String rol;

    @SerializedName("activo")
    private boolean activo;

    public String getNombre() {
        return nombre;
    }

    public String getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }
}

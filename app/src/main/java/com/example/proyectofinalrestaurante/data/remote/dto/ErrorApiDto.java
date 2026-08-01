package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de error de la Edge Function: {@code {"error": "..."}}.
 *
 * <p>Sus mensajes sí se le muestran al usuario, a diferencia de los de PostgREST o
 * Auth: los escribimos nosotros en la función, en lenguaje humano y sin filtrar
 * detalles internos ("Ya existe un empleado con esa identidad").</p>
 */
public final class ErrorApiDto {

    @SerializedName("error")
    private String error;

    public String getError() {
        return error;
    }
}

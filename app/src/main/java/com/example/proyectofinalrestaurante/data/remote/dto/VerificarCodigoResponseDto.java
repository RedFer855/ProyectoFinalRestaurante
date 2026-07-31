package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Response de POST /auth/v1/verify — sesión temporal con la que se fija la contraseña nueva. */
public final class VerificarCodigoResponseDto {

    @SerializedName("access_token")
    private String accessToken;

    public String getAccessToken() {
        return accessToken;
    }
}

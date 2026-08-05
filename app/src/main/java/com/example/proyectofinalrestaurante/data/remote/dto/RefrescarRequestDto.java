package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Body de {@code POST /auth/v1/token?grant_type=refresh_token} (P-009). */
public final class RefrescarRequestDto {

    @SerializedName("refresh_token")
    private final String refreshToken;

    public RefrescarRequestDto(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}

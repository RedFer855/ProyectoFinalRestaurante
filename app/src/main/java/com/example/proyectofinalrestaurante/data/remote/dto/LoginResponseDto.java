package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Response de Supabase Auth para grant_type=password. */
public final class LoginResponseDto {

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("user")
    private UsuarioDto user;

    public String getAccessToken() {
        return accessToken;
    }

    public UsuarioDto getUser() {
        return user;
    }

    public static final class UsuarioDto {

        @SerializedName("id")
        private String id;

        @SerializedName("email")
        private String email;

        public String getId() {
            return id;
        }

        public String getEmail() {
            return email;
        }
    }
}

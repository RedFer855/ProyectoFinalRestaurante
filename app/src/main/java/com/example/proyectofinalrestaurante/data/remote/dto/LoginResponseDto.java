package com.example.proyectofinalrestaurante.data.remote.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Response de Supabase Auth para {@code grant_type=password} y {@code grant_type=refresh_token}
 * (misma forma en los dos, P-009: {@code refresh_token} y {@code expires_in} se sumaron acá
 * porque antes se tiraban a la basura — la sesión no se persistía).
 */
public final class LoginResponseDto {

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("refresh_token")
    private String refreshToken;

    /** Segundos relativos desde que Supabase emitió el token, no un instante absoluto. */
    @SerializedName("expires_in")
    private Integer expiresIn;

    @SerializedName("user")
    private UsuarioDto user;

    public String getAccessToken() {
        return accessToken;
    }

    @Nullable
    public String getRefreshToken() {
        return refreshToken;
    }

    @Nullable
    public Integer getExpiresIn() {
        return expiresIn;
    }

    /** {@code null} en la respuesta del refresh: Supabase no repite el usuario ahí. */
    @Nullable
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

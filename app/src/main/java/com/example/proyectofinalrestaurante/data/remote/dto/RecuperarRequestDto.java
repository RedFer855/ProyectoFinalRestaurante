package com.example.proyectofinalrestaurante.data.remote.dto;

/** Body de POST /auth/v1/recover — solicitar un código OTP de recuperación de contraseña. */
public final class RecuperarRequestDto {

    private final String email;

    public RecuperarRequestDto(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}

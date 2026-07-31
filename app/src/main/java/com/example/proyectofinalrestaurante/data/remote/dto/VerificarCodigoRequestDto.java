package com.example.proyectofinalrestaurante.data.remote.dto;

/** Body de POST /auth/v1/verify — verificar el código OTP de recuperación recibido por correo. */
public final class VerificarCodigoRequestDto {

    private final String type;
    private final String email;
    private final String token;

    public VerificarCodigoRequestDto(String type, String email, String token) {
        this.type = type;
        this.email = email;
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }
}

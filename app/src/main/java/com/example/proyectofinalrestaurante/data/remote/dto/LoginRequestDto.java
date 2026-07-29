package com.example.proyectofinalrestaurante.data.remote.dto;

/** Body de POST /auth/v1/token?grant_type=password */
public final class LoginRequestDto {

    private final String email;
    private final String password;

    public LoginRequestDto(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}

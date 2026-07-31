package com.example.proyectofinalrestaurante.data.remote.dto;

/** Body de PUT /auth/v1/user — fijar la contraseña nueva durante la recuperación. */
public final class CambiarContraseniaRequestDto {

    private final String password;

    public CambiarContraseniaRequestDto(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }
}

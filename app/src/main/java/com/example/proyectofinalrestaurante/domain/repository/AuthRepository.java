package com.example.proyectofinalrestaurante.domain.repository;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Sesion;

/**
 * Contrato de autenticación (Domain Layer). {@code data} implementa esta interfaz
 * — ver contexto/AGENTS.md, regla de oro #2: domain nunca referencia data.
 */
public interface AuthRepository {

    Result<Sesion> login(String correo, String contrasenia);

    /**
     * Paso 1 de recuperación de contraseña: pide un código OTP de 6 dígitos por correo.
     * Devuelve {@code Result.ok()} tanto si el correo está registrado como si no — nunca
     * se revela si una cuenta existe.
     */
    Result<Void> solicitarCodigo(String correo);

    /**
     * Paso 2 de recuperación: verifica el código OTP. Devuelve el {@code access_token}
     * de la sesión temporal con la que se fija la contraseña nueva.
     */
    Result<String> verificarCodigo(String correo, String codigo);

    /**
     * Paso 3 de recuperación: fija la contraseña nueva y revoca la sesión temporal
     * inmediatamente después. El usuario vuelve a loguearse con su clave nueva.
     */
    Result<Void> cambiarContrasenia(String accessToken, String nuevaContrasenia);
}

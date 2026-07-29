package com.example.proyectofinalrestaurante.domain.repository;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Sesion;

/**
 * Contrato de autenticación (Domain Layer). {@code data} implementa esta interfaz
 * — ver contexto/AGENTS.md, regla de oro #2: domain nunca referencia data.
 */
public interface AuthRepository {

    Result<Sesion> login(String correo, String contrasenia);
}

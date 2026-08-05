package com.example.proyectofinalrestaurante.domain.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Sesion;

/**
 * Contrato de persistencia de la sesión (P-009).
 *
 * <p>La implementación concreta ({@code SesionLocal}, en {@code data}) cifra el JSON de la
 * sesión con Android Keystore — domain no sabe cómo, ni le importa. Regla de oro: domain
 * nunca referencia data.</p>
 */
public interface SesionRepository {

    void guardar(Sesion sesion);

    @Nullable
    Sesion leer();

    void borrar();
}

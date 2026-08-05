package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.core.AlmacenSeguro;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.example.proyectofinalrestaurante.domain.repository.SesionRepository;
import com.google.gson.Gson;

/**
 * Implementación de {@link SesionRepository} sobre {@link AlmacenSeguro} (P-009).
 *
 * <p>Serializa la {@link Sesion} a JSON con Gson antes de cifrarla: es la forma más simple de
 * persistir un objeto de dominio sin acoplar {@code AlmacenSeguro} (que solo sabe cifrar
 * texto) a la forma de {@code Sesion}. Gson deserializa los campos {@code final} sin
 * necesitar un constructor sin argumentos.</p>
 */
public final class SesionLocal implements SesionRepository {

    private final AlmacenSeguro almacen;
    private final Gson gson = new Gson();

    public SesionLocal(AlmacenSeguro almacen) {
        this.almacen = almacen;
    }

    @Override
    public void guardar(Sesion sesion) {
        almacen.guardar(gson.toJson(sesion));
    }

    @Nullable
    @Override
    public Sesion leer() {
        String json = almacen.leer();
        if (json == null) {
            return null;
        }
        try {
            return gson.fromJson(json, Sesion.class);
        } catch (RuntimeException ex) {
            // JSON corrupto o de una versión vieja de Sesion: mismo contrato que
            // AlmacenSeguro.leer() ante cualquier fallo — se borra y se trata como
            // "sin sesión", nunca se propaga la excepción.
            almacen.borrar();
            return null;
        }
    }

    @Override
    public void borrar() {
        almacen.borrar();
    }
}

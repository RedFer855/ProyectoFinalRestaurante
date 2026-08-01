package com.example.proyectofinalrestaurante.domain.repository;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Empleado;
import com.example.proyectofinalrestaurante.domain.model.NuevoEmpleado;

import java.util.List;

/**
 * Contrato del módulo Empleados (Domain Layer). {@code data} lo implementa.
 *
 * <p>El alta pasa por una Edge Function porque crear una cuenta de acceso exige la
 * llave de servicio, que no puede vivir en la app. El resto va directo por PostgREST
 * con RLS — mantener esa superficie chica es deliberado.</p>
 */
public interface EmpleadoRepository {

    Result<List<Empleado>> listar();

    /** Crea el empleado y su cuenta de acceso. Devuelve el empleado ya creado. */
    Result<Empleado> crear(NuevoEmpleado nuevo);

    /** Datos personales: nombres, apellidos, identidad, teléfono y correo. */
    Result<Void> actualizarDatos(Empleado empleado);

    Result<Void> cambiarRol(String idAuthUser, String nuevoRol);

    Result<Void> cambiarEstado(String idAuthUser, boolean activo);
}

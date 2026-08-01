package com.example.proyectofinalrestaurante.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Empleado;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.NuevoEmpleado;

import java.util.List;

/**
 * Contrato del módulo Empleados (Domain Layer). {@code data} lo implementa.
 *
 * <p>Desde la migración a offline-first las <b>lecturas son {@link LiveData} sobre la base
 * local</b> y no llamadas que puedan fallar: la UI observa Room, que siempre responde, y el
 * {@code SyncWorker} la mantiene al día. Las escrituras siguen devolviendo {@code Result}
 * porque pueden fallar por validación local, pero <b>no</b> esperan a la red: escriben y
 * encolan.</p>
 *
 * <p><b>{@link #crear} es la excepción y sí necesita conexión.</b> El alta crea una cuenta en
 * Supabase Auth con una contraseña temporal, y encolarla obligaría a guardar esa contraseña
 * en el dispositivo (contra <b>P-009</b>) y a reintentar un {@code POST} no idempotente que
 * crea cuentas — lo que {@code Offline-First con Room y Outbox} prohíbe sin
 * <i>idempotency key</i>. Es el único punto del módulo donde la UI puede recibir un error
 * de red.</p>
 */
public interface EmpleadoRepository {

    /** La lista local. Nunca falla y nunca está vacía por culpa de la red. */
    LiveData<List<Empleado>> observarEmpleados();

    /** Estado global de la sincronización, para el indicador de la pantalla. */
    LiveData<EstadoSincronizacion> getEstadoSincronizacion();

    /** Pide una pasada de sincronización (el "bajar para actualizar"). */
    void sincronizar();

    /**
     * Crea el empleado y su cuenta de acceso. <b>Requiere conexión</b> — ver la nota de la
     * interfaz. Devuelve el id del empleado creado.
     */
    Result<Integer> crear(NuevoEmpleado nuevo);

    /** Datos personales: nombres, apellidos, identidad, teléfono y correo. */
    Result<Void> actualizarDatos(Empleado empleado);

    Result<Void> cambiarRol(int idEmpleado, String nuevoRol);

    Result<Void> cambiarEstado(int idEmpleado, boolean activo);
}

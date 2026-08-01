package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Empleado;

/**
 * Reglas de quién puede tocar a quién dentro del módulo Empleados (Plan Fase 1d).
 *
 * <p><b>Esto es para la interfaz, no es la seguridad.</b> La autoridad real es el
 * trigger {@code proteger_admins()} de Postgres, que rechaza la operación aunque
 * alguien modifique el APK. Acá se replica la misma lógica para que la app no ofrezca
 * botones que el servidor va a rechazar — mismo patrón que {@link Permisos} con RLS.</p>
 *
 * <p>Java puro, sin dependencias de Android: es la pieza testeable de este entregable.</p>
 */
public final class ReglasEmpleado {

    private ReglasEmpleado() {
    }

    private static boolean esAdmin(@Nullable String rol) {
        return Permisos.ROL_ADMIN.equalsIgnoreCase(rol);
    }

    private static boolean esElMismo(@Nullable String idAuthActor, @Nullable Empleado objetivo) {
        return idAuthActor != null && objetivo != null
                && idAuthActor.equals(objetivo.getIdAuthUser());
    }

    /**
     * Editar los datos personales (nombres, teléfono, correo…).
     *
     * <p>Un admin puede editar a cualquiera <b>menos a otro admin</b>. A sí mismo sí:
     * corregir su propio teléfono no compromete nada.</p>
     */
    public static boolean puedeEditar(@Nullable String rolActor, @Nullable String idAuthActor,
                                      @Nullable Empleado objetivo) {
        if (!esAdmin(rolActor) || objetivo == null) {
            return false;
        }
        return !esAdmin(objetivo.getRol()) || esElMismo(idAuthActor, objetivo);
    }

    /**
     * Cambiar el rol de alguien.
     *
     * <p>Más estricto que editar: además de no poder tocar a otro admin,
     * <b>nadie puede cambiar su propio rol</b>. Un admin que se auto-degrade dejaría
     * el sistema potencialmente sin ningún administrador y sin forma de recuperarlo
     * desde la app.</p>
     */
    public static boolean puedeCambiarRol(@Nullable String rolActor, @Nullable String idAuthActor,
                                          @Nullable Empleado objetivo) {
        if (!esAdmin(rolActor) || objetivo == null) {
            return false;
        }
        return !esAdmin(objetivo.getRol()) && !esElMismo(idAuthActor, objetivo);
    }

    /**
     * Activar o desactivar a alguien.
     *
     * <p>Mismo criterio que cambiar el rol. Desactivarse a uno mismo dejaría al admin
     * afuera en el acto: el servidor lo permitiría (el trigger solo protege el rol y
     * a los otros admins), pero la app no lo ofrece.</p>
     */
    public static boolean puedeCambiarEstado(@Nullable String rolActor,
                                             @Nullable String idAuthActor,
                                             @Nullable Empleado objetivo) {
        if (!esAdmin(rolActor) || objetivo == null) {
            return false;
        }
        return !esAdmin(objetivo.getRol()) && !esElMismo(idAuthActor, objetivo);
    }
}

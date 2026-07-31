package com.example.proyectofinalrestaurante.domain;

import java.util.Set;

/**
 * Visibilidad de los ítems del menú de navegación por rol.
 *
 * <p>Desde el Plan Fase 1c (Entregable 1) esta clase <b>ya no tiene su propia tabla</b>:
 * delega todo en {@link Permisos}. Un módulo es visible si el rol tiene {@link Accion#VER}
 * sobre él. Así hay una sola fuente de verdad — dos tablas separadas terminarían
 * desincronizándose.</p>
 *
 * <p>El enum {@code Item} se reemplazó por {@link Modulo}, compartido con {@link Permisos}.</p>
 *
 * <p>⚠ El rol del cliente es solo para mostrar/ocultar ítems. No es seguridad: un APK se
 * puede modificar. La seguridad real son las policies RLS de Postgres, que se evalúan en el
 * servidor con el JWT del usuario (ver Esquema de Base de Datos).</p>
 */
public final class VisibilidadMenu {

    public static final String ROL_ADMIN = Permisos.ROL_ADMIN;
    public static final String ROL_MESERO = Permisos.ROL_MESERO;
    public static final String ROL_COCINA = Permisos.ROL_COCINA;

    private VisibilidadMenu() {
    }

    /** Módulos visibles para el rol dado — los ítems que aparecen en el menú lateral. */
    public static Set<Modulo> itemsVisibles(String rol) {
        return Permisos.modulosVisibles(rol);
    }

    /** Conveniencia para {@code NavigationView}: un módulo es visible si el rol puede verlo. */
    public static boolean esVisible(String rol, Modulo modulo) {
        return Permisos.puede(rol, modulo, Accion.VER);
    }
}

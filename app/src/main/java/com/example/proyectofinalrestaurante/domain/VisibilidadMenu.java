package com.example.proyectofinalrestaurante.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Matriz de visibilidad del menú de navegación por rol (Plan Fase 1b, Entregable 6).
 * Java puro, sin dependencias de Android — el filtrado es verificable con JUnit.
 *
 * <p>⚠ El rol del cliente es solo para mostrar/ocultar ítems. No es seguridad:
 * un APK se puede modificar. La seguridad real son las policies RLS de Postgres,
 * que se evalúan en el servidor con el JWT del usuario (ver Esquema de Base de Datos).</p>
 */
public final class VisibilidadMenu {

    public static final String ROL_ADMIN = "admin";
    public static final String ROL_MESERO = "mesero";
    public static final String ROL_COCINA = "cocina";

    private VisibilidadMenu() {
    }

    /** Ítems del menú que existen en la app. */
    public enum Item {
        INICIO,
        PEDIDOS,
        MESAS,
        MENU,
        CLIENTES,
        EMPLEADOS,
        REPORTES
    }

    /**
     * Ítems visibles para el rol dado. Un rol desconocido ve solo lo mínimo
     * (Inicio), anteponiendo la seguridad a la comodidad.
     */
    public static Set<Item> itemsVisibles(String rol) {
        if (rol == null) {
            return Collections.emptySet();
        }
        switch (rol.toLowerCase(Locale.ROOT)) {
            case ROL_ADMIN:
                return Collections.unmodifiableSet(EnumSet.allOf(Item.class));
            case ROL_MESERO:
                return Collections.unmodifiableSet(EnumSet.of(
                        Item.INICIO, Item.PEDIDOS, Item.MESAS, Item.MENU, Item.CLIENTES));
            case ROL_COCINA:
                return Collections.unmodifiableSet(EnumSet.of(Item.INICIO, Item.PEDIDOS));
            default:
                return Collections.singleton(Item.INICIO);
        }
    }

    /** Conveniencia para {@code NavigationView}: un ítem es visible si está en la matriz. */
    public static boolean esVisible(String rol, Item item) {
        return itemsVisibles(rol).contains(item);
    }
}

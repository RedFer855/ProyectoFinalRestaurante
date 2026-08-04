package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Cliente;

import java.util.List;
import java.util.Locale;

/**
 * Reglas de negocio del módulo Clientes (Fase 2d). Java puro, sin dependencias de Android.
 *
 * <p><b>Esto no es seguridad.</b> Quien impide que un mesero borre un cliente es la policy
 * RLS de Postgres. Acá se replican para no ofrecer una acción que el servidor va a rechazar.</p>
 */
public final class ReglasCliente {

    private ReglasCliente() {
    }

    /**
     * Normaliza la identidad: elimina todo lo que no sea dígito.
     * Espeja exactamente el {@code regexp_replace(identidad, '[^0-9]', '', 'g')} del servidor.
     */
    public static String normalizarIdentidad(@Nullable String identidad) {
        if (identidad == null) {
            return "";
        }
        return identidad.replaceAll("[^0-9]", "");
    }

    /**
     * ¿Se puede dar de baja este cliente?
     *
     * <p>Un cliente inactiva ya está dada de baja. El servidor rechaza el DELETE si el
     * cliente tiene pedidos (trigger {@code trg_clientes_no_borrar_con_pedidos}).</p>
     */
    public static boolean puedeDarseDeBaja(@Nullable Cliente cliente) {
        return cliente != null && cliente.isActivo();
    }

    /**
     * ¿Se puede reactivar un cliente dado de baja?
     */
    public static boolean puedeReactivarse(@Nullable Cliente cliente) {
        return cliente != null && !cliente.isActivo();
    }

    /**
     * ¿Se puede borrar físicamente un cliente?
     *
     * <p>Solo si no tiene pedidos. El trigger del servidor lo valida también, pero acá
     * podemos predecirlo sin gastar un viaje de red.</p>
     */
    public static boolean puedeBorrarse(@Nullable Cliente cliente) {
        return cliente != null && cliente.getCantidadPedidos() == 0;
    }

    /**
     * ¿Se puede editar un cliente?
     */
    public static boolean puedeEditarse(@Nullable Cliente cliente) {
        return cliente != null;
    }

    /**
     * ¿Hay otro cliente con la misma identidad normalizada?
     *
     * @param idClienteActual id del cliente que se está editando (0 si es creación)
     */
    public static boolean existeOtroConIdentidad(@Nullable List<Cliente> clientes,
                                                 @Nullable String identidad,
                                                 int idClienteActual) {
        if (identidad == null || identidad.trim().isEmpty() || clientes == null) {
            return false;
        }
        String norm = normalizarIdentidad(identidad);
        if (norm.isEmpty()) {
            return false;
        }
        for (Cliente cliente : clientes) {
            if (cliente.getIdLocal() != idClienteActual
                    && norm.equals(normalizarIdentidad(cliente.getIdentidad()))) {
                return true;
            }
        }
        return false;
    }
}

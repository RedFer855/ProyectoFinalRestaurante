package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Mesa;

import java.util.List;
import java.util.Locale;

/**
 * Reglas de negocio del módulo Mesas (Fase 2c). Java puro, sin dependencias de Android.
 *
 * <p><b>Esto no es seguridad.</b> Quien impide que un mesero cree una mesa es la policy RLS
 * de Postgres. Acá se replican para no ofrecer una acción que el servidor va a rechazar.</p>
 */
public final class ReglasMesa {

    private ReglasMesa() {
    }

    /**
     * ¿Se puede cambiar el estado operativo de esta mesa?
     *
     * <p>Una mesa dada de baja ({@code activo == false}) no debería cambiar de estado —
     * el servidor lo rechaza con {@code id_estado = 1}. Acá se replica esa regla para no
     * ofrecer la acción.</p>
     */
    public static boolean puedeCambiarEstado(@Nullable Mesa mesa) {
        return mesa != null && mesa.isActivo();
    }

    /**
     * ¿Se puede editar la mesa (número, capacidad, ubicación)?
     *
     * <p>Solo las mesas activas. Una mesa dada de baja no tiene sentido editarla.</p>
     */
    public static boolean puedeEditarse(@Nullable Mesa mesa) {
        return mesa != null && mesa.isActivo();
    }

    /**
     * ¿Se puede dar de baja esta mesa?
     *
     * <p>Una mesa inactiva ya está dada de baja. El servidor rechaza el DELETE si la mesa
     * tiene pedidos asociados (trigger {@code trg_mesa_no_borrar}). Acá no verificamos
     * pedidos porque eso requiere un viaje de red; la UI puede mostrar la opción y el
     * servidor rechazará si no corresponde.</p>
     */
    public static boolean puedeDarseDeBaja(@Nullable Mesa mesa) {
        return mesa != null && mesa.isActivo();
    }

    /**
     * ¿Se puede reactivar una mesa dada de baja?
     */
    public static boolean puedeReactivarse(@Nullable Mesa mesa) {
        return mesa != null && !mesa.isActivo();
    }

    /**
     * ¿Se puede borrar físicamente una mesa?
     *
     * <p><b>Nunca.</b> El trigger del servidor lo rechaza siempre. Este método existe para
     * que la UI nunca ofrezca la opción.</p>
    */
    public static boolean puedeBorrarse(@Nullable Mesa mesa) {
        return false;
    }

    /**
     * Normaliza un número de mesa para búsqueda.
     */
    public static String normalizarNumero(int numero) {
        return String.valueOf(numero).trim();
    }

    /**
     * ¿Hay otra mesa con el mismo número? Útil para validar duplicados antes de crear o
     * editar.
     *
     * @param idMesaActual id de la mesa que se está editando (0 si es creación)
     */
    public static boolean existeOtraMesaConNumero(@Nullable List<Mesa> mesas, int numero,
                                                   int idMesaActual) {
        if (mesas == null) {
            return false;
        }
        for (Mesa mesa : mesas) {
            if (mesa.getIdLocal() != idMesaActual && mesa.getNumeroMesa() == numero) {
                return true;
            }
        }
        return false;
    }

    /**
     * Un daltónico no distingue colores: el estado se comunica por color + texto.
     * Este método existe para documentar esa regla de diseño (Plan Fase 2c, §4).
     */
    public static boolean necesitaEtiqueta() {
        return true;
    }
}

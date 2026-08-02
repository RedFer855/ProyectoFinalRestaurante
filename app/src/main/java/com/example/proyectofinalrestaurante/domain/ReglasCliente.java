package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Cliente;

/**
 * Espejo en el cliente de las reglas que impone el servidor sobre clientes (Plan Fase 2d).
 *
 * <p><b>Esto es para la interfaz, no es la seguridad.</b> La RLS y el trigger
 * {@code trg_clientes_no_borrar_con_pedidos} son quienes de verdad lo hacen cumplir; acá se
 * replica para no ofrecer una acción que el servidor va a rechazar — mismo patrón que
 * {@link ReglasMesa} y {@link ReglasMenu}.</p>
 */
public final class ReglasCliente {

    /**
     * Formato hondureño de identidad tras normalizar: 13 dígitos. No se valida el guion —
     * solo la cantidad de dígitos, después de quitar todo lo que no sea número (Plan Fase 2d,
     * §2.2 y §4).
     */
    public static final int DIGITOS_IDENTIDAD = 13;

    private ReglasCliente() {
    }

    /**
     * Deja solo los dígitos, igual que {@code regexp_replace(identidad, '[^0-9]', '', 'g')}
     * del servidor (Plan Fase 2d, §2.2). Devuelve {@code null} si no queda ningún dígito —
     * mismo criterio que el {@code nullif(...)} del servidor, para que "" y "----" normalicen
     * igual que "sin identidad" y no colisionen entre sí como si fueran la misma persona.
     */
    @Nullable
    public static String normalizarIdentidad(@Nullable String identidad) {
        if (identidad == null) {
            return null;
        }
        String soloDigitos = identidad.replaceAll("[^0-9]", "");
        return soloDigitos.isEmpty() ? null : soloDigitos;
    }

    /**
     * ¿Se puede borrar de verdad (no solo dar de baja)? Igual que
     * {@code ReglasMenu.puedeBorrarse(Categoria)}: sin pedidos asociados, un cliente cargado
     * por error no tiene por qué quedar para siempre (Plan Fase 2d, §2.3).
     */
    public static boolean puedeBorrarse(@Nullable Cliente cliente) {
        return cliente != null && cliente.getCantidadPedidos() == 0;
    }

    /** ¿El rol puede dar de baja/reactivar o borrar un cliente? Solo admin (Plan Fase 2d, §2.6). */
    public static boolean puedeDarDeBajaOBorrar(@Nullable String rol) {
        return Permisos.puede(rol, Modulo.CLIENTES, Accion.ELIMINAR);
    }
}

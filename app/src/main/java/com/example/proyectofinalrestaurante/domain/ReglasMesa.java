package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Mesa;

import java.util.List;

/**
 * Espejo en el cliente de las reglas que impone el servidor sobre las mesas (Plan Fase 2c).
 *
 * <p><b>Esto es para la interfaz, no es la seguridad.</b> Quien realmente impide borrar una
 * mesa o que un mesero la edite son el trigger {@code trg_mesa_no_borrar}, la función
 * {@code cambiar_estado_mesa()} y las policies RLS de Postgres. Acá se replican para no
 * ofrecer una acción que el servidor va a rechazar — mismo patrón que {@link ReglasMenu} con
 * los índices únicos.</p>
 *
 * <p>Que la app sea <b>más</b> estricta que el servidor es seguro; al revés es el problema.</p>
 */
public final class ReglasMesa {

    /**
     * Límite de caracteres de la ubicación. El DDL real de {@code mesa.ubicacion} no está
     * registrado en la bóveda (Plan Fase 2c, §2.5), así que este 100 es el techo que fija el
     * cliente para no gastar un viaje de red en algo que el servidor rechazaría. Única fuente
     * de verdad: {@link ValidadorMesa} referencia esta constante.
     */
    public static final int MAX_UBICACION_LONGITUD = 100;

    private ReglasMesa() {
    }

    // ------------------------------------------------------------------ estado operativo

    /**
     * ¿El rol puede cambiar el estado operativo de una mesa? Solo admin y mesero, según la
     * matriz de {@link Permisos} (MESAS: CAMBIAR_ESTADO) y la RLS que la respalda: cocina no
     * puede y un rol desconocido se niega por defecto.
     */
    public static boolean puedeCambiarEstado(@Nullable String rol) {
        return Permisos.puede(rol, Modulo.MESAS, Accion.CAMBIAR_ESTADO);
    }

    /**
     * ¿La mesa puede cambiar su estado operativo ahora? Una mesa dada de baja no cambia de
     * estado: el RPC {@code cambiar_estado_mesa()} solo actualiza filas con
     * {@code id_estado = 1} (Plan Fase 2c, §2.4).
     */
    public static boolean puedeCambiarEstadoOperativo(@Nullable Mesa mesa) {
        return mesa != null && mesa.esActiva();
    }

    /**
     * Las mesas no se borran, se dan de baja.
     *
     * <p>Regla explícita del servidor: el trigger {@code trg_mesa_no_borrar} hace fallar todo
     * {@code DELETE}, porque {@code pedido.id_mesa} referencia a {@code mesa} y borrar una
     * mesa rompería el historial de pedidos. La app no ofrece borrar, solo dar de baja.</p>
     *
     * @return siempre {@code true}: documenta un invariante, no una condición.
     */
    public static boolean lasMesasNoSeBorran() {
        return true;
    }

    // ------------------------------------------------------------------ unicidad

    /**
     * ¿Ya existe otra mesa con el mismo número en el salón? {@code idLocalAExcluir} se
     * excluye para que editar una mesa sin cambiarle el número no se detecte como duplicado.
     *
     * <p>Espeja el índice único del servidor sobre {@code numero_mesa} (Plan Fase 2c, §2.2):
     * dos mesas "4" en el mismo salón es un error de captura, no un caso de uso.</p>
     */
    public static boolean esNumeroDuplicado(@Nullable List<Mesa> mesas, int numero,
                                            int idLocalAExcluir) {
        if (mesas == null) {
            return false;
        }
        for (Mesa mesa : mesas) {
            if (mesa.getIdLocal() != idLocalAExcluir && mesa.getNumeroMesa() == numero) {
                return true;
            }
        }
        return false;
    }
}

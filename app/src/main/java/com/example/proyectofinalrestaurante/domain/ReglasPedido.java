package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.Pedido;
import com.example.proyectofinalrestaurante.domain.model.Platillo;

/**
 * Espejo en el cliente de la matriz que impone el RPC {@code avanzar_estado_pedido} del
 * servidor (Plan Fase 3, §2.5). Se replica para no ofrecer una transición que el servidor
 * va a rechazar — mismo patrón que {@link ReglasMesa}.
 *
 * <p><b>Esto es para la interfaz, no es la seguridad.</b> Quien realmente impide la
 * transición es el RPC, que evalúa el rol con {@code rol_actual()} en la base. Que la app
 * sea <b>más</b> estricta que el servidor es seguro; al revés es el problema.</p>
 */
public final class ReglasPedido {

    private ReglasPedido() {
    }

    /**
     * ¿El rol puede tomar pedidos? Es la matriz de {@link Permisos} tal cual: mesero tiene
     * {@code PEDIDOS: {CREAR, EDITAR}} y admin todo; cocina no. El FAB del tablero usa esta
     * regla para decidir si mostrarse (Plan Fase 3b, §6.3).
     */
    public static boolean puedeTomarPedido(@Nullable String rol) {
        return Permisos.puede(rol, Modulo.PEDIDOS, Accion.CREAR);
    }

    /**
     * ¿Se puede pedir este platillo? Solo si ya subió al servidor ({@code idServidor != null}),
     * porque el payload de {@code crear_pedido} manda {@code id_platillo} — un platillo que
     * todavía no se sincronizó no tendría a qué apuntar (§4.2). El selector lo usa para
     * ocultar esos platillos; el chequeo del sincronizador es el cinturón de seguridad.
     */
    public static boolean puedePedirse(@Nullable Platillo platillo) {
        return platillo != null && platillo.getIdServidor() != null && platillo.isActivo();
    }

    /**
     * ¿El rol puede llevar un pedido a {@code nuevo} estado?
     *
     * <p>Misma matriz que el RPC {@code avanzar_estado_pedido}: un pedido cerrado
     * ({@code Entregado}/{@code Cancelado}) no cambia; admin puede llevar a cualquier
     * estado abierto; cocina avanza de a uno por Pendiente/En preparación/Listo; mesero
     * solo marca Listo → Entregado.</p>
     */
    public static boolean puedeCambiarA(@Nullable String rol, @Nullable Pedido pedido,
                                        @Nullable EstadoPedido nuevo) {
        if (rol == null || pedido == null || pedido.getEstado() == null || nuevo == null) {
            return false;
        }
        EstadoPedido actual = pedido.getEstado();
        if (actual == EstadoPedido.ENTREGADO || actual == EstadoPedido.CANCELADO) {
            return false;
        }
        if (Permisos.ROL_ADMIN.equals(rol)) {
            return true;
        }
        if (Permisos.ROL_COCINA.equals(rol)) {
            return (nuevo == EstadoPedido.EN_PREPARACION || nuevo == EstadoPedido.LISTO)
                    && nuevo.getId() == actual.getId() + 1;
        }
        if (Permisos.ROL_MESERO.equals(rol)) {
            return nuevo == EstadoPedido.ENTREGADO && actual == EstadoPedido.LISTO;
        }
        return false;
    }

    /**
     * ¿El rol puede <b>cancelar</b> un pedido? Solo admin, como manda la matriz de
     * {@link Permisos} ({@code ELIMINAR} sobre {@code PEDIDOS} → en Pedidos es "Cancelar").
     */
    public static boolean puedeCancelar(@Nullable String rol) {
        return Permisos.puede(rol, Modulo.PEDIDOS, Accion.ELIMINAR);
    }

    /**
     * El siguiente estado natural del flujo de cocina: Pendiente → En preparación → Listo
     * → Entregado. Un estado de cierre no avanza ({@code null}). Lo usa la tarjeta para el
     * chip "avanzar": cocina avanza de a uno, mesero marca Entregado desde Listo.
     */
    @Nullable
    public static EstadoPedido siguienteDe(@Nullable EstadoPedido actual) {
        if (actual == null) {
            return null;
        }
        switch (actual) {
            case PENDIENTE:
                return EstadoPedido.EN_PREPARACION;
            case EN_PREPARACION:
                return EstadoPedido.LISTO;
            case LISTO:
                return EstadoPedido.ENTREGADO;
            default:
                return null;
        }
    }
}

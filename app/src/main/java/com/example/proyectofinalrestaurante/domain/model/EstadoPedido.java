package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Estado operativo de un pedido (Plan Fase 3, §2.1). Java puro: el {@code id} es el del
 * catálogo {@code estado_pedido} del servidor (Pendiente, En preparación, Listo, Entregado,
 * Cancelado) y las etiquetas y colores que la UI pinta viven en la capa de presentación,
 * no acá — mismo patrón que {@link EstadoMesa}.
 *
 * <p>{@code ENTREGADO} y {@code CANCELADO} son estados de cierre: un pedido cerrado no
 * cambia de estado (el RPC {@code avanzar_estado_pedido} lo rechaza — ver
 * {@code ReglasPedido}).</p>
 */
public enum EstadoPedido {
    PENDIENTE(1),
    EN_PREPARACION(2),
    LISTO(3),
    ENTREGADO(4),
    CANCELADO(5);

    private final int id;

    EstadoPedido(int id) {
        this.id = id;
    }

    /** Id del catálogo {@code estado_pedido} del servidor. */
    public int getId() {
        return id;
    }

    /**
     * El estado que corresponde a un id del catálogo del servidor.
     *
     * <p>Devuelve {@code null} si el id no existe en este APK. Es deliberado: el servidor
     * puede tener estados más nuevos que esta versión, y mapearlos a un estado que sí
     * conocemos sería mentirle al cocinero (un pedido "en espera de insumos" mostrado como
     * "Pendiente" se adelantaría por error). {@code null} llega a {@link Pedido#getEstado()}
     * y la UI lo pinta como estado desconocido.</p>
     */
    @Nullable
    public static EstadoPedido porId(int id) {
        for (EstadoPedido estado : values()) {
            if (estado.id == id) {
                return estado;
            }
        }
        return null;
    }
}

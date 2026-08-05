package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Tipo de un pedido: en mesa o para llevar (Plan Fase 3b, §6.1). Java puro, mismo molde que
 * {@link EstadoMesa}: el {@code id} es el del catálogo {@code tipo_pedido} del servidor y las
 * etiquetas viven en la UI.
 */
public enum TipoPedido {
    EN_MESA(1),
    PARA_LLEVAR(2);

    private final int id;

    TipoPedido(int id) {
        this.id = id;
    }

    /** Id del catálogo {@code tipo_pedido} del servidor. */
    public int getId() {
        return id;
    }

    /**
     * El tipo que corresponde a un id del catálogo del servidor. {@code null} si este APK
     * todavía no lo conoce — mismo criterio que {@link EstadoMesa#porId(int)}.
     */
    @Nullable
    public static TipoPedido porId(int id) {
        for (TipoPedido tipo : values()) {
            if (tipo.id == id) {
                return tipo;
            }
        }
        return null;
    }
}

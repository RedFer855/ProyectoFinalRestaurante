package com.example.proyectofinalrestaurante.ui.pedidos;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.Pedido;

import java.util.Collections;
import java.util.List;

/**
 * Estado único e inmutable del tablero de Pedidos (Plan Fase 3, E7). Mismo patrón que
 * {@code EstadoClientes}.
 *
 * <p>{@code filtro} es {@code null} para "todos"; cualquier {@code EstadoPedido} filtra la
 * ventana visible por ese estado. {@code hayMas} es derivado ({@code total > ventana}), nunca
 * una bandera suelta (regla 3 del protocolo, Plan Fase 3, §4.5).</p>
 */
public final class EstadoPedidos {

    private final boolean cargando;
    private final List<Pedido> pedidos;
    @Nullable private final EstadoPedido filtro;
    @Nullable private final String error;
    @Nullable private final String mensajeExito;
    private final boolean sincronizando;
    @Nullable private final String ultimoErrorSync;
    private final int total;
    private final int ventana;
    private final boolean hayMas;

    private EstadoPedidos(boolean cargando, List<Pedido> pedidos, @Nullable EstadoPedido filtro,
                          @Nullable String error, @Nullable String mensajeExito,
                          boolean sincronizando, @Nullable String ultimoErrorSync,
                          int total, int ventana, boolean hayMas) {
        this.cargando = cargando;
        this.pedidos = pedidos == null
                ? Collections.emptyList() : Collections.unmodifiableList(pedidos);
        this.filtro = filtro;
        this.error = error;
        this.mensajeExito = mensajeExito;
        this.sincronizando = sincronizando;
        this.ultimoErrorSync = ultimoErrorSync;
        this.total = total;
        this.ventana = ventana;
        this.hayMas = hayMas;
    }

    public static EstadoPedidos cargando() {
        return new EstadoPedidos(true, Collections.emptyList(), null,
                null, null, false, null, 0, 0, false);
    }

    public static EstadoPedidos conDatos(List<Pedido> pedidos, @Nullable EstadoPedido filtro,
                                         boolean sincronizando, @Nullable String ultimoErrorSync,
                                         int total, int ventana) {
        return new EstadoPedidos(false, pedidos, filtro, null, null,
                sincronizando, ultimoErrorSync, total, ventana, total > ventana);
    }

    public EstadoPedidos conMensaje(String mensaje) {
        return new EstadoPedidos(cargando, pedidos, filtro, error, mensaje,
                sincronizando, ultimoErrorSync, total, ventana, hayMas);
    }

    public EstadoPedidos sinMensaje() {
        return new EstadoPedidos(cargando, pedidos, filtro, error, null,
                sincronizando, ultimoErrorSync, total, ventana, hayMas);
    }

    public EstadoPedidos conError(String mensaje) {
        return new EstadoPedidos(cargando, pedidos, filtro, mensaje, null,
                sincronizando, ultimoErrorSync, total, ventana, hayMas);
    }

    public boolean isCargando() {
        return cargando;
    }

    public List<Pedido> getPedidos() {
        return pedidos;
    }

    @Nullable
    public EstadoPedido getFiltro() {
        return filtro;
    }

    @Nullable
    public String getError() {
        return error;
    }

    @Nullable
    public String getMensajeExito() {
        return mensajeExito;
    }

    public boolean isSincronizando() {
        return sincronizando;
    }

    @Nullable
    public String getUltimoErrorSync() {
        return ultimoErrorSync;
    }

    public int getTotal() {
        return total;
    }

    public int getVentana() {
        return ventana;
    }

    public boolean isHayMas() {
        return hayMas;
    }

    public boolean isVacio() {
        return !cargando && pedidos.isEmpty();
    }
}

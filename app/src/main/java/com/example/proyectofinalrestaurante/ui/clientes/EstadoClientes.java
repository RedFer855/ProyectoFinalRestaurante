package com.example.proyectofinalrestaurante.ui.clientes;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Cliente;

import java.util.Collections;
import java.util.List;

/**
 * Estado único e inmutable de la pantalla de Clientes (Plan Fase 2d, E5). Mismo patrón que
 * {@code EstadoMesas}/{@code EstadoEmpleados}.
 *
 * <p>{@code filtroActivo} es {@code null} para "todos", {@code true} para "activos" y
 * {@code false} para "inactivos" — el filtro por estado que advierte el Plan Fase 2d, §5.5.</p>
 */
public final class EstadoClientes {

    private final boolean cargando;
    private final List<Cliente> clientes;
    @Nullable private final Boolean filtroActivo;
    private final String textoBusqueda;
    @Nullable private final String error;
    @Nullable private final String mensajeExito;
    private final boolean sincronizando;
    private final int cambiosSinSubir;
    @Nullable private final String ultimoErrorSync;
    private final int totalSinFiltrar;

    private EstadoClientes(boolean cargando, List<Cliente> clientes,
                           @Nullable Boolean filtroActivo, String textoBusqueda,
                           @Nullable String error, @Nullable String mensajeExito,
                           boolean sincronizando, int cambiosSinSubir,
                           @Nullable String ultimoErrorSync, int totalSinFiltrar) {
        this.cargando = cargando;
        this.clientes = clientes == null ? Collections.emptyList() : Collections.unmodifiableList(clientes);
        this.filtroActivo = filtroActivo;
        this.textoBusqueda = textoBusqueda == null ? "" : textoBusqueda;
        this.error = error;
        this.mensajeExito = mensajeExito;
        this.sincronizando = sincronizando;
        this.cambiosSinSubir = cambiosSinSubir;
        this.ultimoErrorSync = ultimoErrorSync;
        this.totalSinFiltrar = totalSinFiltrar;
    }

    public static EstadoClientes cargando() {
        return new EstadoClientes(true, Collections.emptyList(), null, "",
                null, null, false, 0, null, 0);
    }

    public static EstadoClientes conDatos(List<Cliente> clientes, @Nullable Boolean filtroActivo,
                                          String textoBusqueda, boolean sincronizando,
                                          int cambiosSinSubir, @Nullable String ultimoErrorSync,
                                          int totalSinFiltrar) {
        return new EstadoClientes(false, clientes, filtroActivo, textoBusqueda, null, null,
                sincronizando, cambiosSinSubir, ultimoErrorSync, totalSinFiltrar);
    }

    public EstadoClientes conMensaje(String mensaje) {
        return new EstadoClientes(cargando, clientes, filtroActivo, textoBusqueda, error, mensaje,
                sincronizando, cambiosSinSubir, ultimoErrorSync, totalSinFiltrar);
    }

    public EstadoClientes sinMensaje() {
        return new EstadoClientes(cargando, clientes, filtroActivo, textoBusqueda, error, null,
                sincronizando, cambiosSinSubir, ultimoErrorSync, totalSinFiltrar);
    }

    public EstadoClientes conError(String mensaje) {
        return new EstadoClientes(cargando, clientes, filtroActivo, textoBusqueda, mensaje, null,
                sincronizando, cambiosSinSubir, ultimoErrorSync, totalSinFiltrar);
    }

    public boolean isCargando() {
        return cargando;
    }

    public List<Cliente> getClientes() {
        return clientes;
    }

    @Nullable
    public Boolean getFiltroActivo() {
        return filtroActivo;
    }

    public String getTextoBusqueda() {
        return textoBusqueda;
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

    public int getCambiosSinSubir() {
        return cambiosSinSubir;
    }

    @Nullable
    public String getUltimoErrorSync() {
        return ultimoErrorSync;
    }

    public boolean isVacio() {
        return !cargando && clientes.isEmpty();
    }

    public boolean isVacioPorFiltro() {
        return isVacio() && totalSinFiltrar > 0;
    }
}

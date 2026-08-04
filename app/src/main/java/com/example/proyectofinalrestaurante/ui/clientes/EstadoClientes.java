package com.example.proyectofinalrestaurante.ui.clientes;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Cliente;

import java.util.Collections;
import java.util.List;

/**
 * Estado único e inmutable de la pantalla de Clientes (Fase 2d).
 *
 * <p>Mismo patrón que {@link com.example.proyectofinalrestaurante.ui.mesas.EstadoMesas}:
 * un objeto inmutable que la UI repinta en cada cambio, con el filtro y la búsqueda
 * aplicados para que el Fragment no tenga estado propio.</p>
 */
public final class EstadoClientes {

    /** Valor de {@code filtroEstado} cuando el filtro es "Todos". */
    @Nullable
    public static final Boolean SIN_FILTRO = null;

    private final boolean cargando;
    private final List<Cliente> clientes;
    @Nullable private final Boolean filtroActivo;
    @Nullable private final String textoBusqueda;
    @Nullable private final String error;
    @Nullable private final String mensajeExito;
    private final boolean sincronizando;
    private final int cambiosSinSubir;
    @Nullable private final String ultimoErrorSync;

    private EstadoClientes(boolean cargando, List<Cliente> clientes,
                           @Nullable Boolean filtroActivo,
                           @Nullable String textoBusqueda,
                           @Nullable String error, @Nullable String mensajeExito,
                           boolean sincronizando, int cambiosSinSubir,
                           @Nullable String ultimoErrorSync) {
        this.cargando = cargando;
        this.clientes = clientes == null ? Collections.emptyList()
                : Collections.unmodifiableList(clientes);
        this.filtroActivo = filtroActivo;
        this.textoBusqueda = textoBusqueda == null ? "" : textoBusqueda;
        this.error = error;
        this.mensajeExito = mensajeExito;
        this.sincronizando = sincronizando;
        this.cambiosSinSubir = cambiosSinSubir;
        this.ultimoErrorSync = ultimoErrorSync;
    }

    public static EstadoClientes cargando() {
        return new EstadoClientes(true, Collections.emptyList(),
                SIN_FILTRO, "", null, null, false, 0, null);
    }

    public static EstadoClientes conDatos(List<Cliente> clientes,
                                          @Nullable Boolean filtroActivo,
                                          @Nullable String textoBusqueda,
                                          boolean sincronizando, int cambiosSinSubir,
                                          @Nullable String ultimoErrorSync) {
        return new EstadoClientes(false, clientes, filtroActivo, textoBusqueda,
                null, null, sincronizando, cambiosSinSubir, ultimoErrorSync);
    }

    public static EstadoClientes error(String mensaje) {
        return new EstadoClientes(false, Collections.emptyList(),
                SIN_FILTRO, "", mensaje, null, false, 0, null);
    }

    public EstadoClientes conMensaje(String mensaje) {
        return new EstadoClientes(cargando, clientes, filtroActivo, textoBusqueda,
                error, mensaje, sincronizando, cambiosSinSubir, ultimoErrorSync);
    }

    public EstadoClientes sinMensaje() {
        return new EstadoClientes(cargando, clientes, filtroActivo, textoBusqueda,
                error, null, sincronizando, cambiosSinSubir, ultimoErrorSync);
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

    @Nullable
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
        return !cargando && error == null && clientes.isEmpty();
    }

    public boolean isVacioPorFiltro() {
        return isVacio() && (filtroActivo != SIN_FILTRO || !textoBusqueda.isEmpty());
    }
}

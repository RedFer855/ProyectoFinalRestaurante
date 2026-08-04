package com.example.proyectofinalrestaurante.ui.mesas;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.Mesa;

import java.util.Collections;
import java.util.List;

/**
 * Estado único e inmutable de la pantalla de Mesas (Fase 2c).
 *
 * <p>Mismo patrón que {@link com.example.proyectofinalrestaurante.ui.menu.EstadoMenu}:
 * un objeto inmutable que la UI repinta en cada cambio, con el filtro y la búsqueda
 * aplicados para que el Fragment no tenga estado propio.</p>
 */
public final class EstadoMesas {

    /** Valor de {@code filtroEstado} cuando el filtro es "Todos". */
    @Nullable
    public static final EstadoMesa SIN_FILTRO = null;

    private final boolean cargando;
    private final List<Mesa> mesas;
    @Nullable private final EstadoMesa filtroEstado;
    @Nullable private final String textoBusqueda;
    @Nullable private final String error;
    @Nullable private final String mensajeExito;
    private final boolean sincronizando;
    private final int cambiosSinSubir;
    @Nullable private final String ultimoErrorSync;

    private EstadoMesas(boolean cargando, List<Mesa> mesas,
                        @Nullable EstadoMesa filtroEstado,
                        @Nullable String textoBusqueda,
                        @Nullable String error, @Nullable String mensajeExito,
                        boolean sincronizando, int cambiosSinSubir,
                        @Nullable String ultimoErrorSync) {
        this.cargando = cargando;
        this.mesas = mesas == null ? Collections.emptyList()
                : Collections.unmodifiableList(mesas);
        this.filtroEstado = filtroEstado;
        this.textoBusqueda = textoBusqueda == null ? "" : textoBusqueda;
        this.error = error;
        this.mensajeExito = mensajeExito;
        this.sincronizando = sincronizando;
        this.cambiosSinSubir = cambiosSinSubir;
        this.ultimoErrorSync = ultimoErrorSync;
    }

    public static EstadoMesas cargando() {
        return new EstadoMesas(true, Collections.emptyList(),
                SIN_FILTRO, "", null, null, false, 0, null);
    }

    public static EstadoMesas conDatos(List<Mesa> mesas,
                                       @Nullable EstadoMesa filtroEstado,
                                       @Nullable String textoBusqueda,
                                       boolean sincronizando, int cambiosSinSubir,
                                       @Nullable String ultimoErrorSync) {
        return new EstadoMesas(false, mesas, filtroEstado, textoBusqueda,
                null, null, sincronizando, cambiosSinSubir, ultimoErrorSync);
    }

    public static EstadoMesas error(String mensaje) {
        return new EstadoMesas(false, Collections.emptyList(),
                SIN_FILTRO, "", mensaje, null, false, 0, null);
    }

    public EstadoMesas conMensaje(String mensaje) {
        return new EstadoMesas(cargando, mesas, filtroEstado, textoBusqueda,
                error, mensaje, sincronizando, cambiosSinSubir, ultimoErrorSync);
    }

    public EstadoMesas sinMensaje() {
        return new EstadoMesas(cargando, mesas, filtroEstado, textoBusqueda,
                error, null, sincronizando, cambiosSinSubir, ultimoErrorSync);
    }

    public boolean isCargando() {
        return cargando;
    }

    public List<Mesa> getMesas() {
        return mesas;
    }

    @Nullable
    public EstadoMesa getFiltroEstado() {
        return filtroEstado;
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
        return !cargando && error == null && mesas.isEmpty();
    }

    public boolean isVacioPorFiltro() {
        return isVacio() && (filtroEstado != SIN_FILTRO || !textoBusqueda.isEmpty());
    }
}

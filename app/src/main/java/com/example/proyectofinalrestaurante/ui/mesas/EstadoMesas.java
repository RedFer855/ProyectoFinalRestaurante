package com.example.proyectofinalrestaurante.ui.mesas;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.Mesa;

import java.util.Collections;
import java.util.List;

/**
 * Estado único e inmutable de la pantalla de Mesas (Plan Fase 2c, E5). Mismo patrón que
 * {@code EstadoEmpleados}/{@code EstadoMenu}: cuatro estados reales (cargando, con datos,
 * vacío, error), con {@link #isVacio()} derivado de la lista en vez de guardado como bandera.
 *
 * <p>{@code filtroEstado} es {@code null} para "todos" — el mismo valor centinela que ya
 * usaba la maqueta de la Fase 1c. {@link #isVacioPorFiltro()} distingue "no hay mesas" de
 * "el filtro no encontró nada", igual que en Empleados.</p>
 */
public final class EstadoMesas {

    private final boolean cargando;
    private final List<Mesa> mesas;
    private final List<EstadoMesa> estadosDisponibles;
    @Nullable private final EstadoMesa filtroEstado;
    private final String textoBusqueda;
    @Nullable private final String error;
    @Nullable private final String mensajeExito;
    private final boolean sincronizando;
    private final int cambiosSinSubir;
    @Nullable private final String ultimoErrorSync;
    private final int totalSinFiltrar;

    private EstadoMesas(boolean cargando, List<Mesa> mesas, List<EstadoMesa> estadosDisponibles,
                        @Nullable EstadoMesa filtroEstado, String textoBusqueda,
                        @Nullable String error, @Nullable String mensajeExito,
                        boolean sincronizando, int cambiosSinSubir,
                        @Nullable String ultimoErrorSync, int totalSinFiltrar) {
        this.cargando = cargando;
        this.mesas = mesas == null ? Collections.emptyList() : Collections.unmodifiableList(mesas);
        this.estadosDisponibles = estadosDisponibles == null
                ? Collections.emptyList() : Collections.unmodifiableList(estadosDisponibles);
        this.filtroEstado = filtroEstado;
        this.textoBusqueda = textoBusqueda == null ? "" : textoBusqueda;
        this.error = error;
        this.mensajeExito = mensajeExito;
        this.sincronizando = sincronizando;
        this.cambiosSinSubir = cambiosSinSubir;
        this.ultimoErrorSync = ultimoErrorSync;
        this.totalSinFiltrar = totalSinFiltrar;
    }

    public static EstadoMesas cargando() {
        return new EstadoMesas(true, Collections.emptyList(), Collections.emptyList(), null, "",
                null, null, false, 0, null, 0);
    }

    public static EstadoMesas conDatos(List<Mesa> mesas, List<EstadoMesa> estadosDisponibles,
                                       @Nullable EstadoMesa filtroEstado, String textoBusqueda,
                                       boolean sincronizando, int cambiosSinSubir,
                                       @Nullable String ultimoErrorSync, int totalSinFiltrar) {
        return new EstadoMesas(false, mesas, estadosDisponibles, filtroEstado, textoBusqueda,
                null, null, sincronizando, cambiosSinSubir, ultimoErrorSync, totalSinFiltrar);
    }

    public EstadoMesas conMensaje(String mensaje) {
        return new EstadoMesas(cargando, mesas, estadosDisponibles, filtroEstado, textoBusqueda,
                error, mensaje, sincronizando, cambiosSinSubir, ultimoErrorSync, totalSinFiltrar);
    }

    public EstadoMesas sinMensaje() {
        return new EstadoMesas(cargando, mesas, estadosDisponibles, filtroEstado, textoBusqueda,
                error, null, sincronizando, cambiosSinSubir, ultimoErrorSync, totalSinFiltrar);
    }

    public EstadoMesas conError(String mensaje) {
        return new EstadoMesas(cargando, mesas, estadosDisponibles, filtroEstado, textoBusqueda,
                mensaje, null, sincronizando, cambiosSinSubir, ultimoErrorSync, totalSinFiltrar);
    }

    public boolean isCargando() {
        return cargando;
    }

    public List<Mesa> getMesas() {
        return mesas;
    }

    public List<EstadoMesa> getEstadosDisponibles() {
        return estadosDisponibles;
    }

    @Nullable
    public EstadoMesa getFiltroEstado() {
        return filtroEstado;
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
        return !cargando && mesas.isEmpty();
    }

    public boolean isVacioPorFiltro() {
        return isVacio() && totalSinFiltrar > 0;
    }
}

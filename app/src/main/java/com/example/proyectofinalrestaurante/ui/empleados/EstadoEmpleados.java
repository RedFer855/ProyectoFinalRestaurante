package com.example.proyectofinalrestaurante.ui.empleados;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Empleado;

import java.util.Collections;
import java.util.List;

/**
 * Estado único e inmutable de la pantalla de Empleados (Plan Fase 1d, E4; extendido al
 * migrar el módulo a offline-first).
 *
 * <p>Modela los <b>cuatro</b> estados reales — cargando, con datos, vacío y error — con
 * {@link #isVacio()} derivado en vez de guardado como bandera: una bandera suelta puede
 * terminar contradiciendo a la lista.</p>
 *
 * <p>Desde offline-first suma la sincronización: {@code sincronizando} alimenta el indicador
 * global, {@code cambiosSinSubir} cuenta las filas locales que todavía no llegaron al
 * servidor y {@code ultimoErrorSync} se muestra cuando una subida falló de forma permanente
 * — el caso típico es el trigger {@code proteger_admins()} rechazando el cambio.</p>
 *
 * <p>{@link #isVacioPorFiltro()} distingue "no hay empleados" de "el filtro no encontró
 * nada": el mensaje correcto es distinto y confundirlos hace pensar que se perdieron datos.</p>
 */
public final class EstadoEmpleados {

    private final boolean cargando;
    private final List<Empleado> empleados;
    private final String textoBusqueda;
    @Nullable private final String error;
    /** Evento de un solo disparo: se consume con {@code sinMensaje()} tras mostrarlo. */
    @Nullable private final String mensajeExito;
    private final boolean sincronizando;
    private final int cambiosSinSubir;
    @Nullable private final String ultimoErrorSync;
    /** Total sin filtrar, para poder distinguir "vacío" de "el filtro no encontró nada". */
    private final int totalSinFiltrar;

    private EstadoEmpleados(boolean cargando, List<Empleado> empleados, String textoBusqueda,
                            @Nullable String error, @Nullable String mensajeExito,
                            boolean sincronizando, int cambiosSinSubir,
                            @Nullable String ultimoErrorSync, int totalSinFiltrar) {
        this.cargando = cargando;
        this.empleados = empleados == null ? Collections.emptyList()
                : Collections.unmodifiableList(empleados);
        this.textoBusqueda = textoBusqueda == null ? "" : textoBusqueda;
        this.error = error;
        this.mensajeExito = mensajeExito;
        this.sincronizando = sincronizando;
        this.cambiosSinSubir = cambiosSinSubir;
        this.ultimoErrorSync = ultimoErrorSync;
        this.totalSinFiltrar = totalSinFiltrar;
    }

    public static EstadoEmpleados inicial() {
        return new EstadoEmpleados(false, Collections.emptyList(), "", null, null,
                false, 0, null, 0);
    }

    public static EstadoEmpleados cargando() {
        return new EstadoEmpleados(true, Collections.emptyList(), "", null, null,
                false, 0, null, 0);
    }

    public static EstadoEmpleados conDatos(List<Empleado> empleados, String textoBusqueda,
                                           boolean sincronizando, int cambiosSinSubir,
                                           @Nullable String ultimoErrorSync,
                                           int totalSinFiltrar) {
        return new EstadoEmpleados(false, empleados, textoBusqueda, null, null,
                sincronizando, cambiosSinSubir, ultimoErrorSync, totalSinFiltrar);
    }

    public static EstadoEmpleados error(String mensaje) {
        return new EstadoEmpleados(false, Collections.emptyList(), "", mensaje, null,
                false, 0, null, 0);
    }

    /** Copia con un aviso de éxito pendiente de mostrar. */
    public EstadoEmpleados conMensaje(String mensaje) {
        return new EstadoEmpleados(cargando, empleados, textoBusqueda, error, mensaje,
                sincronizando, cambiosSinSubir, ultimoErrorSync, totalSinFiltrar);
    }

    /** Copia sin el aviso: se llama después de mostrarlo, para que no se repita. */
    public EstadoEmpleados sinMensaje() {
        return new EstadoEmpleados(cargando, empleados, textoBusqueda, error, null,
                sincronizando, cambiosSinSubir, ultimoErrorSync, totalSinFiltrar);
    }

    /** Copia con un error puntual (por ejemplo, el alta sin conexión). */
    public EstadoEmpleados conError(String mensaje) {
        return new EstadoEmpleados(cargando, empleados, textoBusqueda, mensaje, null,
                sincronizando, cambiosSinSubir, ultimoErrorSync, totalSinFiltrar);
    }

    public boolean isCargando() {
        return cargando;
    }

    public List<Empleado> getEmpleados() {
        return empleados;
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

    /** No hay nada que mostrar, pero tampoco es un error ni está cargando. */
    public boolean isVacio() {
        return !cargando && error == null && empleados.isEmpty();
    }

    /** Está vacío <b>porque el filtro no encontró nada</b>, no porque no haya empleados. */
    public boolean isVacioPorFiltro() {
        return isVacio() && totalSinFiltrar > 0;
    }
}

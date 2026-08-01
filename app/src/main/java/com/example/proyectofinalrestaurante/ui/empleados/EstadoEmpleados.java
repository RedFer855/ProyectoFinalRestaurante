package com.example.proyectofinalrestaurante.ui.empleados;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Empleado;

import java.util.Collections;
import java.util.List;

/**
 * Estado único e inmutable de la pantalla de Empleados (Plan Fase 1d, E4).
 *
 * <p>Modela los <b>cuatro</b> estados reales, no dos: cargando, con datos, vacío y
 * error. {@link #isVacio()} se deriva en vez de guardarse como bandera aparte —
 * una bandera suelta puede contradecir a la lista.</p>
 */
public final class EstadoEmpleados {

    private final boolean cargando;
    private final List<Empleado> empleados;
    @Nullable private final String error;
    /** Evento de un solo disparo: se consume con {@code sinMensaje()} tras mostrarlo. */
    @Nullable private final String mensajeExito;

    private EstadoEmpleados(boolean cargando, List<Empleado> empleados,
                            @Nullable String error, @Nullable String mensajeExito) {
        this.cargando = cargando;
        this.empleados = empleados == null ? Collections.emptyList()
                : Collections.unmodifiableList(empleados);
        this.error = error;
        this.mensajeExito = mensajeExito;
    }

    public static EstadoEmpleados inicial() {
        return new EstadoEmpleados(false, Collections.emptyList(), null, null);
    }

    public static EstadoEmpleados cargando() {
        return new EstadoEmpleados(true, Collections.emptyList(), null, null);
    }

    public static EstadoEmpleados conDatos(List<Empleado> empleados) {
        return new EstadoEmpleados(false, empleados, null, null);
    }

    public static EstadoEmpleados error(String mensaje) {
        return new EstadoEmpleados(false, Collections.emptyList(), mensaje, null);
    }

    /** Copia con un aviso de éxito pendiente de mostrar. */
    public EstadoEmpleados conMensaje(String mensaje) {
        return new EstadoEmpleados(cargando, empleados, error, mensaje);
    }

    /** Copia sin el aviso: se llama después de mostrarlo, para que no se repita. */
    public EstadoEmpleados sinMensaje() {
        return new EstadoEmpleados(cargando, empleados, error, null);
    }

    public boolean isCargando() {
        return cargando;
    }

    public List<Empleado> getEmpleados() {
        return empleados;
    }

    @Nullable
    public String getError() {
        return error;
    }

    @Nullable
    public String getMensajeExito() {
        return mensajeExito;
    }

    /** No hay nada que mostrar, pero tampoco es un error ni está cargando. */
    public boolean isVacio() {
        return !cargando && error == null && empleados.isEmpty();
    }
}

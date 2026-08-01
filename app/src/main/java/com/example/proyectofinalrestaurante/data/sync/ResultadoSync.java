package com.example.proyectofinalrestaurante.data.sync;

import androidx.annotation.Nullable;

/**
 * Resultado de una pasada del {@link SincronizadorMenu} (Plan Fase 2b, E5).
 *
 * <p>El worker necesita tres salidas posibles: la pasada terminó bien ({@code OK}), un error
 * <b>transitorio</b> cortó el drenado (hay que reintentar con backoff), o un error
 * <b>permanente</b> descartó una operación (el trabajo "terminó", pero el usuario debe
 * enterarse de que algo no se subió). Por eso no alcanza con un booleano: el mensaje del
 * error permanente es lo que alimenta {@code EstadoSincronizacion.ultimoError} (§4.5).</p>
 */
public final class ResultadoSync {

    public enum Estado {
        OK,
        TRANSIENTE,
        PERMANENTE
    }

    private final Estado estado;
    @Nullable private final String mensaje;

    private ResultadoSync(Estado estado, @Nullable String mensaje) {
        this.estado = estado;
        this.mensaje = mensaje;
    }

    public static ResultadoSync ok() {
        return new ResultadoSync(Estado.OK, null);
    }

    public static ResultadoSync transitorio(@Nullable String mensaje) {
        return new ResultadoSync(Estado.TRANSIENTE, mensaje);
    }

    public static ResultadoSync permanente(@Nullable String mensaje) {
        return new ResultadoSync(Estado.PERMANENTE, mensaje);
    }

    public boolean esOk() {
        return estado == Estado.OK;
    }

    public boolean esTransitorio() {
        return estado == Estado.TRANSIENTE;
    }

    public boolean esPermanente() {
        return estado == Estado.PERMANENTE;
    }

    @Nullable
    public String getMensaje() {
        return mensaje;
    }
}

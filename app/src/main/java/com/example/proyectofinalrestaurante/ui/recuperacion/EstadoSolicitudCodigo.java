package com.example.proyectofinalrestaurante.ui.recuperacion;

/**
 * Estado único e inmutable del paso 1 de recuperación (pedir el código).
 * Sigue [[UiState Inmutable y Flujo Unidireccional]]. El evento de navegación
 * ({@link #getCorreoConfirmado()}) es de un solo disparo: la Activity navega y
 * llama {@code onNavegacionConsumida()} — no se replica P-013.
 */
public final class EstadoSolicitudCodigo {

    private final boolean cargando;
    private final String error;
    private final String correoConfirmado;

    private EstadoSolicitudCodigo(boolean cargando, String error, String correoConfirmado) {
        this.cargando = cargando;
        this.error = error;
        this.correoConfirmado = correoConfirmado;
    }

    public static EstadoSolicitudCodigo inicial() {
        return new EstadoSolicitudCodigo(false, null, null);
    }

    public static EstadoSolicitudCodigo cargando() {
        return new EstadoSolicitudCodigo(true, null, null);
    }

    public static EstadoSolicitudCodigo error(String mensaje) {
        return new EstadoSolicitudCodigo(false, mensaje, null);
    }

    public static EstadoSolicitudCodigo confirmado(String correo) {
        return new EstadoSolicitudCodigo(false, null, correo);
    }

    public boolean isCargando() {
        return cargando;
    }

    public String getError() {
        return error;
    }

    /** Correo confirmado como enviado — evento one-shot para navegar al paso 2. */
    public String getCorreoConfirmado() {
        return correoConfirmado;
    }
}

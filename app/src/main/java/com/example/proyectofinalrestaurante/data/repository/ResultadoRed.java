package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;

/**
 * Resultado de una llamada remota, con el código HTTP (Plan Fase 2b, E5).
 *
 * <p>El outbox necesita saber si un fallo es <b>permanente</b> o <b>transitorio</b> para
 * decidir si descarta la operación o la reintenta. Eso depende del código HTTP, no del
 * mensaje: un 409 es permanente, un 500 es transitorio. Por eso este tipo viaja por el
 * canal de red, y {@code domain.Result} (que no sabe de códigos) queda solo para la UI.</p>
 */
public final class ResultadoRed<T> {

    private final boolean exitoso;
    @Nullable private final T valor;
    @Nullable private final String mensaje;
    /** Código HTTP, o {@code ClasificadorDeError.SIN_CODIGO} si no hubo respuesta. */
    private final int codigoHttp;

    private ResultadoRed(boolean exitoso, @Nullable T valor, @Nullable String mensaje,
                         int codigoHttp) {
        this.exitoso = exitoso;
        this.valor = valor;
        this.mensaje = mensaje;
        this.codigoHttp = codigoHttp;
    }

    public static <T> ResultadoRed<T> exito(T valor) {
        return new ResultadoRed<>(true, valor, null, 200);
    }

    public static <T> ResultadoRed<T> fallo(int codigoHttp, String mensaje) {
        return new ResultadoRed<>(false, null, mensaje, codigoHttp);
    }

    public boolean isExitoso() {
        return exitoso;
    }

    @Nullable
    public T getValor() {
        return valor;
    }

    @Nullable
    public String getMensaje() {
        return mensaje;
    }

    public int getCodigoHttp() {
        return codigoHttp;
    }
}

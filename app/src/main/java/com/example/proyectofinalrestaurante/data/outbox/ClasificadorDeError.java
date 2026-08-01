package com.example.proyectofinalrestaurante.data.outbox;

import java.io.IOException;

import retrofit2.Response;

/**
 * Clasifica un fallo de red como transitorio o permanente (Plan Fase 2b, §4.4).
 *
 * <p>Es la política de reintentos del outbox y por eso vive sola, sin DAOs ni Retrofit:
 * una operación que falla con un error <b>transitorio</b> (sin red, 5xx, timeout, 401 por
 * sesión vencida) se reintenta con backoff; una que falla con uno <b>permanente</b> (el
 * resto de los 4xx, p. ej. un nombre duplicado con 409) se descarta para no bloquear a las
 * demás.</p>
 *
 * <p>Dependencia documentada (Plan Fase 2b, §4.4): el 409 de {@code uq_platillo_nombre} es
 * lo que evita que un {@code POST} reintentado duplique filas. Si algún día se quita ese
 * índice, aparecen platillos duplicados y nadie va a saber por qué.</p>
 */
public final class ClasificadorDeError {

    private ClasificadorDeError() {
    }

    /**
     * Los 401/408/429 y los 5xx se reintentan; el resto de los 4xx son permanentes.
     * Un {@code IOException} es "no llegó respuesta", que es transitorio por definición.
     */
    public static boolean esTransitorio(Throwable error) {
        return error instanceof IOException;
    }

    /**
     * Un {@link Response} HTTP no exitosa: decide si conviene reintentarla.
     *
     * @param codigoHttp el código de la respuesta, o {@link #SIN_CODIGO} si no se tiene.
     */
    public static boolean esTransitorio(int codigoHttp) {
        if (codigoHttp == SIN_CODIGO) {
            return true;
        }
        return codigoHttp == 401 || codigoHttp == 408 || codigoHttp == 429
                || codigoHttp >= 500;
    }

    /** Marca de "no hay código": la usa el drenador cuando solo hay una excepción. */
    public static final int SIN_CODIGO = -1;
}

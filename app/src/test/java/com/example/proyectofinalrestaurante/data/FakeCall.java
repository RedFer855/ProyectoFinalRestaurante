package com.example.proyectofinalrestaurante.data;

import java.io.IOException;

import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * {@code Call<T>} fake para tests de repositorio: devuelve una {@link Response} o lanza
 * una excepción ya armada, sin tocar la red. Reemplaza a Mockito, que no es dependencia
 * del proyecto (ver P-020 en Deuda Técnica - Pendientes).
 */
public final class FakeCall<T> implements Call<T> {

    private final Response<T> respuesta;
    private final IOException fallo;

    private FakeCall(Response<T> respuesta, IOException fallo) {
        this.respuesta = respuesta;
        this.fallo = fallo;
    }

    public static <T> FakeCall<T> deRespuesta(Response<T> respuesta) {
        return new FakeCall<>(respuesta, null);
    }

    public static <T> FakeCall<T> deFallo(IOException fallo) {
        return new FakeCall<>(null, fallo);
    }

    @Override
    public Response<T> execute() throws IOException {
        if (fallo != null) {
            throw fallo;
        }
        return respuesta;
    }

    @Override
    public void enqueue(Callback<T> callback) {
        throw new UnsupportedOperationException("Este fake solo soporta execute() síncrono");
    }

    @Override
    public boolean isExecuted() {
        return false;
    }

    @Override
    public void cancel() {
        // No-op: no hay nada async que cancelar en el fake.
    }

    @Override
    public boolean isCanceled() {
        return false;
    }

    @Override
    public Call<T> clone() {
        return fallo != null ? deFallo(fallo) : deRespuesta(respuesta);
    }

    @Override
    public Request request() {
        return null;
    }

    @Override
    public Timeout timeout() {
        return Timeout.NONE;
    }
}

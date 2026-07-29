package com.example.proyectofinalrestaurante.domain;

/**
 * Resultado de una operación que puede fallar (ver contexto/20 - Patrones/Result Pattern.md).
 * Se usa en vez de dejar que una excepción de red llegue cruda hasta la UI.
 */
public final class Result<T> {

    private final boolean success;
    private final T value;
    private final String error;

    private Result(T value) {
        this.success = true;
        this.value = value;
        this.error = null;
    }

    private Result(String error) {
        this.success = false;
        this.value = null;
        this.error = error;
    }

    public static <T> Result<T> ok(T value) {
        return new Result<>(value);
    }

    public static <T> Result<T> fail(String error) {
        return new Result<>(error);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getValue() {
        return value;
    }

    public String getError() {
        return error;
    }
}

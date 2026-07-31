package com.example.proyectofinalrestaurante.ui.recuperacion;

import com.example.proyectofinalrestaurante.domain.RequisitoContrasenia;

import java.util.Collections;
import java.util.Set;

/**
 * Estado único e inmutable del paso 2 de recuperación (código + nueva contraseña).
 * El evento {@link #isCambioExitoso()} es de un solo disparo: la Activity muestra el
 * Snackbar, navega y llama {@code onNavegacionConsumida()} — no se replica P-013.
 * {@link #segundosRestantes} vive acá (proyectado desde el ViewModel) para que el
 * contador de 60 s sobreviva a la rotación de pantalla.
 */
public final class EstadoCambioContrasenia {

    private final boolean cargando;
    private final String error;
    private final String codigo;
    private final Set<RequisitoContrasenia> incumplidos;
    private final boolean contrasenasCoinciden;
    private final int segundosRestantes;
    private final boolean puedeReenviar;
    private final boolean puedeCambiar;
    private final boolean cambioExitoso;

    private EstadoCambioContrasenia(Builder b) {
        this.cargando = b.cargando;
        this.error = b.error;
        this.codigo = b.codigo;
        this.incumplidos = b.incumplidos == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(b.incumplidos);
        this.contrasenasCoinciden = b.contrasenasCoinciden;
        this.segundosRestantes = b.segundosRestantes;
        this.puedeReenviar = b.puedeReenviar;
        this.puedeCambiar = b.puedeCambiar;
        this.cambioExitoso = b.cambioExitoso;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isCargando() {
        return cargando;
    }

    public String getError() {
        return error;
    }

    public String getCodigo() {
        return codigo;
    }

    public Set<RequisitoContrasenia> getIncumplidos() {
        return incumplidos;
    }

    public boolean isContrasenasCoinciden() {
        return contrasenasCoinciden;
    }

    public int getSegundosRestantes() {
        return segundosRestantes;
    }

    public boolean isPuedeReenviar() {
        return puedeReenviar;
    }

    public boolean isPuedeCambiar() {
        return puedeCambiar;
    }

    /** Evento one-shot: contraseña cambiada con éxito → Snackbar + volver al login. */
    public boolean isCambioExitoso() {
        return cambioExitoso;
    }

    public static final class Builder {
        private boolean cargando;
        private String error;
        private String codigo;
        private Set<RequisitoContrasenia> incumplidos;
        private boolean contrasenasCoinciden;
        private int segundosRestantes;
        private boolean puedeReenviar;
        private boolean puedeCambiar;
        private boolean cambioExitoso;

        public Builder cargando(boolean valor) {
            this.cargando = valor;
            return this;
        }

        public Builder error(String mensaje) {
            this.error = mensaje;
            return this;
        }

        public Builder codigo(String valor) {
            this.codigo = valor;
            return this;
        }

        public Builder incumplidos(Set<RequisitoContrasenia> valores) {
            this.incumplidos = valores;
            return this;
        }

        public Builder contrasenasCoinciden(boolean valor) {
            this.contrasenasCoinciden = valor;
            return this;
        }

        public Builder segundosRestantes(int valor) {
            this.segundosRestantes = valor;
            return this;
        }

        public Builder puedeReenviar(boolean valor) {
            this.puedeReenviar = valor;
            return this;
        }

        public Builder puedeCambiar(boolean valor) {
            this.puedeCambiar = valor;
            return this;
        }

        public Builder cambioExitoso(boolean valor) {
            this.cambioExitoso = valor;
            return this;
        }

        public EstadoCambioContrasenia build() {
            return new EstadoCambioContrasenia(this);
        }
    }
}

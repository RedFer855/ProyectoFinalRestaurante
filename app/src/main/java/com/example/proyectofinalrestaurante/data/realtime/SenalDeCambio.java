package com.example.proyectofinalrestaurante.data.realtime;

import java.util.Objects;

/**
 * La señal que emite el canal cuando algo cambia (Plan Fase 3, §4.1). Es un objeto
 * <b>inmutable</b> y sin dependencias de Android: solo dice qué módulo cambió. Así
 * {@code data/realtime} no conoce ni una clase del dominio de Pedidos — el único puente
 * entre el canal y el resto es este valor.
 */
public final class SenalDeCambio {

    /** El único módulo que emite señales en esta fase. */
    public static final String MODULO_PEDIDOS = "PEDIDOS";

    private final String modulo;

    private SenalDeCambio(String modulo) {
        this.modulo = Objects.requireNonNull(modulo, "modulo");
    }

    /** Señal de que cambió el módulo de Pedidos. */
    public static SenalDeCambio pedidos() {
        return new SenalDeCambio(MODULO_PEDIDOS);
    }

    /** Señal de un módulo arbitrario (para testear y para fases futuras). */
    public static SenalDeCambio deModulo(String modulo) {
        return new SenalDeCambio(modulo);
    }

    public String getModulo() {
        return modulo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SenalDeCambio)) {
            return false;
        }
        return modulo.equals(((SenalDeCambio) o).modulo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modulo);
    }

    @Override
    public String toString() {
        return "SenalDeCambio{modulo='" + modulo + "'}";
    }
}
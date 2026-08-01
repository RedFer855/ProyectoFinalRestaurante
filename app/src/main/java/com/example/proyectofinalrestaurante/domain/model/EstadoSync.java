package com.example.proyectofinalrestaurante.domain.model;

/**
 * Estado de sincronización de una fila local frente al servidor (Plan Fase 2b, §4.5).
 *
 * <p>{@code SINCRONIZADO} es lo que la UI muestra en silencio; {@code PENDIENTE} y
 * {@code ERROR} se pintan en la tarjeta para que el usuario sepa que lo que ve todavía no
 * está subido (o no se pudo subir). Sin esa distinción, "guardado" y "guardado y subido"
 * son la misma cosa y el usuario pierde la confianza en la app la primera vez que se
 * pierde un cambio.</p>
 */
public enum EstadoSync {
    SINCRONIZADO,
    PENDIENTE,
    ERROR
}

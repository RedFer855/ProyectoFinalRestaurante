package com.example.proyectofinalrestaurante.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Requisitos que puede incumplir una contraseña. Espejo de la política de contraseñas
 * configurada en Supabase (Authentication → Policies, tarea S-2 del Plan Fase 1b) —
 * la validación en la app es UX; la que manda es la del servidor.
 */
public enum RequisitoContrasenia {
    LONGITUD_MINIMA,
    MAYUSCULA,
    MINUSCULA,
    DIGITO,
    SIMBOLO
}

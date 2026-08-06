package com.example.proyectofinalrestaurante.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.domain.model.ResumenInicio;

/**
 * Contrato del resumen del dashboard de Inicio (Domain Layer, Plan Fase 3c, §7.1).
 *
 * <p>Es <b>100% local</b>: seis de sus siete valores son contadores de Room que nunca fallan,
 * y el séptimo (ventas de hoy) lee la misma instantánea que ya mantiene
 * {@link ReporteRepository} — nunca dispara una llamada de red por su cuenta (§5 del plan).
 * Así, para mesero y cocina el dashboard nunca intenta una petición que iba a fallar con 403.</p>
 */
public interface ResumenRepository {

    LiveData<ResumenInicio> observarResumen();
}

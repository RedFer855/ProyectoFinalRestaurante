package com.example.proyectofinalrestaurante.domain.model;

/**
 * Rango temporal de un reporte de ventas (Plan Fase 3c, §4.1). Los tres valores son
 * exactamente los que acepta el RPC {@code reporte_ventas(p_rango text)}: el servidor calcula
 * el rango real en {@code America/Tegucigalpa}, el cliente solo manda uno de estos tres.
 */
public enum RangoReporte {
    HOY,
    SEMANA,
    MES
}

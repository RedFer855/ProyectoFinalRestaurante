package com.example.proyectofinalrestaurante.domain;

/**
 * Regla de vigencia de una instantánea de reporte (Plan Fase 3c, §2.1): a partir de qué edad
 * se considera vieja y hay que refrescarla contra el servidor.
 *
 * <p>Sin este umbral, cada {@code onStart} del Fragment (por ejemplo, rotar el teléfono)
 * dispararía la agregación más cara del sistema. El reloj se inyecta un nivel arriba (en
 * quien llama, {@code ReporteRepositorioLocal}) — mismo patrón que {@code SincronizadorPedidos}
 * (regla 3 de Estrategia de Pruebas Android): nada de red real ni de reloj del sistema leído
 * a ciegas, para poder testear los bordes (14:59 / 15:01) sin dormir el hilo de test.</p>
 */
public final class ReglasReporte {

    /** Umbral de vigencia de la instantánea: 15 minutos (Plan Fase 3c, §2.1). */
    private static final long QUINCE_MINUTOS_EN_MILIS = 15 * 60 * 1000L;

    private ReglasReporte() {
    }

    /** ¿La instantánea generada en {@code generadoEnEpochMillis} ya está vieja? */
    public static boolean esVieja(long generadoEnEpochMillis, long ahoraEpochMillis) {
        return ahoraEpochMillis - generadoEnEpochMillis > QUINCE_MINUTOS_EN_MILIS;
    }
}

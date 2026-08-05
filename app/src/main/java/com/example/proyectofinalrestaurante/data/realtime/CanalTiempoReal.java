package com.example.proyectofinalrestaurante.data.realtime;

/**
 * Contrato del canal de tiempo real (Plan Fase 3, §4.2). Sin dependencias de Android: solo
 * OkHttp (la implementación {@code CanalRealtimeSupabase}) y los oyentes definidos acá.
 *
 * <p>El canal <b>no</b> conoce el dominio de Pedidos: entrega {@link SenalDeCambio}, y el
 * que recibe decide qué sincronizar. El ciclo de vida (conectar/desconectar según
 * {@code ProcessLifecycleOwner}, backoff, no-reintento de JWT vencido) lo maneja el
 * {@code core/SupervisorTiempoReal} de E6, no este contrato.</p>
 */
public interface CanalTiempoReal {

    /** Conecta y se une al canal emitido por la base, llevando el JWT de la sesión. */
    void conectar(String token);

    /** Desconexión limpia: {@code phx_leave} + cierre del socket con código 1000. */
    void desconectar();

    /** Registra el consumidor de señales de cambio. */
    void observarSenales(OyenteSenales oyente);

    /** Registra el observador del estado de la conexión (para que el supervisor decida). */
    void observarEstado(OyenteEstado oyente);

    /** Consumidor de señales: lo que un módulo real-time decide escuchar. */
    interface OyenteSenales {
        void alRecibir(SenalDeCambio senal);
    }

    /** Observador del estado: el supervisor usa {@link #alCaer} para el backoff y E6. */
    interface OyenteEstado {
        void alConectar();

        void alCaer(int codigo, String razon);
    }
}
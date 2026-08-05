package com.example.proyectofinalrestaurante.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.domain.model.Notificacion;

import java.util.List;

/**
 * Contrato del buzón de notificaciones (Domain Layer). {@code data} lo implementa.
 *
 * <p>El buzón es <b>local</b> (Plan Fase 3, §4.6): no tiene tabla en el servidor ni delta
 * propio — las notificaciones se derivan dentro del delta de Pedidos y viven en Room. Este
 * contrato es la cara que la UI (el badge de la Toolbar y la hoja modal) ve del buzón.</p>
 */
public interface NotificacionRepository {

    /** No leídas visibles para esta sesión: alimenta el badge de la Toolbar ({@code R2}). */
    LiveData<Integer> contarNoLeidas();

    /** Las últimas {@code ventana} notificaciones visibles, más recientes primero. */
    LiveData<List<Notificacion>> observarBuzon(int ventana);

    /** Marca una notificación como leída. No falla: es una escritura local. */
    void marcarLeida(long idLocal);

    /** Marca todas las visibles como leídas. La usa la hoja al abrirse. */
    void marcarTodasLeidas();

    /**
     * Purga las notificaciones leídas más viejas que {@code antesDe} (millis). Se llama al
     * conectar el canal de tiempo real: leídas de más de 48 h no sirven de nada y el buzón
     * no debe crecer sin límite (Plan Fase 3, §5.2).
     */
    void purgarViejas(long antesDe);
}

package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.proyectofinalrestaurante.data.local.entity.NotificacionEntity;

import java.util.List;

/**
 * DAO del buzón de notificaciones (Plan Fase 3, §4.6).
 *
 * <p>Las notificaciones son <b>locales</b>: nacen y mueren en el dispositivo y no tienen
 * identidad de servidor. El filtrado por destinatario va <b>en la consulta</b>, no en quien
 * la escribe — {@code SincronizadorPedidos} graba {@code rol_destino} o
 * {@code destinatario_auth} sin conocer la sesión, y la consulta resuelve a quién le toca.
 * Así el buzón se reajusta solo si cambia el rol del usuario.</p>
 *
 * <p>El {@code OnConflictStrategy.IGNORE} sobre {@code clave_unica} es la idempotencia del
 * buzón: re-aplicar un delta (que ocurre por diseño cuando una pasada se corta a mitad) no
 * duplica avisos.</p>
 */
@Dao
public interface NotificacionDao {

    @Query("SELECT * FROM notificaciones"
            + " WHERE (rol_destino IS NULL OR rol_destino = :rol)"
            + " AND (destinatario_auth IS NULL OR destinatario_auth = :idAuth)"
            + " ORDER BY creado_en DESC LIMIT :ventana")
    LiveData<List<NotificacionEntity>> observarBuzon(String rol, String idAuth, int ventana);

    @Query("SELECT COUNT(*) FROM notificaciones WHERE leida = 0")
    LiveData<Integer> contarNoLeidas();

    @Query("SELECT * FROM notificaciones WHERE id_local = :idLocal LIMIT 1")
    NotificacionEntity porIdLocal(long idLocal);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertar(NotificacionEntity notificacion);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertarTodos(List<NotificacionEntity> notificaciones);

    @Query("UPDATE notificaciones SET leida = 1 WHERE id_local = :idLocal")
    void marcarLeida(long idLocal);

    @Query("UPDATE notificaciones SET leida = 1")
    void marcarTodasLeidas();

    /** Purga las leídas más viejas que {@code antesDe} (epoch millis) — retención de 48 h. */
    @Query("DELETE FROM notificaciones WHERE leida = 1 AND creado_en < :antesDe")
    int purgarLeidasViejas(long antesDe);
}
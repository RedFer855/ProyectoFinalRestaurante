package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.proyectofinalrestaurante.data.local.AppDatabase;
import com.example.proyectofinalrestaurante.data.local.dao.NotificacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.NotificacionEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.NotificacionMapper;
import com.example.proyectofinalrestaurante.domain.model.Notificacion;
import com.example.proyectofinalrestaurante.domain.repository.NotificacionRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación local del buzón (Plan Fase 3, E9). Mismo patrón que el resto de los
 * repositorios local-first: lecturas {@link LiveData} sobre Room, escrituras que no fallan.
 *
 * <p>El filtrado por destinatario (rol o {@code idAuth}) va en la consulta y los parámetros
 * entran por constructor desde la fábrica de la UI — que es quien conoce la sesión actual
 * (Plan Fase 3, §4.6: el sincronizador graba {@code rol_destino}/{@code destinatario_auth}
 * sin conocer la sesión; la consulta resuelve).</p>
 */
public final class NotificacionRepositorioLocal implements NotificacionRepository {

    /** {@code null} = no filtrar por rol; ver la consulta de {@code NotificacionDao}. */
    @Nullable private final String rol;
    /** {@code null} = no filtrar por destinatario; ver la consulta de {@code NotificacionDao}. */
    @Nullable private final String idAuth;

    private final NotificacionDao dao;

    public NotificacionRepositorioLocal(AppDatabase base, @Nullable String rol,
                                        @Nullable String idAuth) {
        this.dao = base.notificacionDao();
        this.rol = rol;
        this.idAuth = idAuth;
    }

    @Override
    public LiveData<Integer> contarNoLeidas() {
        return dao.contarNoLeidas();
    }

    @Override
    public LiveData<List<Notificacion>> observarBuzon(int ventana) {
        return Transformations.map(
                dao.observarBuzon(rol, idAuth, ventana),
                NotificacionRepositorioLocal::aDominio);
    }

    @Override
    public void marcarLeida(long idLocal) {
        dao.marcarLeida(idLocal);
    }

    @Override
    public void marcarTodasLeidas() {
        dao.marcarTodasLeidas();
    }

    @Override
    public void purgarViejas(long antesDe) {
        dao.purgarLeidasViejas(antesDe);
    }

    private static List<Notificacion> aDominio(List<NotificacionEntity> entidades) {
        List<Notificacion> dominio = new ArrayList<>(entidades.size());
        for (NotificacionEntity entidad : entidades) {
            dominio.add(NotificacionMapper.aDominio(entidad));
        }
        return dominio;
    }
}
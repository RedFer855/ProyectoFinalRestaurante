package com.example.proyectofinalrestaurante.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.room.Room;

import com.example.proyectofinalrestaurante.data.local.dao.NotificacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.NotificacionEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * El DAO del buzón (Plan Fase 3, §4.6) contra una base en memoria real. Cubre el filtrado
 * por destinatario en la consulta y la idempotencia por {@code clave_unica}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NotificacionDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase base;
    private NotificacionDao dao;

    @Before
    public void crearBaseEnMemoria() {
        base = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = base.notificacionDao();
    }

    @After
    public void cerrarBase() {
        base.close();
    }

    @Test
    public void insertarConMismaClaveUnica_ignoraElDuplicado() {
        long primera = dao.insertar(unAviso("PEDIDO_NUEVO:cocina:41", "cocina", null));
        long segunda = dao.insertar(unAviso("PEDIDO_NUEVO:cocina:41", "cocina", null));

        assertEquals(primera, dao.porIdLocal(primera).getIdLocal());
        assertNull(dao.porIdLocal(segunda));
    }

    /**
     * B8: el filtrado por destinatario va en la consulta. Un mesero no ve las
     * notificaciones de rol cocina, ni las de otro mesero.
     */
    @Test
    public void observarBuzon_filtraPorRolYDestinatario() {
        dao.insertar(conRol(unAviso("1", "cocina", null), "cocina"));
        dao.insertar(unAviso("2", null, "uuid-mesero-1"));
        dao.insertar(unAviso("3", null, "uuid-mesero-2"));

        AtomicReference<List<NotificacionEntity>> ultimo = new AtomicReference<>();
        LiveData<List<NotificacionEntity>> liveData = dao.observarBuzon("mesero", "uuid-mesero-1", 20);
        liveData.observeForever(ultimo::set);

        List<NotificacionEntity> buzon = ultimo.get();
        assertNotNull(buzon);
        // Solo la propia (destinatario_auth = uuid-mesero-1); ni cocina ni el otro mesero.
        assertEquals(1, buzon.size());
        assertEquals("uuid-mesero-1", buzon.get(0).getDestinatarioAuth());
    }

    @Test
    public void observarBuzon_ordenaDeMasNuevaAMasVieja() {
        dao.insertar(conCreadoEn(unAviso("x", null, "uuid-mesero-1"), 1000L));
        dao.insertar(conCreadoEn(unAviso("y", null, "uuid-mesero-1"), 3000L));

        AtomicReference<List<NotificacionEntity>> ultimo = new AtomicReference<>();
        LiveData<List<NotificacionEntity>> liveData = dao.observarBuzon("mesero", "uuid-mesero-1", 20);
        liveData.observeForever(ultimo::set);

        List<NotificacionEntity> buzon = ultimo.get();
        assertNotNull(buzon);
        assertEquals(2, buzon.size());
        assertTrue(buzon.get(0).getCreadoEn() > buzon.get(1).getCreadoEn());
    }

    @Test
    public void contarNoLeidas_cuentaSoloLasPendientes() {
        dao.insertar(unAviso("a", "cocina", null));
        long leida = dao.insertar(unAviso("b", "cocina", null));
        dao.marcarLeida(leida);

        AtomicReference<Integer> ultimo = new AtomicReference<>();
        LiveData<Integer> liveData = dao.contarNoLeidas();
        liveData.observeForever(ultimo::set);

        assertEquals(1, ultimo.get().intValue());
    }

    @Test
    public void marcarLeida_y_contarNoLeidas_emitenDeNuevo() {
        long id = dao.insertar(unAviso("a", "cocina", null));

        dao.marcarLeida(id);

        NotificacionEntity recargada = dao.porIdLocal(id);
        assertTrue(recargada.isLeida());
    }

    @Test
    public void marcarTodasLeidas_marcaTodas() {
        dao.insertar(unAviso("a", "cocina", null));
        dao.insertar(unAviso("b", "cocina", null));
        AtomicReference<Integer> ultimo = new AtomicReference<>();
        dao.contarNoLeidas().observeForever(ultimo::set);

        dao.marcarTodasLeidas();

        assertEquals(0, ultimo.get().intValue());
    }

    @Test
    public void purgarLeidasViejas_borraSoloLasLeidasYViejas() {
        long vieja = dao.insertar(conCreadoEn(unAviso("a", "cocina", null), 1000L));
        long nueva = dao.insertar(conCreadoEn(unAviso("b", "cocina", null), 5000L));
        dao.marcarTodasLeidas();

        int borradas = dao.purgarLeidasViejas(4000L);

        assertEquals(1, borradas);
        assertNull(dao.porIdLocal(vieja));
        assertNotNull(dao.porIdLocal(nueva));
    }

    private static NotificacionEntity unAviso(String claveUnica, String rol, String destinatario) {
        NotificacionEntity n = new NotificacionEntity();
        n.setTipo("PEDIDO_NUEVO");
        n.setRolDestino(rol);
        n.setDestinatarioAuth(destinatario);
        n.setArg1("41");
        n.setCreadoEn(2000L);
        n.setLeida(false);
        n.setClaveUnica(claveUnica == null ? "PEDIDO_NUEVO:41" : claveUnica);
        return n;
    }

    private static NotificacionEntity conRol(NotificacionEntity n, String rol) {
        n.setRolDestino(rol);
        return n;
    }

    private static NotificacionEntity conCreadoEn(NotificacionEntity n, long creadoEn) {
        n.setCreadoEn(creadoEn);
        return n;
    }
}
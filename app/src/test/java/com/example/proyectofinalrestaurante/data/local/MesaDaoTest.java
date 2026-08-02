package com.example.proyectofinalrestaurante.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.room.Room;

import com.example.proyectofinalrestaurante.data.local.dao.MesaDao;
import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;

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
 * El DAO de mesas (Plan Fase 2c, E3) contra una base en memoria real. Mismo patrón que
 * {@link PlatilloDaoTest}: Robolectric porque Room necesita el framework de SQLite de
 * Android; sdk 35 porque Robolectric 4.16 no soporta el targetSdk 37 del proyecto.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MesaDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase base;
    private MesaDao dao;

    @Before
    public void crearBaseEnMemoria() {
        base = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = base.mesaDao();
    }

    @After
    public void cerrarBase() {
        base.close();
    }

    @Test
    public void insertarYPorIdLocal_roundTripCompleto() {
        MesaEntity mesa = unaMesa(4, 6, 1, "SINCRONIZADO");
        long id = dao.insertar(mesa);

        MesaEntity recargada = dao.porIdLocal(id);

        assertNotNull(recargada);
        assertEquals(4, recargada.getNumeroMesa());
        assertEquals(6, recargada.getCapacidad());
        assertEquals(1, recargada.getIdEstadoMesa());
        assertEquals("SINCRONIZADO", recargada.getEstadoSync());
    }

    @Test
    public void porIdServidor_buscaPorLaFilaDelServidor() {
        MesaEntity mesa = unaMesa(4, 6, 1, "SINCRONIZADO");
        mesa.setIdServidor(800);
        long id = dao.insertar(mesa);

        MesaEntity recargada = dao.porIdServidor(800);

        assertNotNull(recargada);
        assertEquals(id, recargada.getIdLocal());
    }

    @Test
    public void insertarConMismoIdServidor_reemplazaEnVezDeDuplicar() {
        dao.insertar(conIdServidor(unaMesa(4, 6, 1, "SINCRONIZADO"), 800));
        dao.insertar(conIdServidor(unaMesa(4, 8, 1, "SINCRONIZADO"), 800));

        MesaEntity recargada = dao.porIdServidor(800);

        assertNotNull(recargada);
        assertEquals(8, recargada.getCapacidad());
    }

    @Test
    public void actualizar_persisteLaBajaLogica() {
        long id = dao.insertar(unaMesa(4, 6, 1, "SINCRONIZADO"));
        MesaEntity mesa = dao.porIdLocal(id);
        mesa.setIdEstado(2);
        mesa.setActivo(false);

        dao.actualizar(mesa);

        MesaEntity recargada = dao.porIdLocal(id);
        assertEquals(2, recargada.getIdEstado());
        assertFalse(recargada.isActivo());
    }

    @Test
    public void contarNoSincronizadas_cuentaSoloLasQueNoEstanSincronizadas() {
        dao.insertar(unaMesa(1, 2, 1, "SINCRONIZADO"));
        dao.insertar(unaMesa(2, 2, 1, "PENDIENTE"));
        dao.insertar(unaMesa(3, 2, 1, "ERROR"));

        assertEquals(2, dao.contarNoSincronizadas());
    }

    @Test
    public void observarTodas_emiteEnOrdenPorNumeroYReflejaLosCambios() {
        AtomicReference<List<MesaEntity>> ultimo = new AtomicReference<>();
        LiveData<List<MesaEntity>> liveData = dao.observarTodas();
        liveData.observeForever(ultimo::set);

        dao.insertar(unaMesa(20, 4, 1, "SINCRONIZADO"));
        dao.insertar(unaMesa(3, 2, 1, "SINCRONIZADO"));

        List<MesaEntity> mesas = ultimo.get();
        assertNotNull(mesas);
        assertEquals(2, mesas.size());
        assertTrue(mesas.get(0).getNumeroMesa() < mesas.get(1).getNumeroMesa());
    }

    private static MesaEntity unaMesa(int numeroMesa, int capacidad, int idEstadoMesa,
                                      String estadoSync) {
        MesaEntity mesa = new MesaEntity();
        mesa.setNumeroMesa(numeroMesa);
        mesa.setCapacidad(capacidad);
        mesa.setIdEstadoMesa(idEstadoMesa);
        mesa.setIdEstado(1);
        mesa.setActivo(true);
        mesa.setEstadoSync(estadoSync);
        return mesa;
    }

    private static MesaEntity conIdServidor(MesaEntity mesa, Integer idServidor) {
        mesa.setIdServidor(idServidor);
        return mesa;
    }
}

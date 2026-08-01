package com.example.proyectofinalrestaurante.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.room.Room;

import com.example.proyectofinalrestaurante.data.local.dao.PlatilloDao;
import com.example.proyectofinalrestaurante.data.local.entity.PlatilloEntity;

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
 * El DAO de platillos (Plan Fase 2b, E2) contra una base en memoria real. Robolectric porque
 * Room necesita el framework de SQLite de Android; sdk 35 porque Robolectric 4.16 no
 * soporta el targetSdk 37 del proyecto.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PlatilloDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase base;
    private PlatilloDao dao;

    @Before
    public void crearBaseEnMemoria() {
        base = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = base.platilloDao();
    }

    @After
    public void cerrarBase() {
        base.close();
    }

    @Test
    public void insertarYPorIdLocal_roundTripCompleto() {
        PlatilloEntity platillo = unPlatillo("Baleada", 45.0, 1, "SINCRONIZADO");
        long id = dao.insertar(platillo);

        PlatilloEntity recargado = dao.porIdLocal(id);

        assertNotNull(recargado);
        assertEquals("Baleada", recargado.getNombre());
        assertEquals(45.0, recargado.getPrecio(), 0.0);
        assertEquals(1, recargado.getIdCategoriaLocal());
        assertEquals("SINCRONIZADO", recargado.getEstadoSync());
    }

    @Test
    public void porIdServidor_buscaPorLaFilaDelServidor() {
        PlatilloEntity platillo = unPlatillo("Baleada", 45.0, 1, "SINCRONIZADO");
        platillo.setIdServidor(500);
        long id = dao.insertar(platillo);

        PlatilloEntity recargado = dao.porIdServidor(500);

        assertNotNull(recargado);
        assertEquals(id, recargado.getIdLocal());
    }

    @Test
    public void insertarConMismoIdServidor_reemplazaEnVezDeDuplicar() {
        dao.insertar(conIdServidor(unPlatillo("Baleada", 45.0, 1, "SINCRONIZADO"), 500));
        dao.insertar(conIdServidor(unPlatillo("Baleada nueva", 55.0, 1, "SINCRONIZADO"), 500));

        PlatilloEntity recargado = dao.porIdServidor(500);

        assertNotNull(recargado);
        assertEquals("Baleada nueva", recargado.getNombre());
    }

    @Test
    public void actualizar_persisteLosCambios() {
        long id = dao.insertar(unPlatillo("Baleada", 45.0, 1, "SINCRONIZADO"));
        PlatilloEntity platillo = dao.porIdLocal(id);
        platillo.setPrecio(60.0);
        platillo.setEstadoSync("PENDIENTE");

        dao.actualizar(platillo);

        assertEquals(60.0, dao.porIdLocal(id).getPrecio(), 0.0);
        assertEquals("PENDIENTE", dao.porIdLocal(id).getEstadoSync());
    }

    @Test
    public void borrar_eliminaLaFila() {
        long id = dao.insertar(unPlatillo("Baleada", 45.0, 1, "SINCRONIZADO"));

        dao.borrar(dao.porIdLocal(id));

        assertNull(dao.porIdLocal(id));
    }

    @Test
    public void contarNoSincronizados_cuentaSoloLosQueNoEstanSincronizados() {
        dao.insertar(unPlatillo("Sincronizado", 10.0, 1, "SINCRONIZADO"));
        dao.insertar(unPlatillo("Pendiente", 20.0, 1, "PENDIENTE"));
        dao.insertar(unPlatillo("Con error", 30.0, 1, "ERROR"));

        assertEquals(2, dao.contarNoSincronizados());
    }

    @Test
    public void observarTodos_emiteEnOrdenAlfabeticoYReflejaLosCambios() {
        AtomicReference<List<PlatilloEntity>> ultimo = new AtomicReference<>();
        LiveData<List<PlatilloEntity>> liveData = dao.observarTodos();
        liveData.observeForever(ultimo::set);

        dao.insertar(unPlatillo("Zorro", 30.0, 1, "SINCRONIZADO"));
        dao.insertar(unPlatillo("Ábaco", 20.0, 1, "SINCRONIZADO"));

        List<PlatilloEntity> platillos = ultimo.get();
        assertNotNull(platillos);
        assertEquals(2, platillos.size());
        assertTrue(platillos.get(0).getNombre().compareToIgnoreCase(platillos.get(1).getNombre()) < 0);
    }

    private static PlatilloEntity unPlatillo(String nombre, double precio, int idCategoriaLocal,
                                             String estadoSync) {
        PlatilloEntity platillo = new PlatilloEntity();
        platillo.setNombre(nombre);
        platillo.setPrecio(precio);
        platillo.setIdCategoriaLocal(idCategoriaLocal);
        platillo.setEstadoSync(estadoSync);
        platillo.setActivo(true);
        return platillo;
    }

    private static PlatilloEntity conIdServidor(PlatilloEntity platillo, Integer idServidor) {
        platillo.setIdServidor(idServidor);
        return platillo;
    }
}

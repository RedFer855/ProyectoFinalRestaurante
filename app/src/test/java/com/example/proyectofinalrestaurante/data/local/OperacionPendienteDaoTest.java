package com.example.proyectofinalrestaurante.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;

import com.example.proyectofinalrestaurante.data.local.dao.OperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * El DAO del outbox (Plan Fase 2b, E4) contra una base en memoria real: FIFO estricto por
 * {@code id}, filtros por fila local y eliminación. {@link OutboxTest} cubre la lógica de
 * encolado; este cubre que la semántica del SQL sea la que el outbox asume.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class OperacionPendienteDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase base;
    private OperacionPendienteDao dao;

    @Before
    public void crearBaseEnMemoria() {
        base = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = base.operacionPendienteDao();
    }

    @After
    public void cerrarBase() {
        base.close();
    }

    @Test
    public void encolar_asignaIdYDevuelveLaFila() {
        long id = dao.encolar(unaOperacion("CREAR_PLATILLO", 3, "{}", null));

        OperacionPendienteEntity recargada = dao.porId(id);

        assertNotNull(recargada);
        assertEquals("CREAR_PLATILLO", recargada.getTipo());
        assertEquals(3, recargada.getIdLocal());
        assertEquals(0, recargada.getIntentos());
    }

    @Test
    public void primeras_devuelveFifoPorIdYRespetaLimite() {
        dao.encolar(unaOperacion("CREAR_PLATILLO", 1, "a", null));
        dao.encolar(unaOperacion("CREAR_PLATILLO", 2, "b", null));
        dao.encolar(unaOperacion("CREAR_PLATILLO", 3, "c", null));

        List<OperacionPendienteEntity> primeras = dao.primeras(2);

        assertEquals(2, primeras.size());
        assertEquals("a", primeras.get(0).getPayloadJson());
        assertEquals("b", primeras.get(1).getPayloadJson());
    }

    @Test
    public void deFila_devuelveSoloLasDeEsaFilaLocalEnOrden() {
        dao.encolar(unaOperacion("CREAR_PLATILLO", 1, "a", null));
        dao.encolar(unaOperacion("CREAR_PLATILLO", 2, "b", null));
        dao.encolar(unaOperacion("ACTUALIZAR_PLATILLO", 1, "a2", null));

        List<OperacionPendienteEntity> deFila = dao.deFila(1);

        assertEquals(2, deFila.size());
        assertEquals("a", deFila.get(0).getPayloadJson());
        assertEquals("a2", deFila.get(1).getPayloadJson());
    }

    @Test
    public void eliminar_sacaLaOperacionDeLaCola() {
        long id = dao.encolar(unaOperacion("CREAR_PLATILLO", 1, "a", null));
        assertEquals(1, dao.contar());

        dao.eliminar(id);

        assertEquals(0, dao.contar());
        assertNull(dao.porId(id));
    }

    @Test
    public void actualizar_persisteLosCambiosDeReintento() {
        long id = dao.encolar(unaOperacion("CREAR_PLATILLO", 1, "a", null));
        OperacionPendienteEntity operacion = dao.porId(id);
        operacion.setIntentos(2);
        operacion.setUltimoError("timeout");

        dao.actualizar(operacion);

        assertEquals(2, dao.porId(id).getIntentos());
        assertEquals("timeout", dao.porId(id).getUltimoError());
    }

    @Test
    public void borrar_tambienSacaLaOperacionDeLaCola() {
        long id = dao.encolar(unaOperacion("CREAR_PLATILLO", 1, "a", null));

        dao.borrar(dao.porId(id));

        assertEquals(0, dao.contar());
    }

    @Test
    public void contar_vaciaEmpiezaEnCero() {
        assertTrue(dao.contar() == 0);
    }

    private static OperacionPendienteEntity unaOperacion(String tipo, long idLocal,
                                                         String payloadJson, String rutaImagen) {
        OperacionPendienteEntity operacion = new OperacionPendienteEntity();
        operacion.setTipo(tipo);
        operacion.setIdLocal(idLocal);
        operacion.setPayloadJson(payloadJson);
        operacion.setRutaImagenLocal(rutaImagen);
        operacion.setIntentos(0);
        operacion.setCreadoEn(System.currentTimeMillis());
        return operacion;
    }
}

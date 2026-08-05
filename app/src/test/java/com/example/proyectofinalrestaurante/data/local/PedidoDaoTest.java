package com.example.proyectofinalrestaurante.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.room.Room;

import com.example.proyectofinalrestaurante.data.local.dao.PedidoDao;
import com.example.proyectofinalrestaurante.data.local.entity.PedidoEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * El DAO de pedidos (Plan Fase 3, E3) contra una base en memoria real. Mismo patrón que
 * {@link MesaDaoTest}: Robolectric porque Room necesita el framework de SQLite de Android.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PedidoDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase base;
    private PedidoDao dao;

    @Before
    public void crearBaseEnMemoria() {
        base = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = base.pedidoDao();
    }

    @After
    public void cerrarBase() {
        base.close();
    }

    @Test
    public void insertarYPorIdLocal_roundTripCompleto() {
        long id = dao.insertar(unPedido(1, "2026-08-04T12:00:00+00:00", 1, "SINCRONIZADO"));

        PedidoEntity recargada = dao.porIdLocal(id);

        assertNotNull(recargada);
        assertEquals(1, recargada.getIdServidor().intValue());
        assertEquals(4, recargada.getNumeroMesa().intValue());
        assertEquals(380.0, recargada.getTotal(), 0.001);
        assertEquals(3, recargada.getCantidadItems());
    }

    @Test
    public void porIdServidor_buscaPorLaFilaDelServidor() {
        long id = dao.insertar(unPedido(1042, "2026-08-04T12:00:00+00:00", 1, "SINCRONIZADO"));

        PedidoEntity recargada = dao.porIdServidor(1042);

        assertNotNull(recargada);
        assertEquals(id, recargada.getIdLocal());
    }

    @Test
    public void insertarConMismoIdServidor_reemplazaEnVezDeDuplicar() {
        dao.insertar(unPedido(1042, "2026-08-04T12:00:00+00:00", 1, "SINCRONIZADO"));
        dao.insertar(unPedido(1042, "2026-08-04T13:00:00+00:00", 3, "SINCRONIZADO"));

        PedidoEntity recargada = dao.porIdServidor(1042);

        assertNotNull(recargada);
        assertEquals(3, recargada.getIdEstadoPedido());
        assertNull(dao.porIdServidor(1043));
    }

    /**
     * B9: la ventana devuelve las primeras {@code ventana} filas en orden {@code fecha ASC},
     * no las primeras insertadas ni las de mayor id.
     */
    @Test
    public void observarVentana_devuelveSoloLasPrimerasEnOrdenDeFecha() {
        for (int i = 1; i <= 50; i++) {
            dao.insertar(unPedido(i, String.format("2026-08-04T%02d:00:00+00:00", (i % 24)),
                    1, "SINCRONIZADO"));
        }

        AtomicReference<List<PedidoEntity>> ultimo = new AtomicReference<>();
        LiveData<List<PedidoEntity>> liveData = dao.observarVentana(20);
        liveData.observeForever(ultimo::set);

        List<PedidoEntity> ventana = ultimo.get();
        assertNotNull(ventana);
        assertEquals(20, ventana.size());
        // El primer pedido del día (fecha 00:00, insertado en la vuelta i=24) abre la ventana.
        assertEquals("2026-08-04T00:00:00+00:00", ventana.get(0).getFecha());
    }

    @Test
    public void contarTotal_devuelveLaCantidadDeFilas() {
        dao.insertar(unPedido(1, "2026-08-04T12:00:00+00:00", 1, "SINCRONIZADO"));
        dao.insertar(unPedido(2, "2026-08-04T13:00:00+00:00", 1, "SINCRONIZADO"));

        AtomicReference<Integer> ultimo = new AtomicReference<>();
        LiveData<Integer> liveData = dao.contarTotal();
        liveData.observeForever(ultimo::set);

        assertEquals(2, ultimo.get().intValue());
    }

    @Test
    public void contarNoSincronizadas_cuentaSoloLasQueNoEstanSincronizadas() {
        dao.insertar(unPedido(1, "2026-08-04T12:00:00+00:00", 1, "SINCRONIZADO"));
        dao.insertar(unPedido(2, "2026-08-04T12:00:00+00:00", 1, "PENDIENTE"));
        dao.insertar(unPedido(3, "2026-08-04T12:00:00+00:00", 1, "ERROR"));

        assertEquals(2, dao.contarNoSincronizadas());
    }

    @Test
    public void insertarTodos_insertaLaListaCompleta() {
        List<PedidoEntity> lista = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            lista.add(unPedido(i, "2026-08-04T12:00:00+00:00", 1, "SINCRONIZADO"));
        }

        dao.insertarTodos(lista);

        assertEquals(50, dao.porIdServidor(50).getIdServidor().intValue());
    }

    private static PedidoEntity unPedido(int idServidor, String fecha, int idEstadoPedido,
                                         String estadoSync) {
        PedidoEntity pedido = new PedidoEntity();
        pedido.setIdServidor(idServidor);
        pedido.setFecha(fecha);
        pedido.setIdEstadoPedido(idEstadoPedido);
        pedido.setNumeroMesa(4);
        pedido.setCliente("Ana Cruz");
        pedido.setTotal(380.0);
        pedido.setCantidadItems(3);
        pedido.setIdAuthUsuario("uuid-mesero");
        pedido.setActualizadoEn(fecha);
        pedido.setEstadoSync(estadoSync);
        return pedido;
    }
}
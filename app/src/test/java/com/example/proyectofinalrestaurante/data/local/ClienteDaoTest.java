package com.example.proyectofinalrestaurante.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.room.Room;

import com.example.proyectofinalrestaurante.data.local.dao.ClienteDao;
import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;

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
 * El DAO de clientes (Plan Fase 2d, E3) contra una base en memoria real. Mismo patrón que
 * {@link MesaDaoTest}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ClienteDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase base;
    private ClienteDao dao;

    @Before
    public void crearBaseEnMemoria() {
        base = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = base.clienteDao();
    }

    @After
    public void cerrarBase() {
        base.close();
    }

    @Test
    public void insertarYPorIdLocal_roundTripCompleto() {
        long id = dao.insertar(unCliente("Ana", "Cruz", "SINCRONIZADO"));

        ClienteEntity recargado = dao.porIdLocal(id);

        assertNotNull(recargado);
        assertEquals("Ana", recargado.getNombre());
        assertEquals("Cruz", recargado.getApellido());
        assertEquals("SINCRONIZADO", recargado.getEstadoSync());
    }

    @Test
    public void porIdServidor_buscaPorLaFilaDelServidor() {
        ClienteEntity cliente = unCliente("Ana", "Cruz", "SINCRONIZADO");
        cliente.setIdServidor(800);
        long id = dao.insertar(cliente);

        ClienteEntity recargado = dao.porIdServidor(800);

        assertNotNull(recargado);
        assertEquals(id, recargado.getIdLocal());
    }

    @Test
    public void insertarConMismoIdServidor_reemplazaEnVezDeDuplicar() {
        dao.insertar(conIdServidor(unCliente("Ana", "Cruz", "SINCRONIZADO"), 800));
        dao.insertar(conIdServidor(unCliente("Ana", "Cruz Actualizada", "SINCRONIZADO"), 800));

        ClienteEntity recargado = dao.porIdServidor(800);

        assertNotNull(recargado);
        assertEquals("Cruz Actualizada", recargado.getApellido());
    }

    @Test
    public void actualizar_persisteLaBajaLogica() {
        long id = dao.insertar(unCliente("Ana", "Cruz", "SINCRONIZADO"));
        ClienteEntity cliente = dao.porIdLocal(id);
        cliente.setIdEstado(2);
        cliente.setActivo(false);

        dao.actualizar(cliente);

        ClienteEntity recargado = dao.porIdLocal(id);
        assertEquals(2, recargado.getIdEstado());
        assertFalse(recargado.isActivo());
    }

    @Test
    public void borrar_loQuitaDeLaBase() {
        long id = dao.insertar(conIdServidor(unCliente("Ana", "Cruz", "SINCRONIZADO"), 800));
        ClienteEntity cliente = dao.porIdLocal(id);

        dao.borrar(cliente);

        assertNull(dao.porIdLocal(id));
    }

    @Test
    public void contarNoSincronizadas_cuentaSoloLasQueNoEstanSincronizadas() {
        dao.insertar(unCliente("Ana", "Cruz", "SINCRONIZADO"));
        dao.insertar(unCliente("Luis", "Medina", "PENDIENTE"));
        dao.insertar(unCliente("Sofía", "Ramos", "ERROR"));

        assertEquals(2, dao.contarNoSincronizadas());
    }

    @Test
    public void observarTodos_emiteEnOrdenPorNombreYReflejaLosCambios() {
        AtomicReference<List<ClienteEntity>> ultimo = new AtomicReference<>();
        LiveData<List<ClienteEntity>> liveData = dao.observarTodos();
        liveData.observeForever(ultimo::set);

        dao.insertar(unCliente("Sofía", "Ramos", "SINCRONIZADO"));
        dao.insertar(unCliente("Ana", "Cruz", "SINCRONIZADO"));

        List<ClienteEntity> clientes = ultimo.get();
        assertNotNull(clientes);
        assertEquals(2, clientes.size());
        assertEquals("Ana", clientes.get(0).getNombre());
        assertEquals("Sofía", clientes.get(1).getNombre());
    }

    private static ClienteEntity unCliente(String nombre, String apellido, String estadoSync) {
        ClienteEntity cliente = new ClienteEntity();
        cliente.setNombre(nombre);
        cliente.setApellido(apellido);
        cliente.setIdEstado(1);
        cliente.setActivo(true);
        cliente.setCantidadPedidos(0);
        cliente.setEstadoSync(estadoSync);
        return cliente;
    }

    private static ClienteEntity conIdServidor(ClienteEntity cliente, Integer idServidor) {
        cliente.setIdServidor(idServidor);
        return cliente;
    }
}

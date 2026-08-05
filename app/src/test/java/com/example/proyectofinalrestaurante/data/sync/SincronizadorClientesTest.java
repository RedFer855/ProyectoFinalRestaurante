package com.example.proyectofinalrestaurante.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.FakeOperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.ClienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.remote.SupabaseClienteApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.BuscarOCrearClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearClienteDto;
import com.example.proyectofinalrestaurante.data.repository.ClienteRemoto;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.google.gson.Gson;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Tests de {@link SincronizadorClientes}: drenado del outbox y sync delta.
 *
 * <p>Mismo foco que {@code SincronizadorMesasTest}: los casos C1-C5 de <b>P-029</b>
 * (2026-08-04) prueban el bucle de paginación por offset con marca fija, portado desde
 * {@code SincronizadorMenu.bajarPlatillos()}.</p>
 */
public class SincronizadorClientesTest {

    private final FakeSupabaseClienteApi api = new FakeSupabaseClienteApi();
    private final FakeOperacionPendienteDao operaciones = new FakeOperacionPendienteDao();
    private final Outbox outbox = new Outbox(operaciones, TipoOperacion.Modulo.CLIENTES);
    private final FakeClienteDao clientes = new FakeClienteDao();
    private final FakeSincronizacionDao marcas = new FakeSincronizacionDao();
    private final Gson gson = new Gson();

    private final EjecutorDeTransaccionEspia transacciones = new EjecutorDeTransaccionEspia();

    private SincronizadorClientes sincronizador() {
        return new SincronizadorClientes(
                new ClienteRemoto(api, () -> "token"), outbox, clientes, marcas, transacciones);
    }

    private static final class EjecutorDeTransaccionEspia implements EjecutorDeTransaccion {
        private int veces;

        @Override
        public void enTransaccion(Runnable bloque) {
            veces++;
            bloque.run();
        }
    }

    // ------------------------------------------------------------------ drenado

    @Test
    public void modulo_esElDeClientes() {
        assertEquals(TipoOperacion.Modulo.CLIENTES, sincronizador().modulo());
    }

    @Test
    public void crearCliente_exitoso_seteaIdServidorYVaciaLaCola() {
        clientes.insertar(unCliente(1, null, EstadoSync.PENDIENTE));
        outbox.encolar(TipoOperacion.CREAR_CLIENTE, 1, null, null);
        api.respuestaCrear = FakeCall.deRespuesta(Response.success(
                List.of(clienteServidor(7, "2026-08-01T10:00:00+00:00"))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(7, clientes.porIdLocal(1).getIdServidor().intValue());
        assertEquals(EstadoSync.SINCRONIZADO.name(), clientes.porIdLocal(1).getEstadoSync());
        assertEquals(0, outbox.contar());
    }

    @Test
    public void actualizarCliente_sinIdServidor_seDescartaComoOk() {
        clientes.insertar(unCliente(1, null, EstadoSync.PENDIENTE));
        outbox.encolar(TipoOperacion.ACTUALIZAR_CLIENTE, 1, null, null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(0, outbox.contar());
    }

    @Test
    public void borrarCliente_usaElIdServidorDelPayload() {
        outbox.encolar(TipoOperacion.BORRAR_CLIENTE, 1, PayloadOperacion.borrarCategoria(9), null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals("eq.9", api.ultimoFiltroBorrar);
        assertEquals(0, outbox.contar());
    }

    @Test
    public void tipoDesconocido_seDescartaParaNoBloquearLaCola() {
        outbox.encolar("OPERACION_DE_UNA_VERSION_FUTURA", 1, null, null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esPermanente());
        assertEquals(0, outbox.contar());
    }

    // ------------------------------------------------------------------ delta: básico

    @Test
    public void delta_sinMarca_bajaTodaLaTablaEnLaPrimeraPasada() {
        api.respuestaPorPagina = (filtro, offset) -> FakeCall.deRespuesta(
                Response.success(List.of(clienteServidor(7, "2026-08-01T10:00:00+00:00"))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertNull(api.ultimoFiltroPedido);
        assertEquals(EstadoSync.SINCRONIZADO.name(), clientes.porIdServidor(7).getEstadoSync());
        assertEquals("2026-08-01T10:00:00+00:00",
                marcas.porTabla(SincronizadorClientes.TABLA).getMarcaAgua());
    }

    // ------------------------------------------------------------------ delta: P-029 (C1-C5)

    @Test
    public void delta_cincuentaFilasConLaMismaMarca_noPierdeLasQueSiguen() {
        String mismaMarca = "2026-08-01T10:00:00+00:00";
        List<ClienteDto> primeraPagina = new ArrayList<>();
        for (int i = 0; i < ClienteRemoto.LIMITE_DELTA; i++) {
            primeraPagina.add(clienteServidor(1000 + i, mismaMarca));
        }
        api.respuestaPorPagina = (filtro, offset) -> offset == 0
                ? FakeCall.deRespuesta(Response.success(primeraPagina))
                : FakeCall.deRespuesta(Response.success(List.of(clienteServidor(2000, mismaMarca))));

        assertTrue(sincronizador().sincronizar().esOk());

        assertEquals(ClienteRemoto.LIMITE_DELTA + 1, clientes.porIdLocal.size());
    }

    @Test
    public void delta_paginaCompleta_pideLaSiguienteConOffsetYLaMismaMarcaInicial() {
        List<ClienteDto> primeraPagina = new ArrayList<>();
        for (int i = 0; i < ClienteRemoto.LIMITE_DELTA; i++) {
            primeraPagina.add(clienteServidor(1000 + i, String.format("2026-08-01T%02d:00:00+00:00", i)));
        }
        api.respuestaPorPagina = (filtro, offset) -> offset == 0
                ? FakeCall.deRespuesta(Response.success(primeraPagina))
                : FakeCall.deRespuesta(Response.success(
                        // "50" > "49" lexicográficamente: la última página trae la marca
                        // más alta de toda la pasada (ver el mismo comentario en
                        // SincronizadorMesasTest).
                        List.of(clienteServidor(2000, "2026-08-01T50:00:00+00:00"))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(ClienteRemoto.LIMITE_DELTA, api.ultimoOffsetPedido);
        assertEquals("2026-08-01T50:00:00+00:00",
                marcas.porTabla(SincronizadorClientes.TABLA).getMarcaAgua());
    }

    @Test
    public void delta_unaPaginaFalla_laMarcaNoAvanza() {
        List<ClienteDto> primeraPagina = new ArrayList<>();
        for (int i = 0; i < ClienteRemoto.LIMITE_DELTA; i++) {
            primeraPagina.add(clienteServidor(1000 + i, String.format("2026-08-01T%02d:00:00+00:00", i)));
        }
        api.respuestaPorPagina = (filtro, offset) -> offset == 0
                ? FakeCall.deRespuesta(Response.success(primeraPagina))
                : FakeCall.deFallo(new IOException("timeout"));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esTransitorio());
        assertNull(marcas.porTabla(SincronizadorClientes.TABLA));
    }

    @Test(timeout = 10_000)
    public void delta_servidorQueSiempreDevuelvePaginasLlenas_cortaPorElTopeDePaginas() {
        int[] pedidos = {0};
        api.respuestaPorPagina = (filtro, offset) -> {
            pedidos[0]++;
            List<ClienteDto> pagina = new ArrayList<>();
            for (int i = 0; i < ClienteRemoto.LIMITE_DELTA; i++) {
                pagina.add(clienteServidor(offset + i, null));
            }
            return FakeCall.deRespuesta(Response.success(pagina));
        };

        sincronizador().sincronizar();

        assertEquals(SincronizadorClientes.MAX_PAGINAS, pedidos[0]);
    }

    @Test
    public void delta_aplicaCadaPaginaEnUnaSolaTransaccion() {
        List<ClienteDto> pagina = new ArrayList<>();
        for (int i = 0; i < ClienteRemoto.LIMITE_DELTA; i++) {
            pagina.add(clienteServidor(1000 + i, String.format("2026-08-01T%02d:00:00+00:00", i)));
        }
        api.respuestaPorPagina = (filtro, offset) -> offset == 0
                ? FakeCall.deRespuesta(Response.success(pagina))
                : FakeCall.deRespuesta(Response.success(List.of()));

        assertTrue(sincronizador().sincronizar().esOk());

        assertEquals(2, transacciones.veces);
        assertEquals(ClienteRemoto.LIMITE_DELTA, clientes.porIdLocal.size());
    }

    // ------------------------------------------------------------------ conflicto LWW

    @Test
    public void delta_servidorMasNuevoQueFilaEnError_pisaLaFilaYReportaLaPerdida() {
        ClienteEntity local = unCliente(1, 50, EstadoSync.ERROR);
        local.setActualizadoEn("2026-01-01T10:00:00+00:00");
        clientes.insertar(local);
        api.respuestaPorPagina = (filtro, offset) -> FakeCall.deRespuesta(Response.success(
                List.of(clienteServidor(50, "2026-06-01T10:00:00+00:00"))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esPermanente());
        assertEquals(SincronizadorClientes.CAMBIO_LOCAL_PERDIDO, resultado.getMensaje());
        assertEquals(EstadoSync.SINCRONIZADO.name(), clientes.porIdLocal(1).getEstadoSync());
    }

    // ------------------------------------------------------------------ helpers

    private static ClienteEntity unCliente(int idLocal, Integer idServidor, EstadoSync estado) {
        ClienteEntity c = new ClienteEntity();
        c.setIdLocal(idLocal);
        c.setIdServidor(idServidor);
        c.setNombre("Ana");
        c.setApellido("Cruz");
        c.setIdentidad("0801199512345");
        c.setTelefono("9988-1122");
        c.setActivo(true);
        c.setEstadoSync(estado.name());
        return c;
    }

    /** Fila mínima del servidor para los tests de paginación (P-029). */
    private ClienteDto clienteServidor(int idCliente, String marca) {
        String json = "{\"id_cliente\":" + idCliente + ",\"nombres\":\"Ana" + idCliente + "\","
                + "\"apellidos\":\"Cruz\",\"identidad\":\"0801\",\"telefono\":\"9999\","
                + "\"id_estado\":1,\"activo\":true,\"cantidad_pedidos\":0"
                + (marca == null ? "" : ",\"actualizado_en\":\"" + marca + "\"")
                + "}";
        return gson.fromJson(json, ClienteDto.class);
    }

    // ------------------------------------------------------------------ fakes

    private static final class FakeClienteDao implements ClienteDao {

        final Map<Integer, ClienteEntity> porIdLocal = new HashMap<>();
        final Map<Integer, ClienteEntity> porIdServidor = new HashMap<>();
        int siguienteId = 1;

        @Override
        public LiveData<List<ClienteEntity>> observarTodos() {
            throw new UnsupportedOperationException("el sincronizador no observa");
        }

        @Override
        public ClienteEntity porIdLocal(long idLocal) {
            return porIdLocal.get((int) idLocal);
        }

        @Override
        public ClienteEntity porIdServidor(int idServidor) {
            return porIdServidor.get(idServidor);
        }

        @Override
        public long insertar(ClienteEntity cliente) {
            if (cliente.getIdLocal() == 0) {
                cliente.setIdLocal(siguienteId++);
            }
            porIdLocal.put(cliente.getIdLocal(), cliente);
            if (cliente.getIdServidor() != null) {
                porIdServidor.put(cliente.getIdServidor(), cliente);
            }
            return cliente.getIdLocal();
        }

        @Override
        public void actualizar(ClienteEntity cliente) {
            porIdLocal.put(cliente.getIdLocal(), cliente);
            if (cliente.getIdServidor() != null) {
                porIdServidor.put(cliente.getIdServidor(), cliente);
            }
        }

        @Override
        public void borrar(ClienteEntity cliente) {
            porIdLocal.remove(cliente.getIdLocal());
        }

        @Override
        public int contarNoSincronizadas() {
            return 0;
        }
    }

    private static final class FakeSincronizacionDao implements SincronizacionDao {

        final Map<String, SincronizacionEntity> marcas = new HashMap<>();

        @Override
        public SincronizacionEntity porTabla(String tabla) {
            return marcas.get(tabla);
        }

        @Override
        public void guardar(SincronizacionEntity marca) {
            marcas.put(marca.getTabla(), marca);
        }
    }

    private static final class FakeSupabaseClienteApi implements SupabaseClienteApi {

        Call<List<ClienteDto>> respuestaCrear;
        Call<Void> respuestaActualizarCliente = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaBorrar = FakeCall.deRespuesta(Response.success(null));
        Call<Integer> respuestaBuscarOCrear = FakeCall.deRespuesta(Response.success(1));

        BiFunction<String, Integer, Call<List<ClienteDto>>> respuestaPorPagina =
                (filtro, offset) -> FakeCall.deRespuesta(Response.success(Collections.emptyList()));

        String ultimoFiltroPedido;
        int ultimoOffsetPedido;
        String ultimoFiltroBorrar;

        @Override
        public Call<List<ClienteDto>> listarClientesDesde(String bearerToken, String select,
                                                           String actualizadoEnMayorQue,
                                                           String orden, int limite,
                                                           int desplazamiento) {
            ultimoFiltroPedido = actualizadoEnMayorQue;
            ultimoOffsetPedido = desplazamiento;
            return respuestaPorPagina.apply(actualizadoEnMayorQue, desplazamiento);
        }

        @Override
        public Call<List<ClienteDto>> crearCliente(String bearerToken, CrearClienteDto cuerpo) {
            return respuestaCrear;
        }

        @Override
        public Call<Void> actualizarCliente(String bearerToken, String idClienteIgualA,
                                            ActualizarClienteDto cuerpo) {
            return respuestaActualizarCliente;
        }

        @Override
        public Call<Void> borrarCliente(String bearerToken, String idClienteIgualA) {
            ultimoFiltroBorrar = idClienteIgualA;
            return respuestaBorrar;
        }

        @Override
        public Call<Integer> buscarOCrearCliente(String bearerToken, BuscarOCrearClienteDto cuerpo) {
            return respuestaBuscarOCrear;
        }
    }
}

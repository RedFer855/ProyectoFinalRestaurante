package com.example.proyectofinalrestaurante.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.FakeOperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.MesaDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.remote.SupabaseMesaApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CambiarEstadoMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.MesaDto;
import com.example.proyectofinalrestaurante.data.repository.MesaRemoto;
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
 * Tests de {@link SincronizadorMesas}: drenado del outbox y sync delta.
 *
 * <p>El foco es <b>P-029</b> (2026-08-04): antes de este fix, el delta avanzaba la marca de
 * agua dentro del bucle de páginas, así que ≥50 filas con el mismo {@code actualizado_en} se
 * perdían a partir de la 51.ª. Los casos C1-C5 son los mismos que ya cubren
 * {@code SincronizadorMenuTest} y prueban el mismo bucle, portado acá.</p>
 */
public class SincronizadorMesasTest {

    private final FakeSupabaseMesaApi api = new FakeSupabaseMesaApi();
    private final FakeOperacionPendienteDao operaciones = new FakeOperacionPendienteDao();
    private final Outbox outbox = new Outbox(operaciones, TipoOperacion.Modulo.MESAS);
    private final FakeMesaDao mesas = new FakeMesaDao();
    private final FakeSincronizacionDao marcas = new FakeSincronizacionDao();
    private final Gson gson = new Gson();

    /** Cuenta transacciones abiertas, para verificar que el delta se aplica por página. */
    private final EjecutorDeTransaccionEspia transacciones = new EjecutorDeTransaccionEspia();

    private SincronizadorMesas sincronizador() {
        return new SincronizadorMesas(
                new MesaRemoto(api, () -> "token"), outbox, mesas, marcas, transacciones);
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
    public void modulo_esElDeMesas() {
        assertEquals(TipoOperacion.Modulo.MESAS, sincronizador().modulo());
    }

    @Test
    public void crearMesa_exitoso_seteaIdServidorYVaciaLaCola() {
        MesaEntity fila = unaMesa(1, null, EstadoSync.PENDIENTE);
        mesas.insertar(fila);
        outbox.encolar(TipoOperacion.CREAR_MESA, 1, null, null);
        api.respuestaCrear = FakeCall.deRespuesta(Response.success(
                List.of(mesaServidor(7, "2026-08-01T10:00:00+00:00"))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(7, mesas.porIdLocal(1).getIdServidor().intValue());
        assertEquals(EstadoSync.SINCRONIZADO.name(), mesas.porIdLocal(1).getEstadoSync());
        assertEquals(0, outbox.contar());
    }

    @Test
    public void actualizarMesa_sinIdServidor_seDescartaComoOk() {
        // La edición ya viajó en el CREAR pendiente (mismo pliegue que el Menú).
        mesas.insertar(unaMesa(1, null, EstadoSync.PENDIENTE));
        outbox.encolar(TipoOperacion.ACTUALIZAR_MESA, 1, null, null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
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
                Response.success(List.of(mesaServidor(7, "2026-08-01T10:00:00+00:00"))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertNull(api.ultimoFiltroPedido);
        assertEquals(EstadoSync.SINCRONIZADO.name(), mesas.porIdServidor(7).getEstadoSync());
        assertEquals("2026-08-01T10:00:00+00:00", marcas.porTabla(SincronizadorMesas.TABLA).getMarcaAgua());
    }

    // ------------------------------------------------------------------ delta: P-029 (C1-C5)

    @Test
    public void delta_cincuentaFilasConLaMismaMarca_noPierdeLasQueSiguen() {
        String mismaMarca = "2026-08-01T10:00:00+00:00";
        List<MesaDto> primeraPagina = new ArrayList<>();
        for (int i = 0; i < MesaRemoto.LIMITE_DELTA; i++) {
            primeraPagina.add(mesaServidor(1000 + i, mismaMarca));
        }
        api.respuestaPorPagina = (filtro, offset) -> offset == 0
                ? FakeCall.deRespuesta(Response.success(primeraPagina))
                : FakeCall.deRespuesta(Response.success(List.of(mesaServidor(2000, mismaMarca))));

        assertTrue(sincronizador().sincronizar().esOk());

        assertEquals(MesaRemoto.LIMITE_DELTA + 1, mesas.porIdLocal.size());
    }

    @Test
    public void delta_paginaCompleta_pideLaSiguienteConOffsetYLaMismaMarcaInicial() {
        List<MesaDto> primeraPagina = new ArrayList<>();
        for (int i = 0; i < MesaRemoto.LIMITE_DELTA; i++) {
            primeraPagina.add(mesaServidor(1000 + i, String.format("2026-08-01T%02d:00:00+00:00", i)));
        }
        api.respuestaPorPagina = (filtro, offset) -> offset == 0
                ? FakeCall.deRespuesta(Response.success(primeraPagina))
                : FakeCall.deRespuesta(Response.success(
                        // "50" > "49" lexicográficamente: la última página trae la marca
                        // más alta de toda la pasada, no una hora real (la primera página
                        // ya usó horas 00-49 como valores sintéticos).
                        List.of(mesaServidor(2000, "2026-08-01T50:00:00+00:00"))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(MesaRemoto.LIMITE_DELTA, api.ultimoOffsetPedido);
        assertEquals("2026-08-01T50:00:00+00:00",
                marcas.porTabla(SincronizadorMesas.TABLA).getMarcaAgua());
    }

    @Test
    public void delta_unaPaginaFalla_laMarcaNoAvanza() {
        List<MesaDto> primeraPagina = new ArrayList<>();
        for (int i = 0; i < MesaRemoto.LIMITE_DELTA; i++) {
            primeraPagina.add(mesaServidor(1000 + i, String.format("2026-08-01T%02d:00:00+00:00", i)));
        }
        api.respuestaPorPagina = (filtro, offset) -> offset == 0
                ? FakeCall.deRespuesta(Response.success(primeraPagina))
                : FakeCall.deFallo(new IOException("timeout"));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esTransitorio());
        assertNull(marcas.porTabla(SincronizadorMesas.TABLA));
    }

    @Test(timeout = 10_000)
    public void delta_servidorQueSiempreDevuelvePaginasLlenas_cortaPorElTopeDePaginas() {
        int[] pedidos = {0};
        api.respuestaPorPagina = (filtro, offset) -> {
            pedidos[0]++;
            List<MesaDto> pagina = new ArrayList<>();
            for (int i = 0; i < MesaRemoto.LIMITE_DELTA; i++) {
                pagina.add(mesaServidor(offset + i, null));
            }
            return FakeCall.deRespuesta(Response.success(pagina));
        };

        sincronizador().sincronizar();

        assertEquals(SincronizadorMesas.MAX_PAGINAS, pedidos[0]);
    }

    @Test
    public void delta_aplicaCadaPaginaEnUnaSolaTransaccion() {
        List<MesaDto> pagina = new ArrayList<>();
        for (int i = 0; i < MesaRemoto.LIMITE_DELTA; i++) {
            pagina.add(mesaServidor(1000 + i, String.format("2026-08-01T%02d:00:00+00:00", i)));
        }
        api.respuestaPorPagina = (filtro, offset) -> offset == 0
                ? FakeCall.deRespuesta(Response.success(pagina))
                : FakeCall.deRespuesta(Response.success(List.of()));

        assertTrue(sincronizador().sincronizar().esOk());

        assertEquals(2, transacciones.veces);
        assertEquals(MesaRemoto.LIMITE_DELTA, mesas.porIdLocal.size());
    }

    // ------------------------------------------------------------------ conflicto LWW

    @Test
    public void delta_servidorMasNuevoQueFilaEnError_pisaLaFilaYReportaLaPerdida() {
        MesaEntity local = unaMesa(1, 50, EstadoSync.ERROR);
        local.setActualizadoEn("2026-01-01T10:00:00+00:00");
        mesas.insertar(local);
        api.respuestaPorPagina = (filtro, offset) -> FakeCall.deRespuesta(Response.success(
                List.of(mesaServidor(50, "2026-06-01T10:00:00+00:00"))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esPermanente());
        assertEquals(SincronizadorMesas.CAMBIO_LOCAL_PERDIDO, resultado.getMensaje());
        assertEquals(EstadoSync.SINCRONIZADO.name(), mesas.porIdLocal(1).getEstadoSync());
    }

    // ------------------------------------------------------------------ helpers

    private static MesaEntity unaMesa(int idLocal, Integer idServidor, EstadoSync estado) {
        MesaEntity m = new MesaEntity();
        m.setIdLocal(idLocal);
        m.setIdServidor(idServidor);
        m.setNumeroMesa(4);
        m.setCapacidad(6);
        m.setUbicacion("Patio");
        m.setIdEstadoMesa(1);
        m.setEstadoMesa("Libre");
        m.setActivo(true);
        m.setEstadoSync(estado.name());
        return m;
    }

    /** Fila mínima del servidor para los tests de paginación (P-029). */
    private MesaDto mesaServidor(int idMesa, String marca) {
        String json = "{\"id_mesa\":" + idMesa + ",\"numero_mesa\":" + idMesa
                + ",\"capacidad\":4,\"ubicacion\":\"Patio\",\"id_estado_mesa\":1,"
                + "\"estado_mesa\":\"Libre\",\"id_estado\":1,\"activo\":true"
                + (marca == null ? "" : ",\"actualizado_en\":\"" + marca + "\"")
                + "}";
        return gson.fromJson(json, MesaDto.class);
    }

    // ------------------------------------------------------------------ fakes

    private static final class FakeMesaDao implements MesaDao {

        final Map<Integer, MesaEntity> porIdLocal = new HashMap<>();
        final Map<Integer, MesaEntity> porIdServidor = new HashMap<>();
        int siguienteId = 1;

        @Override
        public LiveData<List<MesaEntity>> observarTodas() {
            throw new UnsupportedOperationException("el sincronizador no observa");
        }

        @Override
        public MesaEntity porIdLocal(long idLocal) {
            return porIdLocal.get((int) idLocal);
        }

        @Override
        public MesaEntity porIdServidor(int idServidor) {
            return porIdServidor.get(idServidor);
        }

        @Override
        public long insertar(MesaEntity mesa) {
            if (mesa.getIdLocal() == 0) {
                mesa.setIdLocal(siguienteId++);
            }
            porIdLocal.put(mesa.getIdLocal(), mesa);
            if (mesa.getIdServidor() != null) {
                porIdServidor.put(mesa.getIdServidor(), mesa);
            }
            return mesa.getIdLocal();
        }

        @Override
        public void actualizar(MesaEntity mesa) {
            porIdLocal.put(mesa.getIdLocal(), mesa);
            if (mesa.getIdServidor() != null) {
                porIdServidor.put(mesa.getIdServidor(), mesa);
            }
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

    private static final class FakeSupabaseMesaApi implements SupabaseMesaApi {

        Call<List<MesaDto>> respuestaCrear;
        Call<Void> respuestaActualizarMesa = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaCambiarEstado = FakeCall.deRespuesta(Response.success(null));

        BiFunction<String, Integer, Call<List<MesaDto>>> respuestaPorPagina =
                (filtro, offset) -> FakeCall.deRespuesta(Response.success(Collections.emptyList()));

        String ultimoFiltroPedido;
        int ultimoOffsetPedido;

        @Override
        public Call<List<MesaDto>> listarMesasDesde(String bearerToken, String select,
                                                    String actualizadoEnMayorQue, String orden,
                                                    int limite, int desplazamiento) {
            ultimoFiltroPedido = actualizadoEnMayorQue;
            ultimoOffsetPedido = desplazamiento;
            return respuestaPorPagina.apply(actualizadoEnMayorQue, desplazamiento);
        }

        @Override
        public Call<List<MesaDto>> crearMesa(String bearerToken, CrearMesaDto cuerpo) {
            return respuestaCrear;
        }

        @Override
        public Call<Void> actualizarMesa(String bearerToken, String idMesaIgualA,
                                         ActualizarMesaDto cuerpo) {
            return respuestaActualizarMesa;
        }

        @Override
        public Call<Void> cambiarBajaMesa(String bearerToken, String idMesaIgualA,
                                          ActualizarMesaDto cuerpo) {
            return respuestaActualizarMesa;
        }

        @Override
        public Call<Void> cambiarEstadoMesa(String bearerToken, CambiarEstadoMesaDto cuerpo) {
            return respuestaCambiarEstado;
        }
    }
}

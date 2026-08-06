package com.example.proyectofinalrestaurante.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.local.dao.CategoriaDao;
import com.example.proyectofinalrestaurante.data.FakeOperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.PlatilloDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.CategoriaEntity;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.PlatilloEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.sync.payload.PayloadOperacion;
import com.example.proyectofinalrestaurante.data.remote.SupabaseMenuApi;
import com.example.proyectofinalrestaurante.data.remote.SupabaseStorageApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarCategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarPlatilloDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearCategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearPlatilloDto;
import com.example.proyectofinalrestaurante.data.remote.dto.PlatilloDto;
import com.example.proyectofinalrestaurante.data.repository.MenuRemoto;
import com.google.gson.Gson;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import okhttp3.RequestBody;
import okio.Buffer;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Tests de {@link SincronizadorMenu} (Plan Fase 2b, E5): drenado del outbox, bajada del
 * delta y conflicto last-write-wins.
 *
 * <p>Se testea con {@link MenuRemoto} real apoyado en fakes de la API y de Storage (mismo
 * patrón que los tests de 2a): así se cubre el camino completo sincronizador → remoto →
 * Retrofit sin mockear {@code final}s. Los DAOs de Room son fakes en memoria.</p>
 */
public class SincronizadorMenuTest {

    private static final String MARCA_VIEJA = "2026-01-01T10:00:00.000+00:00";
    private static final String MARCA_NUEVA = "2026-06-01T10:00:00.000+00:00";

    @Rule
    public TemporaryFolder temporal = new TemporaryFolder();

    private final FakePlatilloDao platillos = new FakePlatilloDao();
    private final FakeCategoriaDao categorias = new FakeCategoriaDao();
    private final FakeOperacionPendienteDao operaciones = new FakeOperacionPendienteDao();
    private final FakeSincronizacionDao sincronizacion = new FakeSincronizacionDao();
    private final Outbox outbox = new Outbox(operaciones, TipoOperacion.Modulo.MENU);
    private final FakeMenuApi api = new FakeMenuApi();
    private final FakeStorageApi storage = new FakeStorageApi();

    /** Cuenta transacciones abiertas, para verificar que el delta se aplica por página. */
    private final EjecutorDeTransaccionEspia transacciones = new EjecutorDeTransaccionEspia();

    private SincronizadorMenu sincronizador() {
        return sincronizador("token-válido");
    }

    private SincronizadorMenu sincronizador(String token) {
        MenuRemoto remoto = new MenuRemoto(api, storage, () -> token);
        return new SincronizadorMenu(remoto, outbox, platillos, categorias, sincronizacion,
                temporal.getRoot(), transacciones);
    }

    /**
     * Corre el bloque tal cual (los fakes de DAO no tienen transacciones reales) pero
     * lleva la cuenta: lo que importa verificar es cuántas veces se agrupa, no que SQLite
     * haga commit.
     */
    private static final class EjecutorDeTransaccionEspia implements EjecutorDeTransaccion {

        private int veces;

        @Override
        public void enTransaccion(Runnable bloque) {
            veces++;
            bloque.run();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static CategoriaEntity categoria(int idLocal, Integer idServidor, String estado) {
        CategoriaEntity c = new CategoriaEntity();
        c.setIdLocal(idLocal);
        c.setIdServidor(idServidor);
        c.setDescripcion("Entradas");
        c.setActivo(true);
        c.setEstadoSync(estado);
        return c;
    }

    private static PlatilloEntity platillo(int idLocal, Integer idServidor, String estado) {
        PlatilloEntity p = new PlatilloEntity();
        p.setIdLocal(idLocal);
        p.setIdServidor(idServidor);
        p.setNombre("Baleada sencilla");
        p.setDescripcion("Con frijoles");
        p.setPrecio(35.0);
        p.setIdCategoriaLocal(1);
        p.setActivo(true);
        p.setEstadoSync(estado);
        return p;
    }

    private static PlatilloDto platilloServidor(int idPlatillo, String marca, String nombre) {
        return new Gson().fromJson(
                "{\"id_platillo\":" + idPlatillo + ",\"nombre\":\"" + nombre + "\","
                        + "\"precio\":40.0,\"id_categoria\":1,\"id_estado\":1,"
                        + "\"actualizado_en\":\"" + marca + "\"}",
                PlatilloDto.class);
    }

    private static CategoriaDto categoriaServidor(int idCategoria, String marca) {
        return new Gson().fromJson(
                "{\"id_categoria\":" + idCategoria + ",\"descripcion\":\"Entradas\","
                        + "\"id_estado\":1,\"cantidad_platillos\":0,\"cantidad_platillos_activos\":0,"
                        + "\"actualizado_en\":\"" + marca + "\"}",
                CategoriaDto.class);
    }

    private static String jsonPlatillo(int idPlatillo, String marca, String nombre) {
        return "{\"id_platillo\":" + idPlatillo + ",\"nombre\":\"" + nombre + "\","
                + "\"precio\":40.0,\"id_categoria\":1,\"id_estado\":1,"
                + "\"actualizado_en\":\"" + marca + "\"}";
    }

    // ------------------------------------------------------------------ drenado: crear

    @Test
    public void crearPlatillo_categoriaAunSinIdServidor_esTransitorioYNoMueveLaCola() {
        categorias.insertar(categoria(1, null, "PENDIENTE"));
        platillos.insertar(platillo(1, null, "PENDIENTE"));
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, null, null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esTransitorio());
        assertTrue(resultado.getMensaje().contains("todavía no se sincronizó"));
        // La operación sigue en la cola para el próximo reintento.
        assertEquals(1, outbox.contar());
    }

    @Test
    public void crearPlatillo_exitoso_seteaIdServidorYMarcaYBorraLaImagenLocal() throws IOException {
        categorias.insertar(categoria(1, 10, "SINCRONIZADO"));
        platillos.insertar(platillo(1, null, "PENDIENTE"));
        File foto = temporal.newFile("foto.jpg");
        // El archivo tiene que tener bytes reales: MenuRemoto rechaza una imagen vacía.
        Files.write(foto.toPath(), new byte[]{1, 2, 3});
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, null, "foto.jpg");
        api.respuestaCrearPlatillo = FakeCall.deRespuesta(Response.success(
                List.of(new Gson().fromJson(jsonPlatillo(50, MARCA_NUEVA, "Baleada sencilla"),
                        PlatilloDto.class))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        PlatilloEntity fila = platillos.porIdLocal(1);
        assertEquals(50, fila.getIdServidor().intValue());
        assertEquals("SINCRONIZADO", fila.getEstadoSync());
        assertEquals(MARCA_NUEVA, fila.getActualizadoEn());
        assertEquals(0, outbox.contar());
        // La foto que viajó con el CREAR se borra del almacenamiento local al subirse.
        assertFalse(foto.exists());
        // Y se subió una vez con su MIME.
        assertEquals(1, storage.rutasSubidas.size());
        assertEquals("image/jpeg", storage.tipoSubido);
    }

    @Test
    public void crearPlatillo_falloPermanente_descartaYMarcaError() {
        categorias.insertar(categoria(1, 10, "SINCRONIZADO"));
        platillos.insertar(platillo(1, null, "PENDIENTE"));
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, null, null);
        api.respuestaCrearPlatillo = FakeCall.deRespuesta(Response.error(409,
                okhttp3.ResponseBody.create(okhttp3.MediaType.get("application/json"),
                        "{\"message\":\"nombre duplicado\"}")));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esPermanente());
        assertEquals("nombre duplicado", resultado.getMensaje());
        assertEquals("ERROR", platillos.porIdLocal(1).getEstadoSync());
        assertEquals(0, outbox.contar());
    }

    @Test
    public void crearPlatillo_falloTransitorio_cuentaIntentosYAgotadoCaeAPermanente() {
        categorias.insertar(categoria(1, 10, "SINCRONIZADO"));
        platillos.insertar(platillo(1, null, "PENDIENTE"));
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, null, null);
        api.respuestaCrearPlatillo = FakeCall.deRespuesta(Response.error(500,
                okhttp3.ResponseBody.create(okhttp3.MediaType.get("application/json"),
                        "{}")));

        SincronizadorMenu sincronizador = sincronizador();
        assertTrue(sincronizador.sincronizar().esTransitorio());
        assertEquals(1, outbox.contar());
        assertEquals(1, operaciones.porId(1).getIntentos());

        // Los intentos se consumen de a uno por pasada (MAX_INTENTOS = 3).
        assertTrue(sincronizador.sincronizar().esTransitorio());
        assertTrue(sincronizador.sincronizar().esTransitorio());
        assertEquals(3, operaciones.porId(1).getIntentos());

        ResultadoSync agotado = sincronizador.sincronizar();
        assertTrue(agotado.esPermanente());
        assertEquals("ERROR", platillos.porIdLocal(1).getEstadoSync());
        assertEquals(0, outbox.contar());
    }

    // ------------------------------------------------------------------ drenado: ediciones plegadas

    @Test
    public void actualizarPlatillo_sinFila_seDescartaComoOk() {
        // Sin fila local: el CREAR pendiente ya viajó con la edición (repo pliega).
        outbox.encolar(TipoOperacion.ACTUALIZAR_PLATILLO, 99, null, null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(0, outbox.contar());
    }

    @Test
    public void actualizarPlatillo_sinIdServidor_seDescartaComoOk() {
        // Todavía no se creó en el servidor: la edición ya se plegó al CREAR pendiente.
        platillos.insertar(platillo(1, null, "PENDIENTE"));
        outbox.encolar(TipoOperacion.ACTUALIZAR_PLATILLO, 1, null, null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(0, outbox.contar());
    }

    // ------------------------------------------------------------------ drenado: estado e imagen

    @Test
    public void cambiarEstadoPlatillo_usaElEstadoDeLaFilaYSoloEseCampo() {
        platillos.insertar(platillo(1, 7, "PENDIENTE"));
        platillos.porIdLocal(1).setActivo(false);
        outbox.encolar(TipoOperacion.CAMBIAR_ESTADO_PLATILLO, 1, null, null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals("eq.7", api.ultimoFiltroPlatillo);
        // Solo se PATCHea el estado, nada más: si viniera el nombre, Glide/la fila mentirían.
        String cuerpo = new Gson().toJson(api.ultimoActualizarPlatillo);
        assertTrue(cuerpo.contains("\"id_estado\":2"));
        assertFalse(cuerpo.contains("nombre"));
        assertEquals(0, outbox.contar());
    }

    @Test
    public void quitarImagen_enviaLaRutaViejaDelPayload() {
        platillos.insertar(platillo(1, 7, "PENDIENTE"));
        outbox.encolar(TipoOperacion.QUITAR_IMAGEN_PLATILLO, 1,
                PayloadOperacion.quitarImagen("vieja.jpg"), null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals("{\"ruta_imagen\":null}", api.cuerpoCrudoEnviado);
        assertEquals(1, storage.rutasBorradas.size());
        assertEquals("vieja.jpg", storage.rutasBorradas.get(0));
        assertEquals(0, outbox.contar());
    }

    // ------------------------------------------------------------------ drenado: categorías

    @Test
    public void crearCategoria_exitoso_seteaIdServidor() {
        categorias.insertar(categoria(1, null, "PENDIENTE"));
        outbox.encolar(TipoOperacion.CREAR_CATEGORIA, 1, null, null);
        api.respuestaCrearCategoria = FakeCall.deRespuesta(Response.success(
                List.of(categoriaServidor(5, MARCA_NUEVA))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        CategoriaEntity fila = categorias.porIdLocal(1);
        assertEquals(5, fila.getIdServidor().intValue());
        assertEquals("SINCRONIZADO", fila.getEstadoSync());
        assertEquals(0, outbox.contar());
    }

    @Test
    public void borrarCategoria_usaElIdServidorDelPayload() {
        outbox.encolar(TipoOperacion.BORRAR_CATEGORIA, 1,
                PayloadOperacion.borrarCategoria(9), null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals("eq.9", api.ultimoFiltroCategoria);
        assertEquals(0, outbox.contar());
    }

    // ------------------------------------------------------------------ delta

    @Test
    public void delta_sinMarca_bajaTodaLaTablaEnLaPrimeraPasada() {
        // Sin marca de agua y sin outbox: la primera bajada trae todo (filtro null → sin query).
        api.respuestaListarCategoriasDesde = (filter, offset) -> FakeCall.deRespuesta(
                Response.success(List.of(categoriaServidor(5, MARCA_NUEVA))));
        api.respuestaListarPlatillosDesde = (filter, offset) -> FakeCall.deRespuesta(
                Response.success(List.of(platilloServidor(50, MARCA_NUEVA, "Baleada"))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertNull(api.ultimoFiltroCategorias);
        assertNull(api.ultimoFiltroPlatillos);
        assertEquals("SINCRONIZADO", categorias.porIdLocal(1).getEstadoSync());
        assertEquals("SINCRONIZADO", platillos.porIdLocal(1).getEstadoSync());
        assertEquals(MARCA_NUEVA, sincronizacion.porTabla("categorias").getMarcaAgua());
        assertEquals(MARCA_NUEVA, sincronizacion.porTabla("platillos").getMarcaAgua());
    }

    @Test
    public void delta_paginaCompleta_pideLaSiguienteConOffsetYLaMismaMarca() {
        // Primera página: exactamente LIMITE_DELTA (50) filas → hay que pedir la siguiente.
        // Segunda: menos de 50 → terminó.
        List<PlatilloDto> primeraPagina = new ArrayList<>();
        for (int i = 0; i < MenuRemoto.LIMITE_DELTA; i++) {
            primeraPagina.add(new Gson().fromJson(
                    jsonPlatillo(1000 + i, String.format("2026-07-01T%02d:00:00+00:00", i),
                            "Platillo " + i), PlatilloDto.class));
        }
        api.respuestaListarCategoriasDesde = (filter, offset) -> FakeCall.deRespuesta(
                Response.success(List.of()));
        api.respuestaListarPlatillosDesde = (filter, offset) -> {
            if (offset == 0) {
                return FakeCall.deRespuesta(Response.success(primeraPagina));
            }
            return FakeCall.deRespuesta(Response.success(List.of(
                    platilloServidor(2000, "2026-07-01T50:00:00+00:00", "Último"))));
        };

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        // Antes, la segunda consulta avanzaba el filtro a la marca más alta de la primera
        // página. Ahora la marca queda fija en toda la pasada y lo que avanza es el offset:
        // así, 50 filas con el mismo actualizado_en no esconden a las que vienen después.
        assertNull(api.ultimoFiltroPlatillos);
        assertEquals(MenuRemoto.LIMITE_DELTA, api.ultimoOffsetPlatillos);
        // La marca de agua queda en el máximo de toda la bajada, guardada al final.
        assertEquals("2026-07-01T50:00:00+00:00",
                sincronizacion.porTabla("platillos").getMarcaAgua());
    }

    @Test
    public void delta_cincuentaFilasConLaMismaMarca_noPierdeLasQueSiguen() {
        // El escenario de "instalación desde cero" contra un catálogo sembrado con un
        // INSERT masivo: TODAS las filas comparten actualizado_en. Con la paginación vieja
        // (avanzar por marca de agua), la página 2 pedía gt.<esa misma marca> y las filas
        // 51 en adelante no se bajaban NUNCA.
        String mismaMarca = "2026-07-01T10:00:00+00:00";
        List<PlatilloDto> primeraPagina = new ArrayList<>();
        for (int i = 0; i < MenuRemoto.LIMITE_DELTA; i++) {
            primeraPagina.add(new Gson().fromJson(
                    jsonPlatillo(1000 + i, mismaMarca, "Platillo " + i), PlatilloDto.class));
        }
        api.respuestaListarCategoriasDesde = (filter, offset) -> FakeCall.deRespuesta(
                Response.success(List.of()));
        api.respuestaListarPlatillosDesde = (filter, offset) -> {
            if (offset == 0) {
                return FakeCall.deRespuesta(Response.success(primeraPagina));
            }
            return FakeCall.deRespuesta(Response.success(List.of(
                    platilloServidor(2000, mismaMarca, "El que se perdía"))));
        };

        assertTrue(sincronizador().sincronizar().esOk());

        // Las 51: las 50 de la primera página más la que antes quedaba invisible.
        assertEquals(MenuRemoto.LIMITE_DELTA + 1, platillos.porIdLocal.size());
    }

    @Test(timeout = 10_000)
    public void delta_servidorQueSiempreDevuelvePaginasLlenas_cortaPorElTopeDePaginas() {
        // Con la paginación vieja, una página llena cuyas filas no traen actualizado_en
        // dejaba la marca sin avanzar y el while(true) no tenía salida: el worker giraba
        // hasta que WorkManager lo mataba por tiempo. Ahora el que avanza es el offset y
        // MAX_PAGINAS pone un techo duro.
        //
        // El fake simula el peor caso: páginas llenas, siempre, sin marca.
        int[] pedidos = {0};
        api.respuestaListarCategoriasDesde = (filter, offset) -> FakeCall.deRespuesta(
                Response.success(List.of()));
        api.respuestaListarPlatillosDesde = (filter, offset) -> {
            pedidos[0]++;
            List<PlatilloDto> pagina = new ArrayList<>();
            for (int i = 0; i < MenuRemoto.LIMITE_DELTA; i++) {
                pagina.add(new Gson().fromJson(
                        "{\"id_platillo\":" + (offset + i) + ",\"nombre\":\"Fila " + i + "\","
                                + "\"precio\":40.0,\"id_categoria\":1,\"id_estado\":1}",
                        PlatilloDto.class));
            }
            return FakeCall.deRespuesta(Response.success(pagina));
        };

        sincronizador().sincronizar();

        // Lo que se protege es que termine y que lo haga por el tope, no por agotar la
        // paciencia de WorkManager. El @Test(timeout) cubre el "no se cuelga".
        assertEquals(SincronizadorMenu.MAX_PAGINAS, pedidos[0]);
    }

    @Test
    public void delta_aplicaCadaPaginaEnUnaSolaTransaccion() {
        // 50 platillos en una página: sin agrupar serían 50 transacciones SQLite y 50
        // invalidaciones de tabla, o sea 50 re-emisiones de Room y 50 repintados del
        // RecyclerView. Eso es lo que se veía como "los items cargaron de a poco".
        List<PlatilloDto> pagina = new ArrayList<>();
        for (int i = 0; i < MenuRemoto.LIMITE_DELTA; i++) {
            pagina.add(new Gson().fromJson(
                    jsonPlatillo(1000 + i, String.format("2026-07-01T%02d:00:00+00:00", i),
                            "Platillo " + i), PlatilloDto.class));
        }
        api.respuestaListarCategoriasDesde = (filter, offset) -> FakeCall.deRespuesta(
                Response.success(List.of()));
        api.respuestaListarPlatillosDesde = (filter, offset) -> {
            if (offset == 0) {
                return FakeCall.deRespuesta(Response.success(pagina));
            }
            return FakeCall.deRespuesta(Response.success(List.of()));
        };

        assertTrue(sincronizador().sincronizar().esOk());

        // Una por página, no una por fila: categorías (1) + platillos llenos (1) +
        // platillos vacíos que cierran la paginación (1).
        assertEquals(3, transacciones.veces);
        assertEquals(MenuRemoto.LIMITE_DELTA, platillos.porIdLocal.size());
    }

    // ------------------------------------------------------------------ conflicto LWW (§4.6)

    @Test
    public void delta_servidorMasNuevoQueFilaEnError_pisaLaFilaYReportaLaPerdida() {
        PlatilloEntity local = platillo(1, 50, "ERROR");
        local.setActualizadoEn(MARCA_VIEJA);
        platillos.insertar(local);
        api.respuestaListarCategoriasDesde = (filter, offset) -> FakeCall.deRespuesta(Response.success(
                List.of()));
        api.respuestaListarPlatillosDesde = (filter, offset) -> FakeCall.deRespuesta(Response.success(
                List.of(platilloServidor(50, MARCA_NUEVA, "Versión del servidor"))));

        ResultadoSync resultado = sincronizador().sincronizar();

        // El conflicto se reporta como permanente para que la UI avise al usuario.
        assertTrue(resultado.esPermanente());
        assertEquals(SincronizadorMenu.CAMBIO_LOCAL_PERDIDO, resultado.getMensaje());
        PlatilloEntity fila = platillos.porIdLocal(1);
        assertEquals("Versión del servidor", fila.getNombre());
        assertEquals("SINCRONIZADO", fila.getEstadoSync());
        assertEquals(MARCA_NUEVA, fila.getActualizadoEn());
    }

    @Test
    public void delta_filaLocalMasNuevaQueServidor_noSePisa() {
        PlatilloEntity local = platillo(1, 50, "ERROR");
        local.setActualizadoEn(MARCA_NUEVA);
        platillos.insertar(local);
        api.respuestaListarCategoriasDesde = (filter, offset) -> FakeCall.deRespuesta(Response.success(
                List.of()));
        api.respuestaListarPlatillosDesde = (filter, offset) -> FakeCall.deRespuesta(Response.success(
                List.of(platilloServidor(50, MARCA_VIEJA, "Versión vieja"))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals("Baleada sencilla", platillos.porIdLocal(1).getNombre());
    }

    // ------------------------------------------------------------------ sin sesión

    @Test
    public void sincronizar_sinToken_esTransitorio() {
        // Con token nulo, MenuRemoto responde 401 (SIN_SESION): transitorio, se reintenta.
        api.respuestaListarCategoriasDesde = (filter, offset) -> FakeCall.deRespuesta(Response.success(
                List.of()));

        ResultadoSync resultado = sincronizador(null).sincronizar();

        assertTrue(resultado.esTransitorio());
    }

    // ------------------------------------------------------------------ fakes de DAOs

    private static final class FakePlatilloDao implements PlatilloDao {

        // Conteos del dashboard (Fase 3c): el sincronizador no observa.
        @Override
        public LiveData<Integer> observarConteoActivos() {
            throw new UnsupportedOperationException("el sincronizador no observa");
        }

        final Map<Integer, PlatilloEntity> porIdLocal = new LinkedHashMap<>();
        final Map<Integer, PlatilloEntity> porIdServidor = new HashMap<>();
        int siguienteId = 1;

        @Override
        public LiveData<List<PlatilloEntity>> observarTodos() {
            return null;
        }

        @Override
        public PlatilloEntity porIdLocal(long idLocal) {
            return porIdLocal.get((int) idLocal);
        }

        @Override
        public PlatilloEntity porIdServidor(int idServidor) {
            return porIdServidor.get(idServidor);
        }

        @Override
        public long insertar(PlatilloEntity platillo) {
            if (platillo.getIdLocal() == 0) {
                platillo.setIdLocal(siguienteId++);
            }
            porIdLocal.put(platillo.getIdLocal(), platillo);
            if (platillo.getIdServidor() != null) {
                porIdServidor.put(platillo.getIdServidor(), platillo);
            }
            return platillo.getIdLocal();
        }

        @Override
        public void actualizar(PlatilloEntity platillo) {
            porIdLocal.put(platillo.getIdLocal(), platillo);
            if (platillo.getIdServidor() != null) {
                porIdServidor.put(platillo.getIdServidor(), platillo);
            }
        }

        @Override
        public void borrar(PlatilloEntity platillo) {
            porIdLocal.remove(platillo.getIdLocal());
        }

        @Override
        public int contarNoSincronizados() {
            return 0;
        }
    }

    private static final class FakeCategoriaDao implements CategoriaDao {

        final Map<Integer, CategoriaEntity> porIdLocal = new LinkedHashMap<>();
        final Map<Integer, CategoriaEntity> porIdServidor = new HashMap<>();
        int siguienteId = 1;

        @Override
        public LiveData<List<CategoriaEntity>> observarTodas() {
            return null;
        }

        @Override
        public CategoriaEntity porIdLocal(long idLocal) {
            return porIdLocal.get((int) idLocal);
        }

        @Override
        public CategoriaEntity porIdServidor(int idServidor) {
            return porIdServidor.get(idServidor);
        }

        @Override
        public long insertar(CategoriaEntity categoria) {
            if (categoria.getIdLocal() == 0) {
                categoria.setIdLocal(siguienteId++);
            }
            porIdLocal.put(categoria.getIdLocal(), categoria);
            if (categoria.getIdServidor() != null) {
                porIdServidor.put(categoria.getIdServidor(), categoria);
            }
            return categoria.getIdLocal();
        }

        @Override
        public void actualizar(CategoriaEntity categoria) {
            porIdLocal.put(categoria.getIdLocal(), categoria);
            if (categoria.getIdServidor() != null) {
                porIdServidor.put(categoria.getIdServidor(), categoria);
            }
        }

        @Override
        public void borrar(CategoriaEntity categoria) {
            porIdLocal.remove(categoria.getIdLocal());
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

    private static final class FakeMenuApi implements SupabaseMenuApi {

        Call<List<PlatilloDto>> respuestaCrearPlatillo;
        Call<List<CategoriaDto>> respuestaCrearCategoria;
        Call<Void> respuestaActualizarPlatillo = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaActualizarCategoria = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaBorrarCategoria = FakeCall.deRespuesta(Response.success(null));

        // BiFunction y no Function: desde que la paginación va por offset, un fake que
        // solo mira el filtro no puede distinguir la página 1 de la 2 (el filtro es el
        // mismo en toda la pasada).
        BiFunction<String, Integer, Call<List<CategoriaDto>>> respuestaListarCategoriasDesde =
                (filter, offset) -> FakeCall.deRespuesta(Response.success(List.of()));
        BiFunction<String, Integer, Call<List<PlatilloDto>>> respuestaListarPlatillosDesde =
                (filter, offset) -> FakeCall.deRespuesta(Response.success(List.of()));

        String ultimoFiltroPlatillos;
        String ultimoFiltroCategorias;
        int ultimoOffsetPlatillos;
        int ultimoOffsetCategorias;
        String ultimoFiltroPlatillo;
        String ultimoFiltroCategoria;
        String cuerpoCrudoEnviado;
        ActualizarPlatilloDto ultimoActualizarPlatillo;

        @Override
        public Call<List<PlatilloDto>> listarPlatillos(String bearerToken) {
            throw new UnsupportedOperationException("El sincronizador usa listarPlatillosDesde");
        }

        @Override
        public Call<List<CategoriaDto>> listarCategorias(String bearerToken) {
            throw new UnsupportedOperationException("El sincronizador usa listarCategoriasDesde");
        }

        @Override
        public Call<List<PlatilloDto>> listarPlatillosDesde(String bearerToken, String select,
                                                            String actualizadoEnMayorQue,
                                                            String orden, int limite,
                                                            int desplazamiento) {
            ultimoFiltroPlatillos = actualizadoEnMayorQue;
            ultimoOffsetPlatillos = desplazamiento;
            return respuestaListarPlatillosDesde.apply(actualizadoEnMayorQue, desplazamiento);
        }

        @Override
        public Call<List<CategoriaDto>> listarCategoriasDesde(String bearerToken, String select,
                                                              String actualizadoEnMayorQue,
                                                              String orden, int limite,
                                                              int desplazamiento) {
            ultimoFiltroCategorias = actualizadoEnMayorQue;
            ultimoOffsetCategorias = desplazamiento;
            return respuestaListarCategoriasDesde.apply(actualizadoEnMayorQue, desplazamiento);
        }

        @Override
        public Call<List<PlatilloDto>> crearPlatillo(String bearerToken, CrearPlatilloDto cuerpo) {
            return respuestaCrearPlatillo;
        }

        @Override
        public Call<Void> actualizarPlatillo(String bearerToken, String idPlatilloIgualA,
                                             ActualizarPlatilloDto cuerpo) {
            ultimoFiltroPlatillo = idPlatilloIgualA;
            ultimoActualizarPlatillo = cuerpo;
            return respuestaActualizarPlatillo;
        }

        @Override
        public Call<Void> actualizarPlatilloConCuerpoCrudo(String bearerToken,
                                                           String idPlatilloIgualA,
                                                           RequestBody cuerpoJson) {
            ultimoFiltroPlatillo = idPlatilloIgualA;
            cuerpoCrudoEnviado = leer(cuerpoJson);
            return respuestaActualizarPlatillo;
        }

        @Override
        public Call<List<CategoriaDto>> crearCategoria(String bearerToken, CrearCategoriaDto cuerpo) {
            return respuestaCrearCategoria;
        }

        @Override
        public Call<Void> actualizarCategoria(String bearerToken, String idCategoriaIgualA,
                                              ActualizarCategoriaDto cuerpo) {
            ultimoFiltroCategoria = idCategoriaIgualA;
            return respuestaActualizarCategoria;
        }

        @Override
        public Call<Void> borrarCategoria(String bearerToken, String idCategoriaIgualA) {
            ultimoFiltroCategoria = idCategoriaIgualA;
            return respuestaBorrarCategoria;
        }

        private static String leer(RequestBody cuerpo) {
            try (Buffer buffer = new Buffer()) {
                cuerpo.writeTo(buffer);
                return buffer.readString(StandardCharsets.UTF_8);
            } catch (IOException ex) {
                return null;
            }
        }
    }

    private static final class FakeStorageApi implements SupabaseStorageApi {

        final List<String> rutasSubidas = new ArrayList<>();
        final List<String> rutasBorradas = new ArrayList<>();
        String tipoSubido;

        Call<Void> respuestaSubir = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaBorrar = FakeCall.deRespuesta(Response.success(null));

        @Override
        public Call<Void> subir(String bearerToken, String ruta, RequestBody imagen) {
            if (imagen.contentType() != null) {
                tipoSubido = imagen.contentType().toString();
            }
            rutasSubidas.add(ruta);
            return respuestaSubir;
        }

        @Override
        public Call<Void> borrar(String bearerToken, String ruta) {
            rutasBorradas.add(ruta);
            return respuestaBorrar;
        }
    }
}

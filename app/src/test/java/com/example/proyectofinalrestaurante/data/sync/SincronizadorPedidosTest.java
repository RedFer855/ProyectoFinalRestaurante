package com.example.proyectofinalrestaurante.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.FakeOperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.NotificacionDao;
import com.example.proyectofinalrestaurante.data.local.dao.PedidoDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.NotificacionEntity;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.PedidoEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.remote.SupabasePedidoApi;
import com.example.proyectofinalrestaurante.data.remote.dto.AvanzarEstadoPedidoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.PedidoDto;
import com.example.proyectofinalrestaurante.data.repository.PedidoRemoto;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Tests de {@link SincronizadorPedidos} (Plan Fase 3, E4): drenado del outbox,
 * bajada del delta con el molde del Menú (marca fija + offset, B4) y derivación de
 * notificaciones del buzón (B6, B7) con su idempotencia (B5).
 */
public class SincronizadorPedidosTest {

    private static final String PISO = "2026-08-02T12:00:00.000+00:00";
    private static final String MARCA_V1 = "2026-08-04T12:00:00.000+00:00";
    private static final String MARCA_V2 = "2026-08-04T13:00:00.000+00:00";

    private final FakePedidoDao pedidos = new FakePedidoDao();
    private final FakeNotificacionDao notificaciones = new FakeNotificacionDao();
    private final FakeOperacionPendienteDao operaciones = new FakeOperacionPendienteDao();
    private final FakeSincronizacionDao sincronizacion = new FakeSincronizacionDao();
    private final Outbox outbox = new Outbox(operaciones, TipoOperacion.Modulo.PEDIDOS);
    private final FakePedidoApi api = new FakePedidoApi();

    /** Cuenta transacciones abiertas, para verificar que el delta se aplica por página. */
    private final EjecutorDeTransaccionEspia transacciones = new EjecutorDeTransaccionEspia();

    private SincronizadorPedidos sincronizador() {
        return sincronizador("token-válido");
    }

    private SincronizadorPedidos sincronizador(String token) {
        PedidoRemoto remoto = new PedidoRemoto(api, () -> token);
        return new SincronizadorPedidos(remoto, outbox, pedidos, notificaciones, sincronizacion,
                transacciones, () -> PISO, () -> 1000L);
    }

    // ------------------------------------------------------------------ helpers

    private static PedidoDto pedido(int id, String marca, int idEstado, String idAuth) {
        return new Gson().fromJson(
                "{\"id_pedido\":" + id + ",\"fecha\":\"2026-08-04T12:00:00.000+00:00\","
                        + "\"id_estado_pedido\":" + idEstado + ",\"estado_pedido\":\"Listo\","
                        + "\"id_estado\":1,\"id_mesa\":4,\"numero_mesa\":4,"
                        + "\"id_cliente\":1,\"cliente\":\"Ana Cruz\","
                        + "\"id_tipo_pedido\":1,\"tipo_pedido\":\"En mesa\",\"id_usuario\":2,"
                        + "\"id_auth_usuario\":\"" + idAuth + "\",\"total\":380.0,"
                        + "\"cantidad_items\":3,\"actualizado_en\":\"" + marca + "\"}",
                PedidoDto.class);
    }

    private static PedidoDto pedido(int id, String marca, int idEstado) {
        return pedido(id, marca, idEstado, "uuid-mesero");
    }

    private static PedidoEntity pedidoEn(int idLocal, Integer idServidor, int idEstado,
                                         String estadoSync, String marca) {
        PedidoEntity e = new PedidoEntity();
        e.setIdLocal(idLocal);
        e.setIdServidor(idServidor);
        e.setFecha("2026-08-04T12:00:00.000+00:00");
        e.setIdEstadoPedido(idEstado);
        e.setNumeroMesa(4);
        e.setCliente("Ana Cruz");
        e.setTotal(380.0);
        e.setCantidadItems(3);
        e.setIdAuthUsuario("uuid-mesero");
        e.setActualizadoEn(marca);
        e.setEstadoSync(estadoSync);
        return e;
    }

    private void responderPaginado(List<PedidoDto> filas) {
        api.respuestaListarDesde = (filtro, offset) -> {
            if (offset >= filas.size()) {
                return FakeCall.deRespuesta(Response.success(List.of()));
            }
            int hasta = Math.min(offset + PedidoRemoto.LIMITE_DELTA, filas.size());
            return FakeCall.deRespuesta(Response.success(filas.subList(offset, hasta)));
        };
    }

    private int contarNotificacionesTipo(String tipo) {
        int cuenta = 0;
        for (NotificacionEntity n : notificaciones.filas()) {
            if (tipo.equals(n.getTipo())) {
                cuenta++;
            }
        }
        return cuenta;
    }

    // ------------------------------------------------------------------ arranque en frío y delta

    @Test
    public void primeraPasada_sinMarca_pideDesdeElPisoDe48Horas() {
        responderPaginado(List.of());

        sincronizador().sincronizar();

        assertEquals("gt." + PISO, api.ultimoFiltro);
    }

    @Test
    public void pasadaSiguiente_conMarca_pideDesdeLaMarcaGuardada() {
        sincronizacion.guardar(marca(MARCA_V1));
        responderPaginado(List.of());

        sincronizador().sincronizar();

        assertEquals("gt." + MARCA_V1, api.ultimoFiltro);
    }

    @Test
    public void primeraPasada_guardaLaMarcaMaximaDeLasFilas() {
        responderPaginado(List.of(pedido(1, MARCA_V1, 1), pedido(2, MARCA_V2, 1)));

        sincronizador().sincronizar();

        assertNotNull(sincronizacion.porTabla("pedidos"));
        assertEquals(MARCA_V2, sincronizacion.porTabla("pedidos").getMarcaAgua());
    }

    /**
     * B4: 120 filas comparten {@code actualizado_en} — el bug de P-029 se manifiesta
     * exactamente acá. El delta avanza por {@code offset} con la marca fija y las 120
     * quedan en Room.
     */
    @Test
    public void deltaCon120FilasDelMismoInstante_bajaTodasSinPerderNinguna() {
        List<PedidoDto> filas = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            filas.add(pedido(i, MARCA_V1, 1));
        }
        responderPaginado(filas);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(120, pedidos.porIdServidor.size());
        assertEquals("gt." + PISO, api.ultimoFiltro);
        assertEquals(100, api.ultimoOffset);
    }

    @Test
    public void deltaConMasDeUnaPagina_aplicaCadaPaginaEnUnaTransaccion() {
        List<PedidoDto> filas = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            filas.add(pedido(i, MARCA_V1, 1));
        }
        responderPaginado(filas);

        sincronizador().sincronizar();

        // 3 páginas: 50, 50 y 20 (la parcial corta la pasada) = 3 transacciones, no 120.
        assertEquals(3, transacciones.veces);
    }

    @Test
    public void delta_filaNueva_creaPedidoNuevoParaCocina() {
        responderPaginado(List.of(pedido(41, MARCA_V1, 1)));

        sincronizador().sincronizar();

        assertEquals(1, contarNotificacionesTipo("PEDIDO_NUEVO"));
        NotificacionEntity notif = notificaciones.porTipo("PEDIDO_NUEVO");
        assertEquals("cocina", notif.getRolDestino());
        assertEquals("41", notif.getArg1());
        assertNull(notif.getDestinatarioAuth());
    }

    @Test
    public void delta_filaQuePasaAListo_creaPedidoListoParaElMeseroDelPedido() {
        pedidos.insertar(pedidoEn(1, 41, 2, "SINCRONIZADO", MARCA_V1));
        responderPaginado(List.of(pedido(41, MARCA_V2, 3, "uuid-del-mesero")));

        sincronizador().sincronizar();

        assertEquals(1, contarNotificacionesTipo("PEDIDO_LISTO"));
        NotificacionEntity notif = notificaciones.porTipo("PEDIDO_LISTO");
        assertEquals("uuid-del-mesero", notif.getDestinatarioAuth());
        assertEquals("41", notif.getArg1());
        assertNull(notif.getRolDestino());
    }

    @Test
    public void delta_filaActualizadaSinPasarAListo_noCreaNotificacion() {
        pedidos.insertar(pedidoEn(1, 41, 1, "SINCRONIZADO", MARCA_V1));
        responderPaginado(List.of(pedido(41, MARCA_V2, 2)));

        sincronizador().sincronizar();

        assertEquals(0, contarNotificacionesTipo("PEDIDO_LISTO"));
        assertEquals(0, contarNotificacionesTipo("PEDIDO_NUEVO"));
    }

    /**
     * B5: una pasada repetida (el delta se vuelve a aplicar cuando una pasada se cortó a
     * mitad) no duplica filas ni notificaciones — la {@code clave_unica} con IGNORE.
     */
    @Test
    public void deltaReaplicado_noDuplicaFilasNiNotificaciones() {
        List<PedidoDto> filas = List.of(pedido(41, MARCA_V1, 1));
        responderPaginado(filas);

        sincronizador().sincronizar();
        sincronizador().sincronizar();

        assertEquals(1, pedidos.porIdServidor.size());
        assertEquals(1, contarNotificacionesTipo("PEDIDO_NUEVO"));
    }

    @Test
    public void delta_filaQuePasaAListo_dosVeces_noDuplicaElAviso() {
        pedidos.insertar(pedidoEn(1, 41, 2, "SINCRONIZADO", MARCA_V1));
        responderPaginado(List.of(pedido(41, MARCA_V2, 3)));

        sincronizador().sincronizar();
        sincronizador().sincronizar();

        assertEquals(1, contarNotificacionesTipo("PEDIDO_LISTO"));
    }

    @Test
    public void delta_falloTransitorio_esTransitorioYNoGuardaMarca() {
        sincronizacion.guardar(marca(MARCA_V1));
        api.respuestaListarDesde = (filtro, offset) -> FakeCall.deRespuesta(Response.error(500,
                okhttp3.ResponseBody.create(okhttp3.MediaType.get("application/json"), "{}")));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esTransitorio());
        assertEquals(MARCA_V1, sincronizacion.porTabla("pedidos").getMarcaAgua());
    }

    @Test
    public void sincronizar_sinToken_esTransitorio() {
        responderPaginado(List.of(pedido(41, MARCA_V1, 1)));

        ResultadoSync resultado = sincronizador(null).sincronizar();

        assertTrue(resultado.esTransitorio());
    }

    // ------------------------------------------------------------------ drenado

    @Test
    public void drenar_avanzarEstadoExitoso_confirmaLaFilaYSacaLaOperacion() {
        pedidos.insertar(pedidoEn(1, 1042, 1, "PENDIENTE", MARCA_V1));
        outbox.encolar(TipoOperacion.AVANZAR_ESTADO_PEDIDO, 1,
                PayloadOperacion.avanzarEstado(2), null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(0, operaciones.filas().size());
        assertEquals(2, pedidos.porIdServidor.get(1042).getIdEstadoPedido());
        assertEquals("SINCRONIZADO", pedidos.porIdServidor.get(1042).getEstadoSync());
    }

    @Test
    public void drenar_avanzarEstado_sinFila_descarta() {
        outbox.encolar(TipoOperacion.AVANZAR_ESTADO_PEDIDO, 99,
                PayloadOperacion.avanzarEstado(2), null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(0, operaciones.filas().size());
    }

    @Test
    public void drenar_avanzarEstado_sinIdServidor_descarta() {
        pedidos.insertar(pedidoEn(1, null, 1, "PENDIENTE", MARCA_V1));
        outbox.encolar(TipoOperacion.AVANZAR_ESTADO_PEDIDO, 1,
                PayloadOperacion.avanzarEstado(2), null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(0, operaciones.filas().size());
    }

    @Test
    public void drenar_avanzarEstado_sinPayloadValido_descarta() {
        pedidos.insertar(pedidoEn(1, 1042, 1, "PENDIENTE", MARCA_V1));
        outbox.encolar(TipoOperacion.AVANZAR_ESTADO_PEDIDO, 1, "{}", null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(0, operaciones.filas().size());
    }

    @Test
    public void drenar_avanzarEstado_errorPermanente_marcaLaFilaYNotificaErrorSync() {
        pedidos.insertar(pedidoEn(1, 1042, 1, "PENDIENTE", MARCA_V1));
        outbox.encolar(TipoOperacion.AVANZAR_ESTADO_PEDIDO, 1,
                PayloadOperacion.avanzarEstado(4), null);
        api.respuestaAvanzar = FakeCall.deRespuesta(Response.error(400,
                okhttp3.ResponseBody.create(okhttp3.MediaType.get("application/json"),
                        "{\"message\":\"Ese pedido ya está cerrado.\"}")));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esPermanente());
        assertEquals(0, operaciones.filas().size());
        assertEquals("ERROR", pedidos.porIdServidor.get(1042).getEstadoSync());
        assertEquals(1, contarNotificacionesTipo("ERROR_SYNC"));
    }

    @Test
    public void drenar_avanzarEstado_errorTransitorio_cortaLaPasadaYLaOperacionQueda() {
        pedidos.insertar(pedidoEn(1, 1042, 1, "PENDIENTE", MARCA_V1));
        outbox.encolar(TipoOperacion.AVANZAR_ESTADO_PEDIDO, 1,
                PayloadOperacion.avanzarEstado(2), null);
        api.respuestaAvanzar = FakeCall.deRespuesta(Response.error(500,
                okhttp3.ResponseBody.create(okhttp3.MediaType.get("application/json"), "{}")));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esTransitorio());
        assertEquals(1, operaciones.filas().size());
        assertEquals(1, operaciones.filas().get(0).getIntentos());
    }

    @Test
    public void drenar_errorPermanente_conDeltaBien_noPisaElMensaje() {
        pedidos.insertar(pedidoEn(1, 1042, 1, "PENDIENTE", MARCA_V1));
        outbox.encolar(TipoOperacion.AVANZAR_ESTADO_PEDIDO, 1,
                PayloadOperacion.avanzarEstado(4), null);
        api.respuestaAvanzar = FakeCall.deRespuesta(Response.error(400,
                okhttp3.ResponseBody.create(okhttp3.MediaType.get("application/json"),
                        "{\"message\":\"Ese pedido ya está cerrado.\"}")));
        responderPaginado(List.of());

        ResultadoSync resultado = sincronizador().sincronizar();

        // El delta bajó bien pero la operación falló de forma permanente: la pasada
        // "terminó" con un error que el usuario debe ver.
        assertTrue(resultado.esPermanente());
        assertEquals("Ese pedido ya está cerrado.", resultado.getMensaje());
    }

    @Test
    public void sincronizar_operacionDeOtroModulo_noSeToca() {
        // Una operación encolada fuera de la partición PEDIDOS (p. ej. Mesas).
        OperacionPendienteEntity ajena = new OperacionPendienteEntity();
        ajena.setModulo(TipoOperacion.Modulo.MESAS);
        ajena.setTipo(TipoOperacion.CAMBIAR_ESTADO_MESA);
        ajena.setIdLocal(7);
        operaciones.encolar(ajena);
        responderPaginado(List.of());

        sincronizador().sincronizar();

        assertEquals(1, operaciones.filas().size());
    }

    @Test
    public void sincronizar_operacionDesconocida_seDescarta() {
        outbox.encolar("CREAR_PEDIDO_INEXISTENTE", 1, null, null);
        responderPaginado(List.of());

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esPermanente());
        assertEquals(0, operaciones.filas().size());
    }

    // ------------------------------------------------------------------ fakes

    private static final class EjecutorDeTransaccionEspia implements EjecutorDeTransaccion {

        private int veces;

        @Override
        public void enTransaccion(Runnable bloque) {
            veces++;
            bloque.run();
        }
    }

    private static SincronizacionEntity marca(String marcaAgua) {
        SincronizacionEntity e = new SincronizacionEntity();
        e.setTabla("pedidos");
        e.setMarcaAgua(marcaAgua);
        e.setUltimoIntento(1L);
        return e;
    }

    private static final class FakePedidoDao implements PedidoDao {

        final Map<Long, PedidoEntity> porIdLocal = new LinkedHashMap<>();
        final Map<Integer, PedidoEntity> porIdServidor = new HashMap<>();
        long siguienteId = 1;

        @Override
        public LiveData<List<PedidoEntity>> observarVentana(int ventana) {
            return null;
        }

        @Override
        public LiveData<Integer> contarTotal() {
            return null;
        }

        @Override
        public int contarNoSincronizadas() {
            return 0;
        }

        @Override
        public PedidoEntity porIdLocal(long idLocal) {
            return porIdLocal.get(idLocal);
        }

        @Override
        public PedidoEntity porIdServidor(int idServidor) {
            return porIdServidor.get(idServidor);
        }

        @Override
        public long insertar(PedidoEntity pedido) {
            if (pedido.getIdLocal() == 0) {
                pedido.setIdLocal(siguienteId++);
            }
            porIdLocal.put(pedido.getIdLocal(), pedido);
            if (pedido.getIdServidor() != null) {
                porIdServidor.put(pedido.getIdServidor(), pedido);
            }
            return pedido.getIdLocal();
        }

        @Override
        public void insertarTodos(List<PedidoEntity> lista) {
            for (PedidoEntity pedido : lista) {
                insertar(pedido);
            }
        }

        @Override
        public void actualizar(PedidoEntity pedido) {
            porIdLocal.put(pedido.getIdLocal(), pedido);
            if (pedido.getIdServidor() != null) {
                porIdServidor.put(pedido.getIdServidor(), pedido);
            }
        }
    }

    private static final class FakeNotificacionDao implements NotificacionDao {

        final Map<String, NotificacionEntity> porClaveUnica = new LinkedHashMap<>();
        long siguienteId = 1;

        List<NotificacionEntity> filas() {
            return new ArrayList<>(porClaveUnica.values());
        }

        NotificacionEntity porTipo(String tipo) {
            for (NotificacionEntity n : porClaveUnica.values()) {
                if (tipo.equals(n.getTipo())) {
                    return n;
                }
            }
            return null;
        }

        @Override
        public LiveData<List<NotificacionEntity>> observarBuzon(String rol, String idAuth,
                                                                int ventana) {
            return null;
        }

        @Override
        public LiveData<Integer> contarNoLeidas() {
            return null;
        }

        @Override
        public NotificacionEntity porIdLocal(long idLocal) {
            for (NotificacionEntity n : porClaveUnica.values()) {
                if (n.getIdLocal() == idLocal) {
                    return n;
                }
            }
            return null;
        }

        @Override
        public long insertar(NotificacionEntity notificacion) {
            if (porClaveUnica.containsKey(notificacion.getClaveUnica())) {
                return -1;
            }
            notificacion.setIdLocal(siguienteId++);
            porClaveUnica.put(notificacion.getClaveUnica(), notificacion);
            return notificacion.getIdLocal();
        }

        @Override
        public void insertarTodos(List<NotificacionEntity> lista) {
            for (NotificacionEntity n : lista) {
                insertar(n);
            }
        }

        @Override
        public void marcarLeida(long idLocal) {
            for (NotificacionEntity n : porClaveUnica.values()) {
                if (n.getIdLocal() == idLocal) {
                    n.setLeida(true);
                }
            }
        }

        @Override
        public void marcarTodasLeidas() {
            for (NotificacionEntity n : porClaveUnica.values()) {
                n.setLeida(true);
            }
        }

        @Override
        public int purgarLeidasViejas(long antesDe) {
            int borradas = 0;
            for (NotificacionEntity n : new ArrayList<>(porClaveUnica.values())) {
                if (n.isLeida() && n.getCreadoEn() < antesDe) {
                    porClaveUnica.remove(n.getClaveUnica());
                    borradas++;
                }
            }
            return borradas;
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

    private static final class FakePedidoApi implements SupabasePedidoApi {

        BiFunction<String, Integer, Call<List<PedidoDto>>> respuestaListarDesde =
                (filtro, offset) -> FakeCall.deRespuesta(Response.success(List.of()));
        Call<Void> respuestaAvanzar = FakeCall.deRespuesta(Response.success(null));

        String ultimoFiltro;
        int ultimoOffset;

        @Override
        public Call<List<PedidoDto>> listarPedidosDesde(String bearerToken, String select,
                                                        String actualizadoEnMayorQue,
                                                        String orden, int limite,
                                                        int desplazamiento) {
            ultimoFiltro = actualizadoEnMayorQue;
            ultimoOffset = desplazamiento;
            return respuestaListarDesde.apply(actualizadoEnMayorQue, desplazamiento);
        }

        @Override
        public Call<Void> avanzarEstado(String bearerToken, AvanzarEstadoPedidoDto cuerpo) {
            return respuestaAvanzar;
        }
    }
}
package com.example.proyectofinalrestaurante.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.FakeOperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.ClienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.MesaDao;
import com.example.proyectofinalrestaurante.data.local.dao.NotificacionDao;
import com.example.proyectofinalrestaurante.data.local.dao.PedidoDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;
import com.example.proyectofinalrestaurante.data.local.entity.NotificacionEntity;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.PedidoEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.sync.payload.PayloadCrearPedido;
import com.example.proyectofinalrestaurante.data.sync.payload.PayloadOperacion;
import com.example.proyectofinalrestaurante.data.remote.SupabasePedidoApi;
import com.example.proyectofinalrestaurante.data.remote.dto.AvanzarEstadoPedidoDto;

import com.example.proyectofinalrestaurante.data.remote.dto.PedidoDto;
import com.example.proyectofinalrestaurante.data.repository.PedidoRemoto;
import com.google.gson.Gson;

import org.junit.Test;

import java.io.IOException;
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
 * Tests de {@link SincronizadorPedidos} (Plan Fase 3, E4): drenado del outbox,
 * bajada del delta con el molde del Menú (marca fija + offset, B4) y derivación de
 * notificaciones del buzón (B6, B7) con su idempotencia (B5).
 */
public class SincronizadorPedidosTest {

    private static final String PISO = "2026-08-02T12:00:00.000+00:00";
    private static final String MARCA_V1 = "2026-08-04T12:00:00.000+00:00";
    private static final String MARCA_V2 = "2026-08-04T13:00:00.000+00:00";

    private final FakePedidoDao pedidos = new FakePedidoDao();
    private final FakeClienteDao clientes = new FakeClienteDao();
    private final FakeMesaDao mesas = new FakeMesaDao();
    private final FakeNotificacionDao notificaciones = new FakeNotificacionDao();
    private final FakeOperacionPendienteDao operaciones = new FakeOperacionPendienteDao();
    private final FakeSincronizacionDao sincronizacion = new FakeSincronizacionDao();
    private final Outbox outbox = new Outbox(operaciones, TipoOperacion.Modulo.PEDIDOS);
    private final Outbox outboxDeClientes = new Outbox(operaciones, TipoOperacion.Modulo.CLIENTES);
    private final FakePedidoApi api = new FakePedidoApi();

    /** Cuenta transacciones abiertas, para verificar que el delta se aplica por página. */
    private final EjecutorDeTransaccionEspia transacciones = new EjecutorDeTransaccionEspia();

    private SincronizadorPedidos sincronizador() {
        return sincronizador("token-válido");
    }

    private SincronizadorPedidos sincronizador(String token) {
        PedidoRemoto remoto = new PedidoRemoto(api, () -> token);
        return new SincronizadorPedidos(remoto, outbox, pedidos, notificaciones, sincronizacion,
                transacciones, mesas, clientes, outboxDeClientes, () -> PISO, () -> 1000L);
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

        // Solapamiento de la marca (Plan Fase 3b, §3.3): se pide `> marca − 2s` para
        // cerrar la ventana del cursor reloj. La fecha pierde los microsegundos al
        // reformatearse, pero el valor es el mismo instante menos dos segundos.
        assertEquals("gt." + "2026-08-04T11:59:58Z", api.ultimoFiltro);
    }

    /**
     * Plan Fase 3b, B8: el solapamiento de 2 s de la marca re-baja las filas de la frontera;
     * reaplicarlas no duplica ni pisa con datos viejos (idempotente por id_servidor + LWW).
     */
    @Test
    public void deltaConMarcaSolapadaDosSegundos_noDuplicaNiPisaDatosViejos() {
        sincronizacion.guardar(marca(MARCA_V1));
        // La misma fila ya está local y 'actualizada_en' en la frontera solapada.
        pedidos.insertar(pedidoEn(1, 41, 1, "SINCRONIZADO", MARCA_V1));
        responderPaginado(List.of(pedido(41, MARCA_V1, 1)));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(1, pedidos.porIdServidor.size());
        assertEquals(0, contarNotificacionesTipo("PEDIDO_NUEVO"));
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

    // ------------------------------------------------------------------ drenado: CREAR_PEDIDO

    /** Pedido local PENDIENTE sin idServidor (recién tomado offline). */
    private PedidoEntity pedidoPendiente(int idLocal, String cliente) {
        PedidoEntity e = new PedidoEntity();
        e.setIdLocal(idLocal);
        e.setIdServidor(null);
        e.setFecha("2026-08-05T12:00:00.000+00:00");
        e.setIdEstadoPedido(1);
        e.setNumeroMesa(4);
        e.setCliente(cliente);
        e.setTotal(380.0);
        e.setCantidadItems(3);
        e.setIdAuthUsuario("uuid-mesero");
        e.setActualizadoEn("2026-08-05T12:00:00.000+00:00");
        e.setEstadoSync("PENDIENTE");
        pedidos.insertar(e);
        return e;
    }

    private void encolarCrear(int idLocal, PayloadCrearPedido.Cuerpo cuerpo) {
        outbox.encolar(TipoOperacion.CREAR_PEDIDO, idLocal,
                PayloadCrearPedido.serializar(cuerpo.getClaveIdempotencia(), cuerpo.getFecha(),
                        cuerpo.getIdTipoPedido(), cuerpo.getMesaIdLocal(),
                        cuerpo.getClienteIdLocal(), cuerpo.getLineas()), null);
    }

    private static PayloadCrearPedido.Cuerpo cuerpoDeCarrito(Integer idMesaLocal,
                                                             Integer idClienteLocal) {
        return PayloadCrearPedido.parsear(PayloadCrearPedido.serializar(
                "uuid-idempotencia-1", "2026-08-05T12:00:00.000+00:00", 1,
                idMesaLocal, idClienteLocal,
                List.of(new PayloadCrearPedido.Linea(7, 2))));
    }

    /** B2: el mismo CREAR_PEDIDO drenado dos veces → una sola cabecera en el servidor. */
    @Test
    public void crearPedido_drenadoDosVeces_noGeneraDosCabeceras() {
        pedidoPendiente(1, "Ana Cruz");
        PayloadCrearPedido.Cuerpo cuerpo = cuerpoDeCarrito(null, null);
        encolarCrear(1, cuerpo);
        api.respuestaCrear = FakeCall.deRespuesta(Response.success(crearPedido(41)));

        ResultadoSync primera = sincronizador().sincronizar();
        ResultadoSync segunda = sincronizador().sincronizar();

        assertTrue(primera.esOk());
        assertTrue(segunda.esOk());
        assertEquals(0, operaciones.filas().size());
        assertEquals(41, (int) pedidos.porIdServidor.get(41).getIdServidor());
    }

    /** B3: cliente creado offline que ya drenó → sube con el id_cliente real. */
    @Test
    public void crearPedido_conClienteSincronizado_resuelveIdClienteReal() {
        pedidoPendiente(1, "Ana Cruz");
        ClienteEntity cliente = new ClienteEntity();
        cliente.setIdLocal(9);
        cliente.setIdServidor(55);
        clientes.porIdLocal.put(9L, cliente);
        PayloadCrearPedido.Cuerpo cuerpo = cuerpoDeCarrito(null, 9);
        encolarCrear(1, cuerpo);
        api.respuestaCrear = FakeCall.deRespuesta(Response.success(crearPedido(41)));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertTrue(api.ultimoPayloadCrear.contains("\"id_cliente\":55"));
        assertFalse(api.ultimoPayloadCrear.contains("cliente_id_local"));
    }

    /** B5: el CREAR_CLIENTE no drenó todavía → transitorio, sin consumir intento. */
    @Test
    public void crearPedido_clientePendienteSinDrenar_esTransitorioSinConsumirIntento() {
        pedidoPendiente(1, "Ana Cruz");
        ClienteEntity cliente = new ClienteEntity();
        cliente.setIdLocal(9);
        cliente.setIdServidor(null);
        clientes.porIdLocal.put(9L, cliente);
        outboxDeClientes.encolar(TipoOperacion.CREAR_CLIENTE, 9, "{}", null);
        PayloadCrearPedido.Cuerpo cuerpo = cuerpoDeCarrito(null, 9);
        encolarCrear(1, cuerpo);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esTransitorio());
        // La operación CREAR_PEDIDO queda intacta, con 0 intentos (no se consumió el intento).
        OperacionPendienteEntity op = operaciones.filas().stream()
                .filter(o -> TipoOperacion.CREAR_PEDIDO.equals(o.getTipo()))
                .findFirst().orElseThrow();
        assertEquals(0, op.getIntentos());
    }

    /** B4: el CREAR_CLIENTE se descartó → sube con id_cliente null + notificación. */
    @Test
    public void crearPedido_clienteDescartado_degradaIdClienteNullYNotifica() {
        pedidoPendiente(1, "Ana Cruz");
        ClienteEntity cliente = new ClienteEntity();
        cliente.setIdLocal(9);
        cliente.setIdServidor(null);
        clientes.porIdLocal.put(9L, cliente);
        PayloadCrearPedido.Cuerpo cuerpo = cuerpoDeCarrito(null, 9);
        encolarCrear(1, cuerpo);
        api.respuestaCrear = FakeCall.deRespuesta(Response.success(crearPedido(41)));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        // Gson omite los campos null: id_cliente degradado no aparece (Postgres lo toma null).
        assertFalse(api.ultimoPayloadCrear.contains("\"id_cliente\""));
        assertFalse(api.ultimoPayloadCrear.contains("cliente_id_local"));
        assertEquals(1, contarNotificacionesTipo("PEDIDO_SIN_CLIENTE"));
    }

    /** Mesa sin idServidor → sube con id_mesa null + notificación (Plan 3b §4.2). */
    @Test
    public void crearPedido_mesaSinIdServidor_degradaIdMesaNullYNotifica() {
        pedidoPendiente(1, "Ana Cruz");
        MesaEntity mesa = new MesaEntity();
        mesa.setIdLocal(4);
        mesa.setIdServidor(null);
        mesas.porIdLocal.put(4L, mesa);
        PayloadCrearPedido.Cuerpo cuerpo = cuerpoDeCarrito(4, null);
        encolarCrear(1, cuerpo);
        api.respuestaCrear = FakeCall.deRespuesta(Response.success(crearPedido(41)));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        // Gson omite los campos null: id_mesa degradado no aparece (Postgres lo toma null).
        assertFalse(api.ultimoPayloadCrear.contains("\"id_mesa\""));
        assertEquals(1, contarNotificacionesTipo("PEDIDO_SIN_MESA"));
    }

    /** Mesa con idServidor → sube con el id_mesa real. */
    @Test
    public void crearPedido_mesaSincronizada_resuelveIdMesaReal() {
        pedidoPendiente(1, "Ana Cruz");
        MesaEntity mesa = new MesaEntity();
        mesa.setIdLocal(4);
        mesa.setIdServidor(12);
        mesas.porIdLocal.put(4L, mesa);
        PayloadCrearPedido.Cuerpo cuerpo = cuerpoDeCarrito(4, null);
        encolarCrear(1, cuerpo);
        api.respuestaCrear = FakeCall.deRespuesta(Response.success(crearPedido(41)));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertTrue(api.ultimoPayloadCrear.contains("\"id_mesa\":12"));
    }

    /** Un platillo sin idServidor (payload lo trae como 0) → pedido a ERROR, permanente. */
    @Test
    public void crearPedido_conPlatilloSinIdServidor_esPermanenteYPedidoAError() {
        pedidoPendiente(1, "Ana Cruz");
        PayloadCrearPedido.Cuerpo cuerpo = PayloadCrearPedido.parsear(
                PayloadCrearPedido.serializar("uuid-idempotencia-1",
                        "2026-08-05T12:00:00.000+00:00", 1, null, null,
                        List.of(new PayloadCrearPedido.Linea(0, 2))));
        encolarCrear(1, cuerpo);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esPermanente());
        assertEquals(0, operaciones.filas().size());
        assertEquals("ERROR", pedidos.porIdLocal.get(1L).getEstadoSync());
    }

    /**
     * Respuesta del RPC: el id_pedido creado. Se decodifica desde el JSON crudo que manda
     * PostgREST —un entero escalar pelado, no {@code {"id_pedido": N}}— para que el fake
     * hable exactamente la misma forma que el servidor real (ver
     * {@code SupabasePedidoApi#crearPedido}).
     */
    private static Integer crearPedido(int idPedido) {
        return new Gson().fromJson(String.valueOf(idPedido), Integer.class);
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

        // Conteos del dashboard (Fase 3c): el sincronizador no observa.
        @Override
        public LiveData<Integer> observarConteoPorEstado(int idEstadoPedido) {
            throw new UnsupportedOperationException("el sincronizador no observa");
        }

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
        Call<Integer> respuestaCrear =
                FakeCall.deRespuesta(Response.success(0));

        String ultimoFiltro;
        int ultimoOffset;
        String ultimoPayloadCrear;

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

        @Override
        public Call<Integer> crearPedido(String bearerToken, RequestBody cuerpo) {
            try {
                Buffer buffer = new Buffer();
                cuerpo.writeTo(buffer);
                ultimoPayloadCrear = buffer.readUtf8();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            return respuestaCrear;
        }
    }

    /** Fake mínimo de {@link ClienteDao}: solo lo que el sincronizador usa para resolver ids. */
    private static final class FakeClienteDao implements ClienteDao {

        // Conteos del dashboard (Fase 3c): el sincronizador no observa.
        @Override
        public LiveData<Integer> observarConteoActivos() {
            throw new UnsupportedOperationException("el sincronizador no observa");
        }

        final Map<Long, ClienteEntity> porIdLocal = new HashMap<>();

        @Override
        public LiveData<List<ClienteEntity>> observarTodos() {
            return null;
        }

        @Override
        public ClienteEntity porIdLocal(long idLocal) {
            return porIdLocal.get(idLocal);
        }

        @Override
        public ClienteEntity porIdServidor(int idServidor) {
            return null;
        }

        @Override
        public long insertar(ClienteEntity cliente) {
            return 0;
        }

        @Override
        public void actualizar(ClienteEntity cliente) {
        }

        @Override
        public void borrar(ClienteEntity cliente) {
        }

        @Override
        public int contarNoSincronizadas() {
            return 0;
        }
    }

    /** Fake mínimo de {@link MesaDao}: solo lo que el sincronizador usa para resolver ids. */
    private static final class FakeMesaDao implements MesaDao {

        // Conteos del dashboard (Fase 3c): el sincronizador no observa.
        @Override
        public LiveData<Integer> observarConteoActivas() {
            throw new UnsupportedOperationException("el sincronizador no observa");
        }
        // Conteos del dashboard (Fase 3c): el sincronizador no observa.
        @Override
        public LiveData<Integer> observarConteoPorEstadoOperativo(int idEstadoMesa) {
            throw new UnsupportedOperationException("el sincronizador no observa");
        }

        final Map<Long, MesaEntity> porIdLocal = new HashMap<>();

        @Override
        public LiveData<List<MesaEntity>> observarTodas() {
            return null;
        }

        @Override
        public MesaEntity porIdLocal(long idLocal) {
            return porIdLocal.get(idLocal);
        }

        @Override
        public MesaEntity porIdServidor(int idServidor) {
            return null;
        }

        @Override
        public long insertar(MesaEntity mesa) {
            return 0;
        }

        @Override
        public void actualizar(MesaEntity mesa) {
        }

        @Override
        public int contarNoSincronizadas() {
            return 0;
        }
    }
}

package com.example.proyectofinalrestaurante.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.FakeOperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.EmpleadoDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.EmpleadoEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.remote.SupabaseEmpleadoApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarEmpleadoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarPerfilDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoResponseDto;
import com.example.proyectofinalrestaurante.data.remote.dto.EmpleadoDto;
import com.example.proyectofinalrestaurante.data.repository.EmpleadoRemoto;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.google.gson.Gson;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Tests de {@link SincronizadorEmpleados}: drenado del outbox y sync delta.
 *
 * <p>Todo con fakes en memoria, sin Room ni red. El {@code EmpleadoRemoto} es real y se le
 * inyecta un {@code SupabaseEmpleadoApi} falso: así lo que se prueba incluye el mapeo de
 * códigos HTTP a transitorio/permanente, que es lo que decide si una operación se reintenta
 * o se descarta.</p>
 */
public class SincronizadorEmpleadosTest {

    private final FakeSupabaseEmpleadoApi api = new FakeSupabaseEmpleadoApi();
    private final FakeOperacionPendienteDao operaciones = new FakeOperacionPendienteDao();
    private final Outbox outbox =
            new Outbox(operaciones, TipoOperacion.Modulo.EMPLEADOS);
    private final FakeEmpleadoDao empleados = new FakeEmpleadoDao();
    private final FakeSincronizacionDao marcas = new FakeSincronizacionDao();
    private final Gson gson = new Gson();

    /** Cuenta transacciones abiertas, para verificar que el delta se aplica por página. */
    private final EjecutorDeTransaccionEspia transacciones = new EjecutorDeTransaccionEspia();

    private SincronizadorEmpleados sincronizador() {
        return new SincronizadorEmpleados(
                new EmpleadoRemoto(api, () -> "token"), outbox, empleados, marcas, transacciones);
    }

    /**
     * Corre el bloque tal cual (los fakes de DAO no tienen transacciones reales) pero lleva
     * la cuenta: lo que importa verificar es cuántas veces se agrupa, no que SQLite haga
     * commit. Mismo espía que {@code SincronizadorMenuTest}.
     */
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
    public void cambiarRol_exitoso_marcaLaFilaSincronizadaYVaciaLaCola() {
        empleados.guardar(unEmpleado(1, EstadoSync.PENDIENTE));
        outbox.encolar(TipoOperacion.CAMBIAR_ROL_EMPLEADO, 1, null, null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(EstadoSync.SINCRONIZADO.name(), empleados.porId(1).getEstadoSync());
        assertEquals(0, outbox.contar());
    }

    @Test
    public void errorDelTrigger_descartaLaOperacionYDejaLaFilaEnError() {
        empleados.guardar(unEmpleado(1, EstadoSync.PENDIENTE));
        outbox.encolar(TipoOperacion.CAMBIAR_ROL_EMPLEADO, 1, null, null);
        api.respuestaActualizarPerfil = FakeCall.deRespuesta(Response.error(400,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"No se puede modificar a otro administrador\"}")));

        ResultadoSync resultado = sincronizador().sincronizar();

        // 400 es permanente: reintentarlo para siempre bloquearía la cola detrás.
        assertTrue(resultado.esPermanente());
        assertEquals("No se puede modificar a otro administrador", resultado.getMensaje());
        assertEquals(0, outbox.contar());
        assertEquals(EstadoSync.ERROR.name(), empleados.porId(1).getEstadoSync());
    }

    @Test
    public void sinConexion_conservaLaOperacionYCuentaElIntento() {
        empleados.guardar(unEmpleado(1, EstadoSync.PENDIENTE));
        long id = outbox.encolar(TipoOperacion.ACTUALIZAR_EMPLEADO, 1, null, null);
        api.respuestaActualizarEmpleado = FakeCall.deFallo(new IOException("timeout"));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esTransitorio());
        // La operación sigue en la cola: el worker va a reintentar con backoff.
        assertEquals(1, outbox.contar());
        assertEquals(1, operaciones.porId(id).getIntentos());
        // Y la fila NO queda en ERROR: no falló, todavía no se pudo subir.
        assertEquals(EstadoSync.PENDIENTE.name(), empleados.porId(1).getEstadoSync());
    }

    @Test
    public void filaBorrada_descartaLaOperacionSinRomper() {
        outbox.encolar(TipoOperacion.ACTUALIZAR_EMPLEADO, 99, null, null);

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

    @Test
    public void noTocaLasOperacionesDelMenu() {
        // El bug que la partición del outbox evita: sin ella, este CREAR_PLATILLO caería en
        // el `default` del sincronizador de Empleados y se borraría en silencio.
        Outbox delMenu = new Outbox(operaciones, TipoOperacion.Modulo.MENU);
        delMenu.encolar(TipoOperacion.CREAR_PLATILLO, 1, null, null);

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(1, delMenu.contar());
    }

    // ------------------------------------------------------------------ delta

    @Test
    public void delta_insertaLosEmpleadosNuevosYGuardaLaMarca() {
        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista(
                "{\"id_empleado\":7,\"nombres\":\"Marta\",\"apellidos\":\"Zelaya\","
                        + "\"identidad\":\"0801\",\"correo\":\"m@r.hn\",\"id_usuario\":3,"
                        + "\"apodo_usuario\":\"mzelaya\",\"id_auth_user\":\"uuid-7\","
                        + "\"rol\":\"mesero\",\"activo\":true,"
                        + "\"actualizado_en\":\"2026-08-01 12:00:00+00\"}")));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        EmpleadoEntity bajado = empleados.porId(7);
        assertNotNull(bajado);
        assertEquals("mzelaya", bajado.getApodoUsuario());
        assertEquals(EstadoSync.SINCRONIZADO.name(), bajado.getEstadoSync());
        assertEquals("2026-08-01 12:00:00+00",
                marcas.porTabla(SincronizadorEmpleados.TABLA).getMarcaAgua());
    }

    @Test
    public void delta_noPisaUnaFilaLocalPendienteConMarcaMasNueva() {
        EmpleadoEntity local = unEmpleado(7, EstadoSync.PENDIENTE);
        local.setNombres("NombreLocal");
        local.setActualizadoEn("2026-08-01 15:00:00+00");
        empleados.guardar(local);

        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista(
                "{\"id_empleado\":7,\"nombres\":\"NombreDelServidor\",\"apellidos\":\"Z\","
                        + "\"identidad\":\"0801\",\"correo\":\"m@r.hn\",\"id_usuario\":3,"
                        + "\"apodo_usuario\":\"mzelaya\",\"id_auth_user\":\"uuid-7\","
                        + "\"rol\":\"mesero\",\"activo\":true,"
                        + "\"actualizado_en\":\"2026-08-01 12:00:00+00\"}")));

        ResultadoSync resultado = sincronizador().sincronizar();

        // El cambio local todavía no subió y es más nuevo: pisarlo sería perderlo.
        assertTrue(resultado.esOk());
        assertEquals("NombreLocal", empleados.porId(7).getNombres());
    }

    @Test
    public void delta_pisaUnaFilaYaSincronizadaSinAvisarDePerdida() {
        empleados.guardar(unEmpleado(7, EstadoSync.SINCRONIZADO));
        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista(
                "{\"id_empleado\":7,\"nombres\":\"NombreDelServidor\",\"apellidos\":\"Z\","
                        + "\"identidad\":\"0801\",\"correo\":\"m@r.hn\",\"id_usuario\":3,"
                        + "\"apodo_usuario\":\"mzelaya\",\"id_auth_user\":\"uuid-7\","
                        + "\"rol\":\"cocina\",\"activo\":false,"
                        + "\"actualizado_en\":\"2026-08-01 12:00:00+00\"}")));

        ResultadoSync resultado = sincronizador().sincronizar();

        // Es la misma versión: pisarla es inofensivo y no hay nada que avisarle al usuario.
        assertTrue(resultado.esOk());
        assertEquals("NombreDelServidor", empleados.porId(7).getNombres());
        assertEquals("cocina", empleados.porId(7).getRol());
        assertFalse(empleados.porId(7).isActivo());
    }

    @Test
    public void delta_siPisaUnCambioLocalNoSubido_avisaQueSePerdio() {
        // Fila con un cambio local que quedó sin subir —su operación se descartó en una
        // pasada anterior por un error permanente— y una marca más vieja que la del
        // servidor. No hay nada en la cola: si lo hubiera, el drenado lo subiría antes del
        // delta y no habría conflicto (ese es el camino feliz, cubierto más arriba).
        EmpleadoEntity local = unEmpleado(7, EstadoSync.ERROR);
        local.setNombres("NombreLocal");
        local.setActualizadoEn("2026-08-01 09:00:00+00");
        empleados.guardar(local);
        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista(
                "{\"id_empleado\":7,\"nombres\":\"NombreDelServidor\",\"apellidos\":\"Z\","
                        + "\"identidad\":\"0801\",\"correo\":\"m@r.hn\",\"id_usuario\":3,"
                        + "\"apodo_usuario\":\"mzelaya\",\"id_auth_user\":\"uuid-7\","
                        + "\"rol\":\"mesero\",\"activo\":true,"
                        + "\"actualizado_en\":\"2026-08-01 18:00:00+00\"}")));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertEquals("NombreDelServidor", empleados.porId(7).getNombres());
        // No se lo puede tragar en silencio: el usuario escribió algo que ya no está.
        assertTrue(resultado.esPermanente());
        assertEquals(SincronizadorEmpleados.CAMBIO_LOCAL_PERDIDO, resultado.getMensaje());
    }

    @Test
    public void drenadoExitoso_dejaLaFilaSincronizadaYElDeltaYaNoLaVeComoConflicto() {
        // El camino feliz del caso de arriba: el cambio local sí se sube, así que cuando el
        // delta baja la versión del servidor no hay nada que perder.
        EmpleadoEntity local = unEmpleado(7, EstadoSync.PENDIENTE);
        local.setNombres("NombreLocal");
        local.setActualizadoEn("2026-08-01 09:00:00+00");
        empleados.guardar(local);
        outbox.encolar(TipoOperacion.ACTUALIZAR_EMPLEADO, 7, null, null);
        api.respuestaListarDesde = FakeCall.deRespuesta(Response.success(unaLista(
                "{\"id_empleado\":7,\"nombres\":\"NombreLocal\",\"apellidos\":\"Z\","
                        + "\"identidad\":\"0801\",\"correo\":\"m@r.hn\",\"id_usuario\":3,"
                        + "\"apodo_usuario\":\"mzelaya\",\"id_auth_user\":\"uuid-7\","
                        + "\"rol\":\"mesero\",\"activo\":true,"
                        + "\"actualizado_en\":\"2026-08-01 18:00:00+00\"}")));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(0, outbox.contar());
        assertEquals(EstadoSync.SINCRONIZADO.name(), empleados.porId(7).getEstadoSync());
    }

    @Test
    public void delta_sinConexion_esTransitorioYNoGuardaMarca() {
        api.respuestaListarDesde = FakeCall.deFallo(new IOException("timeout"));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esTransitorio());
        assertNull(marcas.porTabla(SincronizadorEmpleados.TABLA));
    }

    // ------------------------------------------------------------------ P-029: paginación del delta

    @Test
    public void delta_cincuentaFilasConLaMismaMarca_noPierdeLasQueSiguen() {
        // El escenario de "instalación desde cero": todas las filas de la primera página
        // comparten actualizado_en. Con la paginación vieja (avanzar la marca por página),
        // la fila 51 no se bajaba nunca — ver el Javadoc de la clase y P-029.
        String mismaMarca = "2026-08-01T10:00:00+00:00";
        List<EmpleadoDto> primeraPagina = new ArrayList<>();
        for (int i = 0; i < EmpleadoRemoto.LIMITE_DELTA; i++) {
            primeraPagina.add(empleadoServidor(1000 + i, mismaMarca));
        }
        api.respuestaPorPagina = (filtro, offset) -> offset == 0
                ? FakeCall.deRespuesta(Response.success(primeraPagina))
                : FakeCall.deRespuesta(Response.success(
                        List.of(empleadoServidor(2000, mismaMarca))));

        assertTrue(sincronizador().sincronizar().esOk());

        assertEquals(EmpleadoRemoto.LIMITE_DELTA + 1, empleados.filas.size());
    }

    @Test
    public void delta_paginaCompleta_pideLaSiguienteConOffsetYLaMismaMarcaInicial() {
        List<EmpleadoDto> primeraPagina = new ArrayList<>();
        for (int i = 0; i < EmpleadoRemoto.LIMITE_DELTA; i++) {
            primeraPagina.add(empleadoServidor(1000 + i,
                    String.format("2026-08-01T%02d:00:00+00:00", i)));
        }
        api.respuestaPorPagina = (filtro, offset) -> offset == 0
                ? FakeCall.deRespuesta(Response.success(primeraPagina))
                : FakeCall.deRespuesta(Response.success(
                        // "50" > "49" lexicográficamente: la última página trae la marca
                        // más alta de toda la pasada (ver el mismo comentario en
                        // SincronizadorMesasTest).
                        List.of(empleadoServidor(2000, "2026-08-01T50:00:00+00:00"))));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esOk());
        assertEquals(EmpleadoRemoto.LIMITE_DELTA, api.ultimoOffsetPedido);
        assertEquals("2026-08-01T50:00:00+00:00",
                marcas.porTabla(SincronizadorEmpleados.TABLA).getMarcaAgua());
    }

    @Test
    public void delta_unaPaginaFalla_laMarcaNoAvanza() {
        List<EmpleadoDto> primeraPagina = new ArrayList<>();
        for (int i = 0; i < EmpleadoRemoto.LIMITE_DELTA; i++) {
            primeraPagina.add(empleadoServidor(1000 + i,
                    String.format("2026-08-01T%02d:00:00+00:00", i)));
        }
        api.respuestaPorPagina = (filtro, offset) -> offset == 0
                ? FakeCall.deRespuesta(Response.success(primeraPagina))
                : FakeCall.deFallo(new IOException("timeout"));

        ResultadoSync resultado = sincronizador().sincronizar();

        assertTrue(resultado.esTransitorio());
        // La marca de la primera página nunca se guardó: el reintento vuelve a pedir desde
        // el principio, no desde una marca a medio camino.
        assertNull(marcas.porTabla(SincronizadorEmpleados.TABLA));
    }

    @Test(timeout = 10_000)
    public void delta_servidorQueSiempreDevuelvePaginasLlenas_cortaPorElTopeDePaginas() {
        int[] pedidos = {0};
        api.respuestaPorPagina = (filtro, offset) -> {
            pedidos[0]++;
            List<EmpleadoDto> pagina = new ArrayList<>();
            for (int i = 0; i < EmpleadoRemoto.LIMITE_DELTA; i++) {
                pagina.add(empleadoServidor(offset + i, null));
            }
            return FakeCall.deRespuesta(Response.success(pagina));
        };

        sincronizador().sincronizar();

        assertEquals(SincronizadorEmpleados.MAX_PAGINAS, pedidos[0]);
    }

    @Test
    public void delta_aplicaCadaPaginaEnUnaSolaTransaccion() {
        List<EmpleadoDto> pagina = new ArrayList<>();
        for (int i = 0; i < EmpleadoRemoto.LIMITE_DELTA; i++) {
            pagina.add(empleadoServidor(1000 + i,
                    String.format("2026-08-01T%02d:00:00+00:00", i)));
        }
        api.respuestaPorPagina = (filtro, offset) -> offset == 0
                ? FakeCall.deRespuesta(Response.success(pagina))
                : FakeCall.deRespuesta(Response.success(List.of()));

        assertTrue(sincronizador().sincronizar().esOk());

        // Una por página: la llena (1) + la vacía que cierra la paginación (1).
        assertEquals(2, transacciones.veces);
        assertEquals(EmpleadoRemoto.LIMITE_DELTA, empleados.filas.size());
    }

    @Test
    public void modulo_esElDeEmpleados() {
        assertEquals(TipoOperacion.Modulo.EMPLEADOS, sincronizador().modulo());
    }

    // ------------------------------------------------------------------ helpers

    private List<EmpleadoDto> unaLista(String json) {
        List<EmpleadoDto> lista = new ArrayList<>();
        lista.add(gson.fromJson(json, EmpleadoDto.class));
        return lista;
    }

    /** Fila mínima para los tests de paginación (P-029): solo lo que el delta necesita. */
    private EmpleadoDto empleadoServidor(int idEmpleado, String marca) {
        String json = "{\"id_empleado\":" + idEmpleado + ",\"nombres\":\"N" + idEmpleado + "\","
                + "\"apellidos\":\"A\",\"identidad\":\"0801\",\"correo\":\"e" + idEmpleado
                + "@r.hn\",\"id_usuario\":1,\"apodo_usuario\":\"u" + idEmpleado
                + "\",\"id_auth_user\":\"uuid-" + idEmpleado + "\",\"rol\":\"mesero\","
                + "\"activo\":true"
                + (marca == null ? "" : ",\"actualizado_en\":\"" + marca + "\"")
                + "}";
        return gson.fromJson(json, EmpleadoDto.class);
    }

    private static EmpleadoEntity unEmpleado(int id, EstadoSync estado) {
        EmpleadoEntity fila = new EmpleadoEntity();
        fila.setIdEmpleado(id);
        fila.setNombres("Marta");
        fila.setApellidos("Zelaya");
        fila.setIdentidad("0801");
        fila.setTelefono("9999-0000");
        fila.setCorreo("marta@restaurante.hn");
        fila.setIdUsuario(3);
        fila.setApodoUsuario("mzelaya");
        fila.setIdAuthUser("uuid-" + id);
        fila.setRol("mesero");
        fila.setActivo(true);
        fila.setEstadoSync(estado.name());
        return fila;
    }

    // ------------------------------------------------------------------ fakes

    private static final class FakeEmpleadoDao implements EmpleadoDao {

        private final Map<Integer, EmpleadoEntity> filas = new HashMap<>();

        void guardar(EmpleadoEntity fila) {
            filas.put(fila.getIdEmpleado(), fila);
        }

        @Override
        public LiveData<List<EmpleadoEntity>> observarTodos() {
            throw new UnsupportedOperationException("el sincronizador no observa");
        }

        @Override
        public EmpleadoEntity porId(int idEmpleado) {
            return filas.get(idEmpleado);
        }

        @Override
        public long insertar(EmpleadoEntity empleado) {
            guardar(empleado);
            return empleado.getIdEmpleado();
        }

        @Override
        public void actualizar(EmpleadoEntity empleado) {
            guardar(empleado);
        }

        @Override
        public void borrar(EmpleadoEntity empleado) {
            filas.remove(empleado.getIdEmpleado());
        }

        @Override
        public int contarNoSincronizados() {
            int cuenta = 0;
            for (EmpleadoEntity fila : filas.values()) {
                if (!EstadoSync.SINCRONIZADO.name().equals(fila.getEstadoSync())) {
                    cuenta++;
                }
            }
            return cuenta;
        }
    }

    private static final class FakeSincronizacionDao implements SincronizacionDao {

        private final Map<String, SincronizacionEntity> marcas = new HashMap<>();

        @Override
        public SincronizacionEntity porTabla(String tabla) {
            return marcas.get(tabla);
        }

        @Override
        public void guardar(SincronizacionEntity marca) {
            marcas.put(marca.getTabla(), marca);
        }
    }

    private static final class FakeSupabaseEmpleadoApi implements SupabaseEmpleadoApi {

        Call<List<EmpleadoDto>> respuestaListarDesde =
                FakeCall.deRespuesta(Response.success(Collections.emptyList()));
        Call<CrearEmpleadoResponseDto> respuestaCrear;
        Call<Void> respuestaActualizarEmpleado = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaActualizarPerfil = FakeCall.deRespuesta(Response.success(null));

        /**
         * BiFunction y no un {@code Call} fijo: para probar la paginación por offset hace
         * falta poder responder distinto según la página pedida (mismo patrón que
         * {@code SincronizadorMenuTest.FakeMenuApi}). Por defecto delega en
         * {@code respuestaListarDesde} para no romper los tests que no pagina.
         */
        java.util.function.BiFunction<String, Integer, Call<List<EmpleadoDto>>> respuestaPorPagina =
                (filtro, offset) -> respuestaListarDesde;

        int ultimoOffsetPedido;

        @Override
        public Call<List<EmpleadoDto>> listar(String bearerToken) {
            return respuestaListarDesde;
        }

        @Override
        public Call<List<EmpleadoDto>> listarDesde(String bearerToken, String select,
                                                   String actualizadoEnMayorQue, String orden,
                                                   int limite, int desplazamiento) {
            ultimoOffsetPedido = desplazamiento;
            return respuestaPorPagina.apply(actualizadoEnMayorQue, desplazamiento);
        }

        @Override
        public Call<CrearEmpleadoResponseDto> crear(String bearerToken,
                                                    CrearEmpleadoRequestDto cuerpo) {
            return respuestaCrear;
        }

        @Override
        public Call<Void> actualizarEmpleado(String bearerToken, String idEmpleadoIgualA,
                                             ActualizarEmpleadoDto cuerpo) {
            return respuestaActualizarEmpleado;
        }

        @Override
        public Call<Void> actualizarPerfil(String bearerToken, String idIgualA,
                                           ActualizarPerfilDto cuerpo) {
            return respuestaActualizarPerfil;
        }
    }
}

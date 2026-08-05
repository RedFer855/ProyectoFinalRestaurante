package com.example.proyectofinalrestaurante.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.remote.SupabaseAuthApi;
import com.example.proyectofinalrestaurante.data.remote.dto.CambiarContraseniaRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginResponseDto;
import com.example.proyectofinalrestaurante.data.remote.dto.RecuperarRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.RefrescarRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.VerificarCodigoRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.VerificarCodigoResponseDto;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.example.proyectofinalrestaurante.domain.repository.SesionRepository;
import com.google.gson.Gson;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Tests de {@link ProveedorDeToken} (P-009, [[Plan Fase 0b - Cierre de la deuda P0]] §4.4).
 * JUnit puro con fakes — no necesita Robolectric: no toca Android, solo
 * {@link SupabaseAuthApi} y {@link SesionRepository} (ambos inyectados). Cubre D5-D10 del
 * plan, los casos de esta pieza (D1-D4 son de {@code AlmacenSeguro}, ver su Javadoc sobre el
 * límite de Robolectric con el Keystore).
 */
public class ProveedorDeTokenTest {

    private final Gson gson = new Gson();

    @After
    public void limpiar() {
        SesionActual.limpiar();
    }

    private Sesion sesion(String accessToken, String refreshToken, long expiraEnMillis) {
        return new Sesion("u1", "a@b.com", accessToken, refreshToken, expiraEnMillis, "Ana", "mesero");
    }

    private LoginResponseDto refrescoDto(String accessToken, String refreshToken, int expiresIn) {
        String json = "{\"access_token\":\"" + accessToken + "\",\"refresh_token\":\""
                + refreshToken + "\",\"expires_in\":" + expiresIn + "}";
        return gson.fromJson(json, LoginResponseDto.class);
    }

    // ------------------------------------------------------------------ sin sesión

    @Test
    public void get_sinSesion_devuelveNullSinLlamarARed() {
        SesionActual.limpiar();
        FakeSupabaseAuthApi api = new FakeSupabaseAuthApi();
        ProveedorDeToken proveedor = new ProveedorDeToken(api, new FakeSesionRepository());

        assertNull(proveedor.get());
        assertEquals(0, api.vecesRefrescoLlamado.get());
    }

    // D5
    @Test
    public void get_conTokenVigente_loDevuelveSinLlamarARed() {
        SesionActual.guardar(sesion("access-vivo", "refresh-1",
                System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)));
        FakeSupabaseAuthApi api = new FakeSupabaseAuthApi();
        ProveedorDeToken proveedor = new ProveedorDeToken(api, new FakeSesionRepository());

        assertEquals("access-vivo", proveedor.get());
        assertEquals(0, api.vecesRefrescoLlamado.get());
    }

    @Test
    public void get_conTokenDentroDelMargenDeSeguridad_refresca() {
        // A 30s de vencer, dentro del margen de 60s: hay que refrescar antes de que venza,
        // no esperar a que ya haya vencido.
        SesionActual.guardar(sesion("access-por-vencer", "refresh-1",
                System.currentTimeMillis() + 30_000L));
        FakeSupabaseAuthApi api = new FakeSupabaseAuthApi();
        api.respuestaRefrescar = FakeCall.deRespuesta(
                Response.success(refrescoDto("access-nuevo", "refresh-2", 3600)));
        ProveedorDeToken proveedor = new ProveedorDeToken(api, new FakeSesionRepository());

        assertEquals("access-nuevo", proveedor.get());
        assertEquals(1, api.vecesRefrescoLlamado.get());
    }

    // D6
    @Test
    public void get_conTokenVencido_refrescaYDevuelveElNuevo() {
        SesionActual.guardar(sesion("access-viejo", "refresh-1",
                System.currentTimeMillis() - 1_000L));
        FakeSupabaseAuthApi api = new FakeSupabaseAuthApi();
        api.respuestaRefrescar = FakeCall.deRespuesta(
                Response.success(refrescoDto("access-nuevo", "refresh-2", 3600)));
        FakeSesionRepository repo = new FakeSesionRepository();
        ProveedorDeToken proveedor = new ProveedorDeToken(api, repo);

        String token = proveedor.get();

        assertEquals("access-nuevo", token);
        assertEquals("access-nuevo", SesionActual.obtener().getAccessToken());
        assertTrue(SesionActual.obtener().getExpiraEnMillis() > System.currentTimeMillis());
    }

    // D8
    @Test
    public void refresh_exitoso_persisteElRefreshTokenNuevo() {
        SesionActual.guardar(sesion("access-viejo", "refresh-viejo",
                System.currentTimeMillis() - 1_000L));
        FakeSupabaseAuthApi api = new FakeSupabaseAuthApi();
        api.respuestaRefrescar = FakeCall.deRespuesta(
                Response.success(refrescoDto("access-nuevo", "refresh-rotado", 3600)));
        FakeSesionRepository repo = new FakeSesionRepository();
        ProveedorDeToken proveedor = new ProveedorDeToken(api, repo);

        proveedor.get();

        assertNotNull(repo.guardada);
        assertEquals("refresh-rotado", repo.guardada.getRefreshToken());
        assertEquals("access-nuevo", repo.guardada.getAccessToken());
    }

    // D9
    @Test
    public void refresh_falla401_devuelveNullYCierraLaSesion() {
        SesionActual.guardar(sesion("access-viejo", "refresh-muerto",
                System.currentTimeMillis() - 1_000L));
        FakeSupabaseAuthApi api = new FakeSupabaseAuthApi();
        api.respuestaRefrescar = FakeCall.deRespuesta(Response.error(401,
                ResponseBody.create(MediaType.get("application/json"), "{\"error\":\"invalid_grant\"}")));
        FakeSesionRepository repo = new FakeSesionRepository();
        ProveedorDeToken proveedor = new ProveedorDeToken(api, repo);

        String token = proveedor.get();

        assertNull(token);
        assertNull("el refresh token ya no sirve: la sesión se cierra", SesionActual.obtener());
        assertTrue(repo.borradoLlamado);
    }

    @Test
    public void refresh_falla400_devuelveNullYCierraLaSesion() {
        SesionActual.guardar(sesion("access-viejo", "refresh-invalido",
                System.currentTimeMillis() - 1_000L));
        FakeSupabaseAuthApi api = new FakeSupabaseAuthApi();
        api.respuestaRefrescar = FakeCall.deRespuesta(Response.error(400,
                ResponseBody.create(MediaType.get("application/json"), "{\"error\":\"invalid_request\"}")));
        FakeSesionRepository repo = new FakeSesionRepository();
        ProveedorDeToken proveedor = new ProveedorDeToken(api, repo);

        assertNull(proveedor.get());
        assertNull(SesionActual.obtener());
        assertTrue(repo.borradoLlamado);
    }

    // D10
    @Test
    public void refresh_sinRed_devuelveNullYNoTocaLaSesion() {
        Sesion original = sesion("access-viejo", "refresh-1", System.currentTimeMillis() - 1_000L);
        SesionActual.guardar(original);
        FakeSupabaseAuthApi api = new FakeSupabaseAuthApi();
        api.respuestaRefrescar = FakeCall.deFallo(new IOException("timeout"));
        FakeSesionRepository repo = new FakeSesionRepository();
        ProveedorDeToken proveedor = new ProveedorDeToken(api, repo);

        String token = proveedor.get();

        assertNull(token);
        // Transitorio: la sesión NO se borra, el próximo get() lo vuelve a intentar.
        assertEquals(original, SesionActual.obtener());
        assertFalse(repo.borradoLlamado);
    }

    @Test
    public void refresh_5xx_esTransitorioYNoTocaLaSesion() {
        Sesion original = sesion("access-viejo", "refresh-1", System.currentTimeMillis() - 1_000L);
        SesionActual.guardar(original);
        FakeSupabaseAuthApi api = new FakeSupabaseAuthApi();
        api.respuestaRefrescar = FakeCall.deRespuesta(Response.error(500,
                ResponseBody.create(MediaType.get("application/json"), "{}")));
        FakeSesionRepository repo = new FakeSesionRepository();
        ProveedorDeToken proveedor = new ProveedorDeToken(api, repo);

        assertNull(proveedor.get());
        assertEquals(original, SesionActual.obtener());
        assertFalse(repo.borradoLlamado);
    }

    @Test
    public void refresh_sinRefreshToken_cierraLaSesionSinLlamarARed() {
        SesionActual.guardar(sesion("access-viejo", null, System.currentTimeMillis() - 1_000L));
        FakeSupabaseAuthApi api = new FakeSupabaseAuthApi();
        FakeSesionRepository repo = new FakeSesionRepository();
        ProveedorDeToken proveedor = new ProveedorDeToken(api, repo);

        assertNull(proveedor.get());
        assertEquals(0, api.vecesRefrescoLlamado.get());
        assertTrue(repo.borradoLlamado);
    }

    // D7: single-flight
    @Test(timeout = 10_000)
    public void get_conSeisHilosYTokenVencido_soloLlamaARefrescarUnaVez() throws InterruptedException {
        SesionActual.guardar(sesion("access-viejo", "refresh-1",
                System.currentTimeMillis() - 1_000L));
        FakeSupabaseAuthApi api = new FakeSupabaseAuthApi();
        // Un pequeño retardo en el fake da tiempo a que los otros hilos lleguen a pedir el
        // lock mientras el primero todavía está "en red": si el single-flight no funcionara,
        // esta ventana es donde se colarían refrescos de más.
        api.demoraRefrescoMillis = 50;
        api.respuestaRefrescar = FakeCall.deRespuesta(
                Response.success(refrescoDto("access-nuevo", "refresh-2", 3600)));
        ProveedorDeToken proveedor = new ProveedorDeToken(api, new FakeSesionRepository());

        int hilos = 6;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch listos = new CountDownLatch(hilos);
        String[] resultados = new String[hilos];
        try {
            for (int i = 0; i < hilos; i++) {
                int idx = i;
                pool.submit(() -> {
                    listos.countDown();
                    try {
                        salida.await();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    resultados[idx] = proveedor.get();
                });
            }
            listos.await();
            salida.countDown();
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }

        for (String resultado : resultados) {
            assertEquals("access-nuevo", resultado);
        }
        assertEquals("una sola llamada de refresh pese a 6 hilos concurrentes",
                1, api.vecesRefrescoLlamado.get());
    }

    // ------------------------------------------------------------------ fakes

    private static final class FakeSesionRepository implements SesionRepository {

        @Nullable
        Sesion guardada;
        boolean borradoLlamado;

        @Override
        public void guardar(Sesion sesion) {
            guardada = sesion;
        }

        @Nullable
        @Override
        public Sesion leer() {
            return guardada;
        }

        @Override
        public void borrar() {
            borradoLlamado = true;
            guardada = null;
        }
    }

    private static final class FakeSupabaseAuthApi implements SupabaseAuthApi {

        Call<LoginResponseDto> respuestaRefrescar;
        final AtomicInteger vecesRefrescoLlamado = new AtomicInteger();
        volatile long demoraRefrescoMillis;

        @Override
        public Call<LoginResponseDto> login(LoginRequestDto body) {
            throw new UnsupportedOperationException("No usado en este test");
        }

        @Override
        public Call<Void> logout(String bearerToken) {
            throw new UnsupportedOperationException("No usado en este test");
        }

        @Override
        public Call<LoginResponseDto> refrescar(RefrescarRequestDto body) {
            vecesRefrescoLlamado.incrementAndGet();
            if (demoraRefrescoMillis > 0) {
                try {
                    Thread.sleep(demoraRefrescoMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return respuestaRefrescar;
        }

        @Override
        public Call<Void> solicitarCodigo(RecuperarRequestDto body) {
            throw new UnsupportedOperationException("No usado en este test");
        }

        @Override
        public Call<VerificarCodigoResponseDto> verificarCodigo(VerificarCodigoRequestDto body) {
            throw new UnsupportedOperationException("No usado en este test");
        }

        @Override
        public Call<Void> cambiarContrasenia(String bearerToken, CambiarContraseniaRequestDto body) {
            throw new UnsupportedOperationException("No usado en este test");
        }
    }
}

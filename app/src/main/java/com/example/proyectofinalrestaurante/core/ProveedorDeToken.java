package com.example.proyectofinalrestaurante.core;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.remote.SupabaseAuthApi;
import com.example.proyectofinalrestaurante.data.remote.dto.LoginResponseDto;
import com.example.proyectofinalrestaurante.data.remote.dto.RefrescarRequestDto;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.example.proyectofinalrestaurante.domain.repository.SesionRepository;

import java.io.IOException;
import java.util.function.Supplier;

import retrofit2.Response;

/**
 * Token supplier con refresh <b>proactivo</b> y <b>single-flight</b> (P-009,
 * [[Plan Fase 0b - Cierre de la deuda P0]] §4.4).
 *
 * <p>Se inyecta donde hoy va {@code SyncApplication::tokenDeLaSesion}: no cambia ni una línea
 * de {@code MesaRemoto}/{@code ClienteRemoto}/{@code MenuRemoto}/{@code EmpleadoRemoto}, que
 * ya reciben el token por un {@code Supplier<String>} inyectado. Es la misma costura, no una
 * nueva.</p>
 *
 * <p><b>Proactivo, no reactivo</b> (no es un {@code Authenticator} de OkHttp), por tres
 * razones documentadas en el plan: (1) el WebSocket de la Fase 3 necesita un token válido
 * <em>antes</em> de conectar — un {@code Authenticator} reacciona a un 401 que ahí nunca
 * llega; (2) hoy hay siete {@code OkHttpClient} (P-028) y habría que poner el
 * {@code Authenticator} en los siete; (3) el {@code Supplier} ya está inyectado en todos
 * lados.</p>
 *
 * <p><b>Single-flight bajo lock</b>: varios sincronizadores corren en fila dentro del mismo
 * {@code SyncWorker} y pueden pedir token a la vez con uno vencido. Supabase <b>rota</b> el
 * refresh token — cada refresh invalida el anterior — así que sin lock, la primera carrera
 * gana y las demás usarían un refresh token ya muerto: la sesión se caería sola. El
 * re-chequeo de vencimiento adentro del {@code synchronized} es el mismo doble chequeo que
 * {@code SyncApplication.baseDeDatos()} usa para su init perezoso: el que entra segundo
 * encuentra el token ya renovado y no pide nada.</p>
 */
public final class ProveedorDeToken implements Supplier<String> {

    /** Margen de seguridad: refrescar un poco antes de vencer, no justo al límite. */
    static final long MARGEN_MILLIS = 60_000L;

    private final SupabaseAuthApi authApi;
    private final SesionRepository sesionRepository;
    private final Object lock = new Object();

    public ProveedorDeToken(SupabaseAuthApi authApi, SesionRepository sesionRepository) {
        this.authApi = authApi;
        this.sesionRepository = sesionRepository;
    }

    @Nullable
    @Override
    public String get() {
        Sesion sesion = SesionActual.obtener();
        if (sesion == null) {
            return null;
        }
        if (vigente(sesion)) {
            return sesion.getAccessToken();
        }
        return refrescar();
    }

    private boolean vigente(Sesion sesion) {
        return sesion.getExpiraEnMillis() - System.currentTimeMillis() > MARGEN_MILLIS;
    }

    @Nullable
    private String refrescar() {
        synchronized (lock) {
            // Re-chequeo: el hilo que entra segundo puede encontrar el token ya renovado
            // por el que entró primero, y no tiene que pedir nada.
            Sesion sesionActual = SesionActual.obtener();
            if (sesionActual == null) {
                return null;
            }
            if (vigente(sesionActual)) {
                return sesionActual.getAccessToken();
            }
            if (sesionActual.getRefreshToken() == null) {
                cerrarSesion();
                return null;
            }
            try {
                Response<LoginResponseDto> respuesta = authApi.refrescar(
                        new RefrescarRequestDto(sesionActual.getRefreshToken())).execute();
                if (!respuesta.isSuccessful() || respuesta.body() == null
                        || respuesta.body().getAccessToken() == null) {
                    if (respuesta.code() == 401 || respuesta.code() == 400) {
                        // El refresh token ya no sirve (vencido, revocado, o ya se usó y
                        // rotó): no hay forma de recuperar la sesión sin volver a loguearse.
                        cerrarSesion();
                    }
                    // Otros códigos (5xx) son transitorios: la sesión persistida NO se toca,
                    // el próximo get() lo vuelve a intentar (D10).
                    return null;
                }
                LoginResponseDto body = respuesta.body();
                long nuevoExpiraEnMillis = System.currentTimeMillis()
                        + (body.getExpiresIn() != null ? body.getExpiresIn() : 3600) * 1000L;
                Sesion renovada = sesionActual.conTokenRenovado(
                        body.getAccessToken(), body.getRefreshToken(), nuevoExpiraEnMillis);
                SesionActual.guardar(renovada);
                sesionRepository.guardar(renovada);
                return renovada.getAccessToken();
            } catch (IOException | SecurityException ex) {
                // Sin red o sin permiso: transitorio, no se toca la sesión guardada (D10).
                return null;
            }
        }
    }

    private void cerrarSesion() {
        SesionActual.limpiar();
        sesionRepository.borrar();
    }
}

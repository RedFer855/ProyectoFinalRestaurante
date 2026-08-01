package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.remote.SupabaseEmpleadoApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarEmpleadoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarPerfilDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoResponseDto;
import com.example.proyectofinalrestaurante.data.remote.dto.EmpleadoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ErrorApiDto;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Empleado;
import com.example.proyectofinalrestaurante.domain.model.NuevoEmpleado;
import com.example.proyectofinalrestaurante.domain.repository.EmpleadoRepository;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import retrofit2.Response;

/**
 * Implementación de {@link EmpleadoRepository} contra Supabase (Data Layer).
 *
 * <p>El token de la sesión activa se obtiene por un {@link Supplier} inyectado en vez
 * de leer {@code SesionActual} directo: así el repositorio se puede testear sin estado
 * global, y la capa {@code data} no depende de dónde vive la sesión.</p>
 */
public class SupabaseEmpleadoRepository implements EmpleadoRepository {

    private static final String SIN_CONEXION = "Sin conexión al servidor. Intentá de nuevo.";
    private static final String SIN_PERMISO_RED = "La app no tiene permiso de red. Contactá al desarrollador.";
    private static final String SIN_SESION = "Tu sesión venció. Volvé a iniciar sesión.";

    private final SupabaseEmpleadoApi api;
    private final Supplier<String> proveedorToken;
    private final Gson gson = new Gson();

    public SupabaseEmpleadoRepository(SupabaseEmpleadoApi api, Supplier<String> proveedorToken) {
        this.api = api;
        this.proveedorToken = proveedorToken;
    }

    @Nullable
    private String bearer() {
        String token = proveedorToken.get();
        return token == null ? null : "Bearer " + token;
    }

    @Override
    public Result<List<Empleado>> listar() {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }
        try {
            Response<List<EmpleadoDto>> respuesta = api.listar(bearer).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return Result.fail("No se pudo cargar la lista de empleados.");
            }
            List<Empleado> empleados = new ArrayList<>();
            for (EmpleadoDto dto : respuesta.body()) {
                empleados.add(aDominio(dto));
            }
            return Result.ok(empleados);
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    @Override
    public Result<Empleado> crear(NuevoEmpleado nuevo) {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }
        try {
            CrearEmpleadoRequestDto cuerpo = new CrearEmpleadoRequestDto(
                    nuevo.getNombres(), nuevo.getApellidos(), nuevo.getIdentidad(),
                    nuevo.getTelefono(), nuevo.getCorreo(), nuevo.getRol(),
                    nuevo.getContraseniaTemporal());

            Response<CrearEmpleadoResponseDto> respuesta = api.crear(bearer, cuerpo).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                // Los mensajes de la Edge Function los escribimos nosotros y están en
                // lenguaje humano, así que sí se le muestran al usuario.
                return Result.fail(mensajeDeError(respuesta, "No se pudo crear el empleado."));
            }

            CrearEmpleadoResponseDto creado = respuesta.body();
            return Result.ok(new Empleado(
                    creado.getIdEmpleado(), nuevo.getNombres(), nuevo.getApellidos(),
                    nuevo.getIdentidad(), nuevo.getTelefono(), nuevo.getCorreo(),
                    0, creado.getApodoUsuario(), creado.getIdAuthUser(),
                    nuevo.getRol(), true));
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    @Override
    public Result<Void> actualizarDatos(Empleado empleado) {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }
        try {
            ActualizarEmpleadoDto cuerpo = new ActualizarEmpleadoDto(
                    empleado.getNombres(), empleado.getApellidos(), empleado.getIdentidad(),
                    empleado.getTelefono(), empleado.getCorreo());

            Response<Void> respuesta = api.actualizarEmpleado(
                    bearer, "eq." + empleado.getIdEmpleado(), cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                return Result.fail("No se pudieron guardar los cambios.");
            }

            // El nombre también vive en `perfiles` (es lo que muestra el menú lateral
            // de esa persona), así que se mantiene en sincronía.
            api.actualizarPerfil(bearer, "eq." + empleado.getIdAuthUser(),
                    ActualizarPerfilDto.soloNombre(empleado.nombreCompleto())).execute();

            return Result.ok(null);
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    @Override
    public Result<Void> cambiarRol(String idAuthUser, String nuevoRol) {
        return actualizarPerfil(idAuthUser, ActualizarPerfilDto.soloRol(nuevoRol),
                "No se pudo cambiar el rol.");
    }

    @Override
    public Result<Void> cambiarEstado(String idAuthUser, boolean activo) {
        return actualizarPerfil(idAuthUser, ActualizarPerfilDto.soloEstado(activo),
                "No se pudo cambiar el estado.");
    }

    private Result<Void> actualizarPerfil(String idAuthUser, ActualizarPerfilDto cuerpo,
                                          String mensajeSiFalla) {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }
        try {
            Response<Void> respuesta =
                    api.actualizarPerfil(bearer, "eq." + idAuthUser, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                // El trigger proteger_admins() responde 400 con su propio mensaje
                // ("No se puede modificar a otro administrador"). Es información útil
                // para el usuario, no un detalle interno.
                return Result.fail(mensajeDeError(respuesta, mensajeSiFalla));
            }
            return Result.ok(null);
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    /** Extrae el mensaje del cuerpo de error, o devuelve el genérico si no se puede. */
    private String mensajeDeError(Response<?> respuesta, String porDefecto) {
        if (respuesta.errorBody() == null) {
            return porDefecto;
        }
        try {
            String crudo = respuesta.errorBody().string();
            ErrorApiDto error = gson.fromJson(crudo, ErrorApiDto.class);
            if (error != null && error.getError() != null && !error.getError().isEmpty()) {
                return error.getError();
            }
            // PostgREST devuelve {"message": "..."} — el trigger escribe ahí su texto.
            MensajePostgrest postgrest = gson.fromJson(crudo, MensajePostgrest.class);
            if (postgrest != null && postgrest.message != null && !postgrest.message.isEmpty()) {
                return postgrest.message;
            }
        } catch (IOException | RuntimeException ignorada) {
            // Cuerpo ilegible o que no es JSON: se cae al mensaje genérico.
        }
        return porDefecto;
    }

    /** Forma del error de PostgREST; el mensaje del trigger llega en `message`. */
    private static final class MensajePostgrest {
        String message;
    }

    private Empleado aDominio(EmpleadoDto dto) {
        return new Empleado(
                dto.getIdEmpleado(), dto.getNombres(), dto.getApellidos(), dto.getIdentidad(),
                dto.getTelefono(), dto.getCorreo(), dto.getIdUsuario(), dto.getApodoUsuario(),
                dto.getIdAuthUser(), dto.getRol(), dto.isActivo());
    }
}

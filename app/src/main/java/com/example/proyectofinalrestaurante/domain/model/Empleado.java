package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Empleado con su acceso al sistema (Plan Fase 1d).
 *
 * <p>Reúne lo que en la base viven en tres tablas: los datos de la persona
 * (`empleados`), su usuario del sistema (`usuarios`) y su perfil de acceso
 * (`perfiles`, que es de donde salen el rol y si está activo). Del lado del servidor
 * esa unión la resuelve la vista `vista_empleados`.</p>
 *
 * <p>{@code estadoSync} lo agregó la migración a offline-first: la UI necesita distinguir
 * un empleado ya subido de uno con cambios locales pendientes o con un error de
 * sincronización. Es {@code domain} puro — un enum, no una dependencia de Room.</p>
 */
public final class Empleado {

    private final int idEmpleado;
    private final String nombres;
    private final String apellidos;
    private final String identidad;
    @Nullable private final String telefono;
    private final String correo;
    private final int idUsuario;
    private final String apodoUsuario;
    /** UUID de `auth.users` — es lo que identifica al empleado como usuario del sistema. */
    private final String idAuthUser;
    private final String rol;
    private final boolean activo;
    private final EstadoSync estadoSync;

    public Empleado(int idEmpleado, String nombres, String apellidos, String identidad,
                    @Nullable String telefono, String correo, int idUsuario,
                    String apodoUsuario, String idAuthUser, String rol, boolean activo,
                    EstadoSync estadoSync) {
        this.idEmpleado = idEmpleado;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.identidad = identidad;
        this.telefono = telefono;
        this.correo = correo;
        this.idUsuario = idUsuario;
        this.apodoUsuario = apodoUsuario;
        this.idAuthUser = idAuthUser;
        this.rol = rol;
        this.activo = activo;
        this.estadoSync = estadoSync;
    }

    public int getIdEmpleado() {
        return idEmpleado;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public String getIdentidad() {
        return identidad;
    }

    @Nullable
    public String getTelefono() {
        return telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public String getApodoUsuario() {
        return apodoUsuario;
    }

    public String getIdAuthUser() {
        return idAuthUser;
    }

    public String getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public EstadoSync getEstadoSync() {
        return estadoSync;
    }

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }
}

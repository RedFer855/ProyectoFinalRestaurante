package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Fila local de un empleado. Vive en {@code data.local} a propósito: {@code domain} no puede
 * importar {@code @Entity} de Room (regla 1 del Protocolo).
 *
 * <p><b>Por qué acá la PK es {@code id_empleado} y no un {@code id_local} propio, a
 * diferencia de {@code PlatilloEntity}:</b> un platillo puede existir localmente antes de que
 * el servidor le asigne un id, así que necesita una identidad local y un
 * {@code id_servidor} nulable. Un empleado no: el alta crea una cuenta en Supabase Auth y
 * <b>exige conexión</b>, así que toda fila de esta tabla ya vino del servidor y su
 * {@code id_empleado} existe siempre. Inventar un {@code id_local} acá sería una indirección
 * que no resuelve ningún problema real.</p>
 *
 * <p>{@code actualizadoEn} es la marca de la vista {@code vista_empleados}, que el servidor
 * calcula como el máximo entre {@code empleados.actualizado_en} y
 * {@code perfiles.actualizado_en} — un empleado "cambia" tanto si se editan sus datos como
 * si se le cambia el rol o el estado, y esos viven en tablas distintas.</p>
 */
@Entity(tableName = "empleados")
public class EmpleadoEntity {

    @PrimaryKey
    @ColumnInfo(name = "id_empleado")
    private int idEmpleado;

    @ColumnInfo(name = "nombres")
    private String nombres;

    @ColumnInfo(name = "apellidos")
    private String apellidos;

    @ColumnInfo(name = "identidad")
    private String identidad;

    @Nullable
    @ColumnInfo(name = "telefono")
    private String telefono;

    @ColumnInfo(name = "correo")
    private String correo;

    @ColumnInfo(name = "id_usuario")
    private int idUsuario;

    @ColumnInfo(name = "apodo_usuario")
    private String apodoUsuario;

    /** UUID de {@code auth.users}: es con lo que se PATCHea `perfiles` (rol y estado). */
    @ColumnInfo(name = "id_auth_user")
    private String idAuthUser;

    @ColumnInfo(name = "rol")
    private String rol;

    @ColumnInfo(name = "activo")
    private boolean activo;

    @ColumnInfo(name = "estado_sync")
    private String estadoSync;

    @Nullable
    @ColumnInfo(name = "actualizado_en")
    private String actualizadoEn;

    public int getIdEmpleado() {
        return idEmpleado;
    }

    public void setIdEmpleado(int idEmpleado) {
        this.idEmpleado = idEmpleado;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getIdentidad() {
        return identidad;
    }

    public void setIdentidad(String identidad) {
        this.identidad = identidad;
    }

    @Nullable
    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(@Nullable String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getApodoUsuario() {
        return apodoUsuario;
    }

    public void setApodoUsuario(String apodoUsuario) {
        this.apodoUsuario = apodoUsuario;
    }

    public String getIdAuthUser() {
        return idAuthUser;
    }

    public void setIdAuthUser(String idAuthUser) {
        this.idAuthUser = idAuthUser;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public String getEstadoSync() {
        return estadoSync;
    }

    public void setEstadoSync(String estadoSync) {
        this.estadoSync = estadoSync;
    }

    @Nullable
    public String getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(@Nullable String actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}

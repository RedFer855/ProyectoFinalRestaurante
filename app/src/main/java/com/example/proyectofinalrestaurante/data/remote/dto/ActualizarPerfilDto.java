package com.example.proyectofinalrestaurante.data.remote.dto;

/**
 * Cuerpo de {@code PATCH rest/v1/perfiles}. Se usa para el rol, para el estado
 * activo/inactivo y para mantener el nombre en sincronía con `empleados`.
 *
 * <p>Los campos nulos no se serializan (Gson los omite por defecto), así que el mismo
 * DTO sirve para un cambio de rol o de estado sin pisar el otro valor.</p>
 *
 * <p><b>`perfiles.rol` es la única vía de escritura del rol.</b> Un trigger propaga el
 * cambio a `usuarios.id_rol`; la app nunca escribe esa columna.</p>
 */
public final class ActualizarPerfilDto {

    private final String rol;
    private final Boolean activo;
    private final String nombre;

    private ActualizarPerfilDto(String rol, Boolean activo, String nombre) {
        this.rol = rol;
        this.activo = activo;
        this.nombre = nombre;
    }

    public static ActualizarPerfilDto soloRol(String rol) {
        return new ActualizarPerfilDto(rol, null, null);
    }

    public static ActualizarPerfilDto soloEstado(boolean activo) {
        return new ActualizarPerfilDto(null, activo, null);
    }

    public static ActualizarPerfilDto soloNombre(String nombre) {
        return new ActualizarPerfilDto(null, null, nombre);
    }
}

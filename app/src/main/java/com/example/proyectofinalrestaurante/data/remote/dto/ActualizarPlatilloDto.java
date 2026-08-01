package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code PATCH rest/v1/platillo}. Actualizaciones parciales.
 *
 * <p>Todos los campos son objetos y no primitivos a propósito: Gson omite los nulos, así
 * que cada factory manda <b>solo</b> lo que cambió y no pisa el resto. Con un
 * {@code double} o un {@code int} crudos, un campo sin tocar viajaría como 0 y borraría el
 * valor bueno. Mismo truco que {@link ActualizarPerfilDto}.</p>
 *
 * <p><b>Lo que este DTO no puede hacer:</b> poner {@code ruta_imagen} en null. Un null acá
 * se omite, que es justo lo contrario de lo que hace falta para quitar una foto. Ese caso
 * usa un cuerpo JSON literal en el repositorio; cambiar la configuración global del
 * converter a {@code serializeNulls()} rompería todas las actualizaciones parciales.</p>
 */
public final class ActualizarPlatilloDto {

    @SerializedName("nombre")
    private final String nombre;

    @SerializedName("descripcion")
    private final String descripcion;

    @SerializedName("precio")
    private final Double precio;

    @SerializedName("id_categoria")
    private final Integer idCategoria;

    @SerializedName("ruta_imagen")
    private final String rutaImagen;

    @SerializedName("id_estado")
    private final Integer idEstado;

    private ActualizarPlatilloDto(String nombre, String descripcion, Double precio,
                                  Integer idCategoria, String rutaImagen, Integer idEstado) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.idCategoria = idCategoria;
        this.rutaImagen = rutaImagen;
        this.idEstado = idEstado;
    }

    /** Activar o desactivar sin tocar ningún otro campo. */
    public static ActualizarPlatilloDto soloEstado(int idEstado) {
        return new ActualizarPlatilloDto(null, null, null, null, null, idEstado);
    }

    /**
     * Datos del platillo, dejando la foto como está.
     *
     * <p>Una descripción vacía se manda como {@code ""} y no como null, porque un null se
     * omitiría y la descripción vieja quedaría en la fila.</p>
     */
    public static ActualizarPlatilloDto soloDatos(String nombre, String descripcion,
                                                  double precio, int idCategoria) {
        return new ActualizarPlatilloDto(nombre, descripcion == null ? "" : descripcion,
                precio, idCategoria, null, null);
    }

    /** Datos del platillo más la ruta de una foto nueva. */
    public static ActualizarPlatilloDto conImagen(String nombre, String descripcion,
                                                  double precio, int idCategoria,
                                                  String rutaImagen) {
        return new ActualizarPlatilloDto(nombre, descripcion == null ? "" : descripcion,
                precio, idCategoria, rutaImagen, null);
    }
}

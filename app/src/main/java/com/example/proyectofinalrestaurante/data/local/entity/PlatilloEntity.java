package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Fila local de un platillo (Plan Fase 2b, E2). Vive en {@code data.local} a propósito:
 * {@code domain} no puede importar {@code @Entity} de Room (regla 1 del Protocolo).
 *
 * <p>{@code idLocal} es la PK de Room y la única identidad fiable sin red;
 * {@code idServidor} es {@code id_platillo} del servidor y llega {@code null} hasta que el
 * {@code POST} responda (Plan Fase 2b, §5.5). El índice único sobre {@code id_servidor}
 * garantiza que al bajar el delta nunca queden dos filas para el mismo platillo del
 * servidor.</p>
 *
 * <p>{@code actualizadoEn} es la marca {@code actualizado_en} del servidor: sirve para el
 * delta y para el conflicto last-write-wins (§4.6). {@code estadoSync} se guarda como texto
 * ("SINCRONIZADO", "PENDIENTE", "ERROR") — es la representación de {@code EstadoSync} en
 * disco.</p>
 */
@Entity(tableName = "platillos",
        indices = {@Index(value = "id_servidor", unique = true)})
public class PlatilloEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_local")
    private int idLocal;

    @Nullable
    @ColumnInfo(name = "id_servidor")
    private Integer idServidor;

    @ColumnInfo(name = "nombre")
    private String nombre;

    @Nullable
    @ColumnInfo(name = "descripcion")
    private String descripcion;

    @ColumnInfo(name = "precio")
    private double precio;

    /** Categoría local a la que pertenece: apunta a {@code CategoriaEntity.idLocal}. */
    @ColumnInfo(name = "id_categoria_local")
    private int idCategoriaLocal;

    @Nullable
    @ColumnInfo(name = "nombre_categoria")
    private String nombreCategoria;

    /** Ruta dentro del bucket, o {@code null}. Nunca la URL completa. */
    @Nullable
    @ColumnInfo(name = "ruta_imagen")
    private String rutaImagen;

    @ColumnInfo(name = "activo")
    private boolean activo;

    @ColumnInfo(name = "estado_sync")
    private String estadoSync;

    @Nullable
    @ColumnInfo(name = "actualizado_en")
    private String actualizadoEn;

    public int getIdLocal() {
        return idLocal;
    }

    public void setIdLocal(int idLocal) {
        this.idLocal = idLocal;
    }

    @Nullable
    public Integer getIdServidor() {
        return idServidor;
    }

    public void setIdServidor(@Nullable Integer idServidor) {
        this.idServidor = idServidor;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    @Nullable
    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(@Nullable String descripcion) {
        this.descripcion = descripcion;
    }

    public double getPrecio() {
        return precio;
    }

    public void setPrecio(double precio) {
        this.precio = precio;
    }

    public int getIdCategoriaLocal() {
        return idCategoriaLocal;
    }

    public void setIdCategoriaLocal(int idCategoriaLocal) {
        this.idCategoriaLocal = idCategoriaLocal;
    }

    @Nullable
    public String getNombreCategoria() {
        return nombreCategoria;
    }

    public void setNombreCategoria(@Nullable String nombreCategoria) {
        this.nombreCategoria = nombreCategoria;
    }

    @Nullable
    public String getRutaImagen() {
        return rutaImagen;
    }

    public void setRutaImagen(@Nullable String rutaImagen) {
        this.rutaImagen = rutaImagen;
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

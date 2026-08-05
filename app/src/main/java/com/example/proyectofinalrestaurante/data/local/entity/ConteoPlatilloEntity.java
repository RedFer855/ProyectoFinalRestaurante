package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Una fila del top-5 de platillos más pedidos, dentro de la instantánea de un rango (Plan
 * Fase 3c, §7.2). {@code orden} conserva el orden descendente que ya trae el servidor: sin él,
 * releer de Room después de rotar la pantalla podría mostrar el top-5 en otro orden.
 */
@Entity(tableName = "conteo_platillo", indices = {@Index("rango")})
public class ConteoPlatilloEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_local")
    private int idLocal;

    @NonNull
    @ColumnInfo(name = "rango")
    private String rango = "";

    @NonNull
    @ColumnInfo(name = "nombre")
    private String nombre = "";

    @ColumnInfo(name = "cantidad")
    private int cantidad;

    @ColumnInfo(name = "orden")
    private int orden;

    public int getIdLocal() {
        return idLocal;
    }

    public void setIdLocal(int idLocal) {
        this.idLocal = idLocal;
    }

    @NonNull
    public String getRango() {
        return rango;
    }

    public void setRango(@NonNull String rango) {
        this.rango = rango;
    }

    @NonNull
    public String getNombre() {
        return nombre;
    }

    public void setNombre(@NonNull String nombre) {
        this.nombre = nombre;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }
}

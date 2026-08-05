package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Una fila del desempeño por mesero, dentro de la instantánea de un rango (Plan Fase 3c,
 * §7.2). {@code orden} conserva el orden que trae el servidor, igual que
 * {@link ConteoPlatilloEntity}.
 */
@Entity(tableName = "desempeno_mesero", indices = {@Index("rango")})
public class DesempenoMeseroEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_local")
    private int idLocal;

    @NonNull
    @ColumnInfo(name = "rango")
    private String rango = "";

    @NonNull
    @ColumnInfo(name = "nombre")
    private String nombre = "";

    @ColumnInfo(name = "cantidad_pedidos")
    private int cantidadPedidos;

    @ColumnInfo(name = "total_vendido")
    private double totalVendido;

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

    public int getCantidadPedidos() {
        return cantidadPedidos;
    }

    public void setCantidadPedidos(int cantidadPedidos) {
        this.cantidadPedidos = cantidadPedidos;
    }

    public double getTotalVendido() {
        return totalVendido;
    }

    public void setTotalVendido(double totalVendido) {
        this.totalVendido = totalVendido;
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }
}

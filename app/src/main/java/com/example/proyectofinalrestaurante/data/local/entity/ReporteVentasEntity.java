package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Cabecera de la instantánea de un reporte de ventas (Plan Fase 3c, §7.2). La PK es
 * {@code rango} ({@code HOY}/{@code SEMANA}/{@code MES}) y no un id autogenerado: hay como
 * mucho tres filas en toda la tabla, una por rango, y cada refresco la reemplaza entera —
 * no es un catálogo que crece.
 */
@Entity(tableName = "reportes_ventas")
public class ReporteVentasEntity {

    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "rango")
    private String rango = "";

    @ColumnInfo(name = "generado_en")
    private long generadoEn;

    @ColumnInfo(name = "total_ventas")
    private double totalVentas;

    @ColumnInfo(name = "cantidad_pedidos")
    private int cantidadPedidos;

    @ColumnInfo(name = "ticket_promedio")
    private double ticketPromedio;

    @NonNull
    public String getRango() {
        return rango;
    }

    public void setRango(@NonNull String rango) {
        this.rango = rango;
    }

    public long getGeneradoEn() {
        return generadoEn;
    }

    public void setGeneradoEn(long generadoEn) {
        this.generadoEn = generadoEn;
    }

    public double getTotalVentas() {
        return totalVentas;
    }

    public void setTotalVentas(double totalVentas) {
        this.totalVentas = totalVentas;
    }

    public int getCantidadPedidos() {
        return cantidadPedidos;
    }

    public void setCantidadPedidos(int cantidadPedidos) {
        this.cantidadPedidos = cantidadPedidos;
    }

    public double getTicketPromedio() {
        return ticketPromedio;
    }

    public void setTicketPromedio(double ticketPromedio) {
        this.ticketPromedio = ticketPromedio;
    }
}

package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Marca de agua del sync delta, por tabla (Plan Fase 2b, §4.3).
 *
 * <p>{@code marcaAgua} es el {@code actualizado_en} más alto recibido de esa tabla: la
 * próxima bajada pide solo {@code actualizado_en > marca}. Sale de los datos recibidos, no
 * del reloj del teléfono, así no hace falta un endpoint de hora del servidor. Con la marca
 * vacía la primera bajada trae todo.</p>
 *
 * <p>{@code ultimoIntento} y {@code ultimoError} son solo diagnóstico: cuándo fue el último
 * intento de sync y con qué error terminó (transitorio, se va a reintentar).</p>
 */
@Entity(tableName = "sincronizacion")
public class SincronizacionEntity {

    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "tabla")
    private String tabla;

    @Nullable
    @ColumnInfo(name = "marca_agua")
    private String marcaAgua;

    @ColumnInfo(name = "ultimo_intento")
    private long ultimoIntento;

    @Nullable
    @ColumnInfo(name = "ultimo_error")
    private String ultimoError;

    public String getTabla() {
        return tabla;
    }

    public void setTabla(String tabla) {
        this.tabla = tabla;
    }

    @Nullable
    public String getMarcaAgua() {
        return marcaAgua;
    }

    public void setMarcaAgua(@Nullable String marcaAgua) {
        this.marcaAgua = marcaAgua;
    }

    public long getUltimoIntento() {
        return ultimoIntento;
    }

    public void setUltimoIntento(long ultimoIntento) {
        this.ultimoIntento = ultimoIntento;
    }

    @Nullable
    public String getUltimoError() {
        return ultimoError;
    }

    public void setUltimoError(@Nullable String ultimoError) {
        this.ultimoError = ultimoError;
    }
}

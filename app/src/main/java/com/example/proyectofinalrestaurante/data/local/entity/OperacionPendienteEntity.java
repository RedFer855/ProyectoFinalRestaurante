package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Operación pendiente de subir (Plan Fase 2b, §4.4 y E4): el outbox.
 *
 * <p>{@code tipo} es una de las constantes de {@code TipoOperacion}; {@code idLocal} apunta
 * a la fila local afectada; {@code payloadJson} lleva los datos de la operación (nombre,
 * precio, estado…), y {@code rutaImagenLocal} la ruta del archivo de imagen dentro de
 * {@code getFilesDir()} cuando la operación trae foto — nunca viaja en el JSON (Plan Fase
 * 2b, §5.2). Al drenar con éxito la operación, el archivo se borra.</p>
 *
 * <p>{@code intentos} cuenta los reintentos; {@code ultimoError} guarda el último error
 * transitorio para diagnósticos. El orden de drenado es FIFO estricto por {@code id}.</p>
 */
@Entity(tableName = "operaciones_pendientes")
public class OperacionPendienteEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @ColumnInfo(name = "tipo")
    private String tipo;

    @ColumnInfo(name = "id_local")
    private long idLocal;

    @ColumnInfo(name = "payload_json")
    private String payloadJson;

    @Nullable
    @ColumnInfo(name = "ruta_imagen_local")
    private String rutaImagenLocal;

    @ColumnInfo(name = "intentos")
    private int intentos;

    @Nullable
    @ColumnInfo(name = "ultimo_error")
    private String ultimoError;

    @ColumnInfo(name = "creado_en")
    private long creadoEn;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public long getIdLocal() {
        return idLocal;
    }

    public void setIdLocal(long idLocal) {
        this.idLocal = idLocal;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    @Nullable
    public String getRutaImagenLocal() {
        return rutaImagenLocal;
    }

    public void setRutaImagenLocal(@Nullable String rutaImagenLocal) {
        this.rutaImagenLocal = rutaImagenLocal;
    }

    public int getIntentos() {
        return intentos;
    }

    public void setIntentos(int intentos) {
        this.intentos = intentos;
    }

    @Nullable
    public String getUltimoError() {
        return ultimoError;
    }

    public void setUltimoError(@Nullable String ultimoError) {
        this.ultimoError = ultimoError;
    }

    public long getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(long creadoEn) {
        this.creadoEn = creadoEn;
    }
}

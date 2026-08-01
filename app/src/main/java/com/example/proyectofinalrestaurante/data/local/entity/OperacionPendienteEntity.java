package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
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
 *
 * <p><b>{@code modulo} particiona la cola</b> (v2 del esquema). La tabla es compartida por
 * el Menú y Empleados, y sin esta columna habría dos formas de romperse: el sincronizador
 * de un módulo descartaría como "tipo desconocido" las operaciones del otro, y las búsquedas
 * por {@code id_local} colisionarían — el platillo 3 y el empleado 3 tienen el mismo número.
 * El índice {@code (modulo, id)} es el que usa el drenado FIFO.</p>
 */
@Entity(tableName = "operaciones_pendientes",
        indices = {@Index(value = {"modulo", "id"})})
public class OperacionPendienteEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    /**
     * {@code @NonNull} + {@code defaultValue} no son adorno: hacen que el esquema que Room
     * genera en una instalación nueva sea <b>idéntico</b> al que deja la migración 1→2 sobre
     * una base existente. Sin el default declarado acá, {@code MigrationTestHelper} detecta
     * la diferencia y falla — que es exactamente para lo que sirve.
     */
    @NonNull
    @ColumnInfo(name = "modulo", defaultValue = "MENU")
    private String modulo;

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

    @NonNull
    public String getModulo() {
        return modulo;
    }

    public void setModulo(@NonNull String modulo) {
        this.modulo = modulo;
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

package com.example.proyectofinalrestaurante.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Fila local de una notificación (Plan Fase 3, §4.6).
 *
 * <p>El buzón reemplaza a las notificaciones push (Plan Fase 3, §3.3): cada vez que el
 * tablero ve un pedido nuevo o listo sin confirmar de lectura, crea una fila acá dirigida
 * al rol correspondiente ({@code rolDestino}) o al mesero concreto que tomó el pedido
 * ({@code destinatarioAuth}). {@code claveUnica} evita duplicados cuando el mismo cambio
 * llega por dos vías (Realtime + refresco de la ventana) o se repite en un reintento.</p>
 *
 * <p>La PK es local y {@code long} — las notificaciones nacen y mueren en el dispositivo y
 * no tienen identidad de servidor. {@code creadoEn} es epoch millis para el purgado por
 * antigüedad (historia 11) y para ordenar el buzón de más nueva a más vieja.</p>
 */
@Entity(tableName = "notificaciones",
        indices = @Index(value = "clave_unica", unique = true))
public class NotificacionEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_local")
    private long idLocal;

    @NonNull
    @ColumnInfo(name = "tipo")
    private String tipo;

    @Nullable
    @ColumnInfo(name = "rol_destino")
    private String rolDestino;

    @Nullable
    @ColumnInfo(name = "destinatario_auth")
    private String destinatarioAuth;

    @Nullable
    @ColumnInfo(name = "arg1")
    private String arg1;

    @ColumnInfo(name = "creado_en")
    private long creadoEn;

    @ColumnInfo(name = "leida")
    private boolean leida;

    @NonNull
    @ColumnInfo(name = "clave_unica")
    private String claveUnica;

    public long getIdLocal() {
        return idLocal;
    }

    public void setIdLocal(long idLocal) {
        this.idLocal = idLocal;
    }

    @NonNull
    public String getTipo() {
        return tipo;
    }

    public void setTipo(@NonNull String tipo) {
        this.tipo = tipo;
    }

    @Nullable
    public String getRolDestino() {
        return rolDestino;
    }

    public void setRolDestino(@Nullable String rolDestino) {
        this.rolDestino = rolDestino;
    }

    @Nullable
    public String getDestinatarioAuth() {
        return destinatarioAuth;
    }

    public void setDestinatarioAuth(@Nullable String destinatarioAuth) {
        this.destinatarioAuth = destinatarioAuth;
    }

    @Nullable
    public String getArg1() {
        return arg1;
    }

    public void setArg1(@Nullable String arg1) {
        this.arg1 = arg1;
    }

    public long getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(long creadoEn) {
        this.creadoEn = creadoEn;
    }

    public boolean isLeida() {
        return leida;
    }

    public void setLeida(boolean leida) {
        this.leida = leida;
    }

    @NonNull
    public String getClaveUnica() {
        return claveUnica;
    }

    public void setClaveUnica(@NonNull String claveUnica) {
        this.claveUnica = claveUnica;
    }
}
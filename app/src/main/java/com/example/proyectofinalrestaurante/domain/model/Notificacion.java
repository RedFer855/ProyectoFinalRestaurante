package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Notificación del buzón (Plan Fase 3, §4.6). Inmutable.
 *
 * <p>El buzón es <b>local</b>: no existe en el servidor, se deriva en el dispositivo al
 * aplicar el delta de Pedidos y sobrevive el turno en Room. {@code rolDestino} y
 * {@code destinatarioAuth} son dos filtros ortogonales — el primero ("todos los de cocina")
 * o el segundo ("este mesero") — y el filtrado va en la consulta, no en quien escribe
 * (menos acoplamiento: {@code SincronizadorPedidos} no necesita conocer la sesión).</p>
 *
 * <p>{@code arg1} lleva el argumento del texto (el número del pedido); la UI resuelve
 * {@code getString(R.string.notif_pedido_nuevo, arg1)} — el texto nunca viaja en la base.</p>
 */
public final class Notificacion {

    private final long idLocal;
    private final TipoNotificacion tipo;
    @Nullable private final String rolDestino;
    @Nullable private final String destinatarioAuth;
    @Nullable private final String arg1;
    /** Instante en que se derivó, en {@code System.currentTimeMillis()}. */
    private final long creadoEn;
    private final boolean leida;

    public Notificacion(long idLocal, TipoNotificacion tipo, @Nullable String rolDestino,
                        @Nullable String destinatarioAuth, @Nullable String arg1,
                        long creadoEn, boolean leida) {
        this.idLocal = idLocal;
        this.tipo = tipo;
        this.rolDestino = rolDestino;
        this.destinatarioAuth = destinatarioAuth;
        this.arg1 = arg1;
        this.creadoEn = creadoEn;
        this.leida = leida;
    }

    public long getIdLocal() {
        return idLocal;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    @Nullable
    public String getRolDestino() {
        return rolDestino;
    }

    @Nullable
    public String getDestinatarioAuth() {
        return destinatarioAuth;
    }

    @Nullable
    public String getArg1() {
        return arg1;
    }

    public long getCreadoEn() {
        return creadoEn;
    }

    public boolean isLeida() {
        return leida;
    }

    /**
     * Copia marcada como leída. Inmutable, para que {@code DiffUtil} detecte el cambio.
     */
    public Notificacion comoLeida() {
        return new Notificacion(idLocal, tipo, rolDestino, destinatarioAuth, arg1,
                creadoEn, true);
    }
}

package com.example.proyectofinalrestaurante.data.sync.payload;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuerpo del {@code CREAR_PEDIDO} del outbox (Plan Fase 3b, §5.3 / §4.2).
 *
 * <p>Es <b>al mismo tiempo</b> el JSON que se guarda en la cola y la base del JSONB que se
 * manda al RPC {@code crear_pedido}: el {@code SincronizadorPedidos} lo serializa cuando el
 * pedido se toma, y al drenar lee ese mismo payload, <b>resuelve los ids locales</b> que aún
 * no tienen {@code id_servidor} (la mesa y el cliente, §4.2) y reemplaza esos campos antes de
 * enviarlo. Los {@code id_platillo} de las líneas ya viajan como ids <b>del servidor</b>.</p>
 *
 * <p>Los campos {@code mesa_id_local}/{@code cliente_id_local} guardan los ids de Room (no
 * los del servidor): por eso el payload no se puede mandar tal cual — el servidor espera
 * {@code id_mesa}/{@code id_cliente} reales. Un pedido puede tomarse sin mesa ni cliente.</p>
 */
public final class PayloadCrearPedido {

    private static final Gson GSON = new Gson();

    private PayloadCrearPedido() {
    }

    /** Serializa el payload para guardarlo en el outbox. */
    public static String serializar(String claveIdempotencia, String fecha, int idTipoPedido,
                                    @Nullable Integer mesaIdLocal,
                                    @Nullable Integer clienteIdLocal,
                                    List<Linea> lineas) {
        Cuerpo cuerpo = new Cuerpo();
        cuerpo.claveIdempotencia = claveIdempotencia;
        cuerpo.fecha = fecha;
        cuerpo.idTipoPedido = idTipoPedido;
        cuerpo.mesaIdLocal = mesaIdLocal;
        cuerpo.clienteIdLocal = clienteIdLocal;
        cuerpo.lineas = new ArrayList<>(lineas);
        return GSON.toJson(cuerpo);
    }

    @Nullable
    public static Cuerpo parsear(@Nullable String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return GSON.fromJson(json, Cuerpo.class);
    }

    /**
     * Serializa el JSON que se manda al RPC {@code crear_pedido} (Plan Fase 3b, §5.3,
     * §4.2). Los {@code id_platillo} ya viajan como ids del servidor en el payload; acá se
     * reemplazan {@code mesa_id_local}/{@code cliente_id_local} por los {@code id_mesa}/
     * {@code id_cliente} reales que el {@code SincronizadorPedidos} resolvió al drenar.
     * {@code idMesa}/{@code idCliente} pueden ser {@code null}: la política de degradación
     * (§4.2) sube el pedido sin ellos antes que no subirlo.
     */
    public static String serializarRpc(String claveIdempotencia, String fecha, int idTipoPedido,
                                       @Nullable Integer idMesa, @Nullable Integer idCliente,
                                       List<Linea> lineas) {
        RpcCuerpo cuerpo = new RpcCuerpo();
        cuerpo.claveIdempotencia = claveIdempotencia;
        cuerpo.fecha = fecha;
        cuerpo.idTipoPedido = idTipoPedido;
        cuerpo.idMesa = idMesa;
        cuerpo.idCliente = idCliente;
        cuerpo.lineas = new ArrayList<>(lineas);
        return GSON.toJson(cuerpo);
    }

    /** Una línea del payload. {@code idPlatillo} es el id <b>del servidor</b>. */
    public static final class Linea {
        @SerializedName("id_platillo")
        private final int idPlatillo;
        @SerializedName("cantidad")
        private final int cantidad;
        @SerializedName("complementos")
        private final List<Object> complementos = java.util.Collections.emptyList();

        public Linea(int idPlatillo, int cantidad) {
            this.idPlatillo = idPlatillo;
            this.cantidad = cantidad;
        }

        public int getIdPlatillo() {
            return idPlatillo;
        }

        public int getCantidad() {
            return cantidad;
        }
    }

    /** Cuerpo deserializado; míralo como inmutable: se lee, no se edita. */
    public static final class Cuerpo {
        @SerializedName("clave_idempotencia")
        String claveIdempotencia;
        @SerializedName("fecha")
        String fecha;
        @SerializedName("id_tipo_pedido")
        int idTipoPedido;
        @SerializedName("mesa_id_local")
        Integer mesaIdLocal;
        @SerializedName("cliente_id_local")
        Integer clienteIdLocal;
        @SerializedName("lineas")
        List<Linea> lineas;

        public String getClaveIdempotencia() {
            return claveIdempotencia;
        }

        public String getFecha() {
            return fecha;
        }

        public int getIdTipoPedido() {
            return idTipoPedido;
        }

        @Nullable
        public Integer getMesaIdLocal() {
            return mesaIdLocal;
        }

        @Nullable
        public Integer getClienteIdLocal() {
            return clienteIdLocal;
        }

        @Nullable
        public List<Linea> getLineas() {
            return lineas;
        }
    }

    /** Cuerpo del RPC {@code crear_pedido}; los ids ya resueltos del servidor, nullable. */
    private static final class RpcCuerpo {
        @SerializedName("clave_idempotencia")
        String claveIdempotencia;
        @SerializedName("fecha")
        String fecha;
        @SerializedName("id_tipo_pedido")
        int idTipoPedido;
        @SerializedName("id_mesa")
        Integer idMesa;
        @SerializedName("id_cliente")
        Integer idCliente;
        @SerializedName("lineas")
        List<Linea> lineas;
    }
}
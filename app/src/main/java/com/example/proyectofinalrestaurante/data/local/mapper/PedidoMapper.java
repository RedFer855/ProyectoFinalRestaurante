package com.example.proyectofinalrestaurante.data.local.mapper;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.entity.PedidoEntity;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.Pedido;

/**
 * Mapeo {@link PedidoEntity} ↔ {@link Pedido} (Plan Fase 3, E3). Mismo rol que
 * {@link MesaMapper} en el módulo Mesas.
 *
 * <p>Cuando el servidor trae un {@code id_estado_pedido} que este APK no conoce,
 * {@link EstadoPedido#porId(int)} devuelve {@code null} y el dominio lo ve como estado
 * desconocido — <b>nunca</b> se mapea a un estado conocido por defecto (Plan Fase 3, §4.3).
 * El centinela {@code 0} en {@code aEntidad} mantiene la columna {@code id_estado_pedido}
 * {@code NOT NULL} y redondea a "desconocido" en el próximo {@link #aDominio}.</p>
 */
public final class PedidoMapper {

    private static final int ID_ESTADO_PEDIDO_DESCONOCIDO = 0;

    private PedidoMapper() {
    }

    public static Pedido aDominio(PedidoEntity entidad) {
        return new Pedido(
                entidad.getIdLocal(),
                entidad.getIdServidor(),
                entidad.getFecha(),
                EstadoPedido.porId(entidad.getIdEstadoPedido()),
                entidad.getNumeroMesa(),
                entidad.getCliente(),
                entidad.getTotal(),
                entidad.getCantidadItems(),
                entidad.getIdAuthUsuario(),
                entidad.getActualizadoEn(),
                PlatilloMapper.aEstadoSync(entidad.getEstadoSync()));
    }

    public static PedidoEntity aEntidad(Pedido pedido) {
        PedidoEntity entidad = new PedidoEntity();
        entidad.setIdLocal(pedido.getIdLocal());
        entidad.setIdServidor(pedido.getIdServidor());
        entidad.setFecha(pedido.getFecha());
        // Estado: solo se reescribe cuando se conoce. El centinela 0 mantiene la columna
        // NOT NULL y redondea a "desconocido" en el próximo aDominio (porId(0) = null).
        EstadoPedido estado = pedido.getEstado();
        entidad.setIdEstadoPedido(estado == null ? ID_ESTADO_PEDIDO_DESCONOCIDO : estado.getId());
        entidad.setNumeroMesa(pedido.getNumeroMesa());
        entidad.setCliente(pedido.getCliente());
        entidad.setTotal(pedido.getTotal());
        entidad.setCantidadItems(pedido.getCantidadItems());
        entidad.setIdAuthUsuario(pedido.getIdAuthUsuario());
        entidad.setActualizadoEn(pedido.getActualizadoEn());
        entidad.setEstadoSync(pedido.getEstadoSync().name());
        return entidad;
    }

    /**
     * Entidad a partir de una fila bajada del servidor (delta). Todo lo que baja llega
     * sincronizado; {@code idServidor} es la clave {@code id_pedido} del servidor.
     * El {@code estadoSync} siempre es {@code SINCRONIZADO}.
     */
    public static PedidoEntity desdeServidor(Integer idServidor, String fecha,
                                             int idEstadoPedido, @Nullable Integer numeroMesa,
                                             @Nullable String cliente, double total,
                                             int cantidadItems,
                                             @Nullable String idAuthUsuario,
                                             @Nullable String actualizadoEn) {
        PedidoEntity entidad = new PedidoEntity();
        entidad.setIdServidor(idServidor);
        entidad.setFecha(fecha);
        entidad.setIdEstadoPedido(idEstadoPedido);
        entidad.setNumeroMesa(numeroMesa);
        entidad.setCliente(cliente);
        entidad.setTotal(total);
        entidad.setCantidadItems(cantidadItems);
        entidad.setIdAuthUsuario(idAuthUsuario);
        entidad.setActualizadoEn(actualizadoEn);
        entidad.setEstadoSync("SINCRONIZADO");
        return entidad;
    }
}
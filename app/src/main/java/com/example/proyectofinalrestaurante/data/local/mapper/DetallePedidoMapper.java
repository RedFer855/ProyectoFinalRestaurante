package com.example.proyectofinalrestaurante.data.local.mapper;

import com.example.proyectofinalrestaurante.data.local.entity.DetallePedidoEntity;
import com.example.proyectofinalrestaurante.domain.model.LineaPedido;

/**
 * Mapeo de una línea del detalle de un pedido (Plan Fase 3b, §6.2 / E3). Mismo rol que
 * {@link PedidoMapper} para la cabecera.
 *
 * <p>{@code nombre} no viaja en {@link DetallePedidoEntity} (que no lo persiste): la capa de
 * lectura lo resuelve con el JOIN a {@code platillos}, como manda §5.6 del plan. Quien lea
 * detalle (el repositorio) pasa el nombre ya resuelto. {@code idServidor} queda {@code null}
 * mientras el {@code CREAR_PEDIDO} no drene o el delta no lo traiga.</p>
 */
public final class DetallePedidoMapper {

    private DetallePedidoMapper() {
    }

    public static LineaPedido aDominio(DetallePedidoEntity detalle, String nombreDelPlatillo) {
        return new LineaPedido(
                detalle.getIdLocal(),
                detalle.getIdServidor(),
                nombreDelPlatillo,
                detalle.getCantidad(),
                detalle.getPrecio());
    }

    /** Línea de detalle local a partir de un {@code LineaCarrito} del alta. */
    public static DetallePedidoEntity aEntidad(long idPedidoLocal, int idPlatilloLocal,
                                               int cantidad, double precio) {
        DetallePedidoEntity detalle = new DetallePedidoEntity();
        detalle.setIdPedidoLocal(idPedidoLocal);
        detalle.setIdPlatilloLocal(idPlatilloLocal);
        detalle.setCantidad(cantidad);
        detalle.setPrecio(precio);
        return detalle;
    }
}
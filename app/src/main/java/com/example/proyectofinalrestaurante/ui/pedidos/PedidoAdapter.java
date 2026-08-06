package com.example.proyectofinalrestaurante.ui.pedidos;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.model.Pedido;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.chip.Chip;

/**
 * Lista de pedidos del tablero (Plan Fase 3, E8), ahora sobre {@link Pedido} del dominio
 * (los datos falsos de la maqueta se fueron en la E10). Tres permisos distintos conviven
 * en la misma tarjeta: avanzar el estado (admin, cocina, y mesero solo Listo → Entregado —
 * ver {@code ReglasPedido.puedeCambiarA}), editar (admin y mesero) y cancelar (solo admin).
 *
 * <p>El chip de estado también es el botón de "avanzar": por eso queda clickeable solo cuando
 * la matriz de permisos lo permite. La validación fina de la transición (rol + estado actual)
 * la vuelve a hacer el ViewModel con {@code ReglasPedido}.</p>
 */
public class PedidoAdapter extends ListAdapter<Pedido, PedidoAdapter.Holder> {

    public interface AlInteractuar {
        void onAvanzarEstado(Pedido pedido);

        void onAccion(Pedido pedido, int accionId);

        /** Abre el detalle del pedido (Plan Fase 3b, E9) al tocar la tarjeta. */
        void onVerDetalle(Pedido pedido);
    }

    private final AlInteractuar alInteractuar;

    public PedidoAdapter(AlInteractuar alInteractuar) {
        super(DIFF);
        this.alInteractuar = alInteractuar;
    }

    private static final DiffUtil.ItemCallback<Pedido> DIFF =
            new DiffUtil.ItemCallback<Pedido>() {
                @Override
                public boolean areItemsTheSame(@NonNull Pedido a, @NonNull Pedido b) {
                    return a.getIdLocal() == b.getIdLocal();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Pedido a, @NonNull Pedido b) {
                    return a.getEstado() == b.getEstado()
                            && a.getTotal() == b.getTotal()
                            && a.getEstadoSync() == b.getEstadoSync();
                }
            };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pedido, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(getItem(position), alInteractuar);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final TextView numero;
        private final TextView hora;
        private final TextView referencia;
        private final TextView total;
        private final Chip estado;
        private final ImageButton opciones;

        Holder(@NonNull View itemView) {
            super(itemView);
            numero = itemView.findViewById(R.id.txt_numero_pedido);
            hora = itemView.findViewById(R.id.txt_hora_pedido);
            referencia = itemView.findViewById(R.id.txt_referencia_pedido);
            total = itemView.findViewById(R.id.txt_total_pedido);
            estado = itemView.findViewById(R.id.chip_estado_pedido);
            opciones = itemView.findViewById(R.id.btn_opciones_pedido);
        }

        void enlazar(Pedido pedido, AlInteractuar alInteractuar) {
            ContextoLocal contexto = new ContextoLocal(itemView);

            itemView.setContentDescription(
                    itemView.getContext().getString(R.string.detalle_cd_ver_detalle));
            itemView.setOnClickListener(v -> alInteractuar.onVerDetalle(pedido));

            long numeroVisible = pedido.getIdServidor() != null
                    ? pedido.getIdServidor() : pedido.getIdLocal();
            numero.setText(itemView.getContext().getString(R.string.pedidos_numero, numeroVisible));
            hora.setText(horaDe(pedido.getFecha()));

            String mesa = pedido.getNumeroMesa() != null
                    ? itemView.getContext().getString(R.string.pedidos_mesa, pedido.getNumeroMesa())
                    : itemView.getContext().getString(R.string.pedidos_para_llevar);
            String cliente = pedido.getCliente();
            referencia.setText(cliente == null
                    ? mesa : mesa + " · " + cliente);

            total.setText(itemView.getContext()
                    .getString(R.string.formato_lempiras, pedido.getTotal()));

            contexto.aplicarEstado(estado, pedido);

            boolean puedeAvanzar = VistaPorPermiso.puede(Modulo.PEDIDOS, Accion.CAMBIAR_ESTADO);
            estado.setClickable(puedeAvanzar);
            estado.setContentDescription(puedeAvanzar
                    ? itemView.getContext().getString(R.string.pedidos_cd_avanzar) : null);
            estado.setOnClickListener(puedeAvanzar ? v -> alInteractuar.onAvanzarEstado(pedido) : null);

            boolean puedeEditar = VistaPorPermiso.puede(Modulo.PEDIDOS, Accion.EDITAR);
            boolean puedeCancelar = VistaPorPermiso.puede(Modulo.PEDIDOS, Accion.ELIMINAR);
            opciones.setVisibility(puedeEditar || puedeCancelar ? View.VISIBLE : View.GONE);
            opciones.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(v.getContext(), v);
                menu.inflate(R.menu.menu_acciones);
                menu.getMenu().findItem(R.id.accion_editar).setVisible(puedeEditar);
                // En pedidos no se borra: se cancela, que deja rastro.
                menu.getMenu().findItem(R.id.accion_eliminar)
                        .setTitle(R.string.accion_cancelar_pedido)
                        .setVisible(puedeCancelar);
                menu.setOnMenuItemClickListener(item -> {
                    alInteractuar.onAccion(pedido, item.getItemId());
                    return true;
                });
                menu.show();
            });
        }
    }

    /**
     * Extrae {@code HH:mm} de la fecha ISO 8601 del servidor ("2026-08-01T10:00:00Z" → "10:00").
     * No se parsea con {@code java.time} para no forzar desugaring: el texto ya viene con la
     * zona y solo interesa la hora local del servidor para el tablero.
     */
    @Nullable
    private static String horaDe(@Nullable String fecha) {
        if (fecha == null) {
            return null;
        }
        int inicio = fecha.indexOf('T');
        if (inicio < 0 || inicio + 5 > fecha.length()) {
            return null;
        }
        return fecha.substring(inicio + 1, inicio + 6);
    }

    /** Aplica el color + texto del estado y, si quedó pendiente de subir, lo advierte. */
    private static final class ContextoLocal {

        private final View itemView;

        ContextoLocal(View itemView) {
            this.itemView = itemView;
        }

        void aplicarEstado(Chip chip, Pedido pedido) {
            chip.setText(itemView.getContext()
                    .getString(EstadoPedidoUi.etiqueta(pedido.getEstado())));
            chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(
                    itemView.getContext(), EstadoPedidoUi.color(pedido.getEstado()))));
            chip.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));
        }
    }
}

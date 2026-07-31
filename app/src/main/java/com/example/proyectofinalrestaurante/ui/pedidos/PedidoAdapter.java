package com.example.proyectofinalrestaurante.ui.pedidos;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.ui.maqueta.DatosMaqueta;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.chip.Chip;

/**
 * Lista de pedidos. Tres permisos distintos conviven en la misma tarjeta:
 * avanzar el estado (admin y cocina), editar (admin y mesero) y cancelar (solo admin).
 */
public class PedidoAdapter extends ListAdapter<DatosMaqueta.Pedido, PedidoAdapter.Holder> {

    public interface AlInteractuar {
        void onAvanzarEstado(DatosMaqueta.Pedido pedido);

        void onAccion(DatosMaqueta.Pedido pedido, int accionId);
    }

    private final AlInteractuar alInteractuar;

    public PedidoAdapter(AlInteractuar alInteractuar) {
        super(DIFF);
        this.alInteractuar = alInteractuar;
    }

    private static final DiffUtil.ItemCallback<DatosMaqueta.Pedido> DIFF =
            new DiffUtil.ItemCallback<DatosMaqueta.Pedido>() {
                @Override
                public boolean areItemsTheSame(@NonNull DatosMaqueta.Pedido a,
                                               @NonNull DatosMaqueta.Pedido b) {
                    return a.numero == b.numero;
                }

                @Override
                public boolean areContentsTheSame(@NonNull DatosMaqueta.Pedido a,
                                                  @NonNull DatosMaqueta.Pedido b) {
                    return a.estado == b.estado && a.total == b.total;
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

        void enlazar(DatosMaqueta.Pedido pedido, AlInteractuar alInteractuar) {
            numero.setText(itemView.getContext().getString(R.string.pedidos_numero, pedido.numero));
            hora.setText(pedido.hora);
            referencia.setText(pedido.referencia + " · " + pedido.cliente);
            total.setText(itemView.getContext()
                    .getString(R.string.formato_lempiras, pedido.total));

            estado.setText(pedido.estado.etiqueta);
            estado.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), pedido.estado.color)));
            estado.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));

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
}

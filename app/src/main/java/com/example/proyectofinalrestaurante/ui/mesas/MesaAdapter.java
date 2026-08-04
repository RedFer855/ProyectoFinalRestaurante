package com.example.proyectofinalrestaurante.ui.mesas;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.chip.Chip;

/**
 * Grilla de mesas (Fase 2c). Tocar una cambia su estado, solo si el rol tiene
 * {@link Accion#CAMBIAR_ESTADO}. El ⋮ (solo admin) queda para editar y dar de baja.
 */
public class MesaAdapter extends ListAdapter<Mesa, MesaAdapter.Holder> {

    public interface AlTocarMesa {
        void onTocar(Mesa mesa);
    }

    private final AlTocarMesa alTocarMesa;

    public MesaAdapter(AlTocarMesa alTocarMesa) {
        super(DIFF);
        this.alTocarMesa = alTocarMesa;
    }

    private static final DiffUtil.ItemCallback<Mesa> DIFF =
            new DiffUtil.ItemCallback<Mesa>() {
                @Override
                public boolean areItemsTheSame(@NonNull Mesa a, @NonNull Mesa b) {
                    return a.getIdLocal() == b.getIdLocal();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Mesa a, @NonNull Mesa b) {
                    return a.getEstadoMesa() == b.getEstadoMesa()
                            && a.getCapacidad() == b.getCapacidad()
                            && a.isActivo() == b.isActivo();
                }
            };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mesa, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(getItem(position), alTocarMesa);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final TextView numero;
        private final TextView capacidad;
        private final Chip estado;

        Holder(@NonNull View itemView) {
            super(itemView);
            numero = itemView.findViewById(R.id.txt_numero_mesa);
            capacidad = itemView.findViewById(R.id.txt_capacidad_mesa);
            estado = itemView.findViewById(R.id.chip_estado_mesa);
        }

        void enlazar(Mesa mesa, AlTocarMesa alTocarMesa) {
            numero.setText(itemView.getContext().getString(R.string.mesas_numero,
                    mesa.getNumeroMesa()));
            capacidad.setText(itemView.getContext().getString(R.string.mesas_capacidad,
                    mesa.getCapacidad()));

            int etiquetaRes = etiquetaDeEstado(mesa.getEstadoMesa());
            int colorRes = colorDeEstado(mesa.getEstadoMesa());
            estado.setText(etiquetaRes);
            estado.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), colorRes)));
            estado.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));
            estado.setClickable(false);

            boolean puedeCambiar = VistaPorPermiso.puede(Modulo.MESAS, Accion.CAMBIAR_ESTADO);
            itemView.setClickable(puedeCambiar);
            itemView.setOnClickListener(puedeCambiar ? v -> alTocarMesa.onTocar(mesa) : null);
        }

        @ColorRes
        private static int colorDeEstado(EstadoMesa estado) {
            switch (estado) {
                case OCUPADA:
                    return R.color.estado_mesa_ocupada;
                case RESERVADA:
                    return R.color.estado_mesa_reservada;
                case LIBRE:
                default:
                    return R.color.estado_mesa_libre;
            }
        }

        private static int etiquetaDeEstado(EstadoMesa estado) {
            switch (estado) {
                case OCUPADA:
                    return R.string.estado_mesa_ocupada;
                case RESERVADA:
                    return R.string.estado_mesa_reservada;
                case LIBRE:
                default:
                    return R.string.estado_mesa_libre;
            }
        }
    }
}

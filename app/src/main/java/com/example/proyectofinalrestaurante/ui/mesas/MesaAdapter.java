package com.example.proyectofinalrestaurante.ui.mesas;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

/** Grilla de mesas. Tocar una cambia su estado, solo si el rol tiene CAMBIAR_ESTADO. */
public class MesaAdapter extends ListAdapter<DatosMaqueta.Mesa, MesaAdapter.Holder> {

    public interface AlTocarMesa {
        void onTocar(DatosMaqueta.Mesa mesa);
    }

    private final AlTocarMesa alTocarMesa;

    public MesaAdapter(AlTocarMesa alTocarMesa) {
        super(DIFF);
        this.alTocarMesa = alTocarMesa;
    }

    private static final DiffUtil.ItemCallback<DatosMaqueta.Mesa> DIFF =
            new DiffUtil.ItemCallback<DatosMaqueta.Mesa>() {
                @Override
                public boolean areItemsTheSame(@NonNull DatosMaqueta.Mesa a,
                                               @NonNull DatosMaqueta.Mesa b) {
                    return a.numero == b.numero;
                }

                @Override
                public boolean areContentsTheSame(@NonNull DatosMaqueta.Mesa a,
                                                  @NonNull DatosMaqueta.Mesa b) {
                    return a.estado == b.estado && a.capacidad == b.capacidad;
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

        void enlazar(DatosMaqueta.Mesa mesa, AlTocarMesa alTocarMesa) {
            numero.setText(itemView.getContext().getString(R.string.mesas_numero, mesa.numero));
            capacidad.setText(itemView.getContext()
                    .getString(R.string.mesas_capacidad, mesa.capacidad));

            estado.setText(mesa.estado.etiqueta);
            estado.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), mesa.estado.color)));
            estado.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));
            // El chip es solo indicador: quien recibe el toque es la tarjeta entera,
            // que es un blanco mucho más cómodo.
            estado.setClickable(false);

            boolean puedeCambiar = VistaPorPermiso.puede(Modulo.MESAS, Accion.CAMBIAR_ESTADO);
            itemView.setClickable(puedeCambiar);
            itemView.setOnClickListener(puedeCambiar ? v -> alTocarMesa.onTocar(mesa) : null);
        }
    }
}

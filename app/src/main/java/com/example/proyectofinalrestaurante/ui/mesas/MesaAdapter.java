package com.example.proyectofinalrestaurante.ui.mesas;

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
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.chip.Chip;

/**
 * Grilla de mesas (Plan Fase 2c, E6). Tocar la tarjeta cambia el estado operativo — es la
 * acción principal, según el rol (§4 del plan); el botón ⋮ (solo quien puede EDITAR) abre
 * editar y dar de baja/reactivar.
 */
public class MesaAdapter extends ListAdapter<Mesa, MesaAdapter.Holder> {

    public interface AlTocarMesa {
        void onTocar(Mesa mesa);
    }

    public interface AlElegirAccion {
        void onAccion(Mesa mesa, int accionId);
    }

    private final AlTocarMesa alTocarMesa;
    private final AlElegirAccion alElegirAccion;

    public MesaAdapter(AlTocarMesa alTocarMesa, AlElegirAccion alElegirAccion) {
        super(DIFF);
        this.alTocarMesa = alTocarMesa;
        this.alElegirAccion = alElegirAccion;
    }

    private static final DiffUtil.ItemCallback<Mesa> DIFF = new DiffUtil.ItemCallback<Mesa>() {
        @Override
        public boolean areItemsTheSame(@NonNull Mesa a, @NonNull Mesa b) {
            return a.getIdLocal() == b.getIdLocal();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Mesa a, @NonNull Mesa b) {
            return a.getEstado() == b.getEstado() && a.getCapacidad() == b.getCapacidad()
                    && a.isActivo() == b.isActivo() && a.getEstadoSync() == b.getEstadoSync()
                    && a.getNumeroMesa() == b.getNumeroMesa();
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
        holder.enlazar(getItem(position), alTocarMesa, alElegirAccion);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final TextView numero;
        private final TextView capacidad;
        private final Chip estado;
        private final Chip sincronizacion;
        private final ImageButton opciones;

        Holder(@NonNull View itemView) {
            super(itemView);
            numero = itemView.findViewById(R.id.txt_numero_mesa);
            capacidad = itemView.findViewById(R.id.txt_capacidad_mesa);
            estado = itemView.findViewById(R.id.chip_estado_mesa);
            sincronizacion = itemView.findViewById(R.id.chip_sync_mesa);
            opciones = itemView.findViewById(R.id.btn_opciones_mesa);
        }

        void enlazar(Mesa mesa, AlTocarMesa alTocarMesa, AlElegirAccion alElegirAccion) {
            numero.setText(itemView.getContext().getString(R.string.mesas_numero, mesa.getNumeroMesa()));
            capacidad.setText(itemView.getContext()
                    .getString(R.string.mesas_capacidad, mesa.getCapacidad()));

            boolean deBaja = !mesa.isActivo();
            estado.setText(deBaja ? R.string.mesas_de_baja : EstadoMesaUi.etiqueta(mesa.getEstado()));
            int colorEstado = deBaja ? R.color.estado_entregado : EstadoMesaUi.color(mesa.getEstado());
            estado.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), colorEstado)));
            estado.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));

            // Regla 8 de Offline-First: el usuario nunca queda con la duda de si su cambio
            // llegó al servidor.
            sincronizacion.setVisibility(
                    mesa.getEstadoSync() != EstadoSync.SINCRONIZADO ? View.VISIBLE : View.GONE);

            boolean puedeCambiarEstado = !deBaja
                    && VistaPorPermiso.puede(Modulo.MESAS, Accion.CAMBIAR_ESTADO);
            itemView.setClickable(puedeCambiarEstado);
            itemView.setOnClickListener(puedeCambiarEstado ? v -> alTocarMesa.onTocar(mesa) : null);

            boolean puedeEditar = VistaPorPermiso.puede(Modulo.MESAS, Accion.EDITAR);
            opciones.setVisibility(puedeEditar ? View.VISIBLE : View.GONE);
            if (!puedeEditar) {
                return;
            }
            opciones.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(v.getContext(), v);
                menu.inflate(R.menu.menu_mesa);
                menu.getMenu().findItem(R.id.accion_activar_desactivar_mesa)
                        .setTitle(mesa.isActivo() ? R.string.accion_desactivar : R.string.accion_activar);
                menu.setOnMenuItemClickListener(item -> {
                    alElegirAccion.onAccion(mesa, item.getItemId());
                    return true;
                });
                menu.show();
            });
        }
    }
}

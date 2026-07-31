package com.example.proyectofinalrestaurante.ui.menu;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.ui.maqueta.DatosMaqueta;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;

/** Lista de platillos. El menú ⋮ solo aparece si el rol puede editar o eliminar. */
public class PlatilloAdapter extends ListAdapter<DatosMaqueta.Platillo, PlatilloAdapter.Holder> {

    /** Se avisa al Fragment para que muestre el aviso de "es una maqueta". */
    public interface AlElegirAccion {
        void onAccion(DatosMaqueta.Platillo platillo, int accionId);
    }

    private final AlElegirAccion alElegirAccion;

    public PlatilloAdapter(AlElegirAccion alElegirAccion) {
        super(DIFF);
        this.alElegirAccion = alElegirAccion;
    }

    private static final DiffUtil.ItemCallback<DatosMaqueta.Platillo> DIFF =
            new DiffUtil.ItemCallback<DatosMaqueta.Platillo>() {
                @Override
                public boolean areItemsTheSame(@NonNull DatosMaqueta.Platillo a,
                                               @NonNull DatosMaqueta.Platillo b) {
                    return a.nombre.equals(b.nombre);
                }

                @Override
                public boolean areContentsTheSame(@NonNull DatosMaqueta.Platillo a,
                                                  @NonNull DatosMaqueta.Platillo b) {
                    return a.precio == b.precio
                            && a.categoria.equals(b.categoria)
                            && a.descripcion.equals(b.descripcion);
                }
            };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_platillo, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(getItem(position), alElegirAccion);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final TextView nombre;
        private final TextView categoria;
        private final TextView descripcion;
        private final TextView precio;
        private final ImageButton opciones;

        Holder(@NonNull View itemView) {
            super(itemView);
            nombre = itemView.findViewById(R.id.txt_nombre_platillo);
            categoria = itemView.findViewById(R.id.txt_categoria_platillo);
            descripcion = itemView.findViewById(R.id.txt_descripcion_platillo);
            precio = itemView.findViewById(R.id.txt_precio_platillo);
            opciones = itemView.findViewById(R.id.btn_opciones_platillo);
        }

        void enlazar(DatosMaqueta.Platillo platillo, AlElegirAccion alElegirAccion) {
            nombre.setText(platillo.nombre);
            categoria.setText(platillo.categoria);
            descripcion.setText(platillo.descripcion);
            precio.setText(itemView.getContext()
                    .getString(R.string.formato_lempiras, platillo.precio));

            boolean puedeEditar = VistaPorPermiso.puede(Modulo.MENU, Accion.EDITAR);
            boolean puedeEliminar = VistaPorPermiso.puede(Modulo.MENU, Accion.ELIMINAR);
            // Sin ninguna acción disponible, el botón ⋮ no tiene razón de existir.
            opciones.setVisibility(puedeEditar || puedeEliminar ? View.VISIBLE : View.GONE);
            opciones.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(v.getContext(), v);
                menu.inflate(R.menu.menu_acciones);
                menu.getMenu().findItem(R.id.accion_editar).setVisible(puedeEditar);
                menu.getMenu().findItem(R.id.accion_eliminar).setVisible(puedeEliminar);
                menu.setOnMenuItemClickListener(item -> {
                    alElegirAccion.onAccion(platillo, item.getItemId());
                    return true;
                });
                menu.show();
            });
        }
    }
}

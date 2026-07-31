package com.example.proyectofinalrestaurante.ui.empleados;

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
import com.example.proyectofinalrestaurante.ui.maqueta.DatosMaqueta;
import com.google.android.material.chip.Chip;

/**
 * Lista de empleados. Todas las acciones son de admin — el módulo entero no existe
 * para los demás roles, así que acá no hace falta filtrar por permiso.
 */
public class EmpleadoAdapter extends ListAdapter<DatosMaqueta.Empleado, EmpleadoAdapter.Holder> {

    public interface AlElegirAccion {
        void onAccion(DatosMaqueta.Empleado empleado, int accionId);
    }

    private final AlElegirAccion alElegirAccion;

    public EmpleadoAdapter(AlElegirAccion alElegirAccion) {
        super(DIFF);
        this.alElegirAccion = alElegirAccion;
    }

    private static final DiffUtil.ItemCallback<DatosMaqueta.Empleado> DIFF =
            new DiffUtil.ItemCallback<DatosMaqueta.Empleado>() {
                @Override
                public boolean areItemsTheSame(@NonNull DatosMaqueta.Empleado a,
                                               @NonNull DatosMaqueta.Empleado b) {
                    return a.identidad.equals(b.identidad);
                }

                @Override
                public boolean areContentsTheSame(@NonNull DatosMaqueta.Empleado a,
                                                  @NonNull DatosMaqueta.Empleado b) {
                    return a.activo == b.activo && a.rol.equals(b.rol);
                }
            };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_empleado, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(getItem(position), alElegirAccion);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final TextView iniciales;
        private final TextView nombre;
        private final TextView correo;
        private final TextView identidad;
        private final Chip rol;
        private final Chip estado;
        private final ImageButton opciones;

        Holder(@NonNull View itemView) {
            super(itemView);
            iniciales = itemView.findViewById(R.id.txt_iniciales_empleado);
            nombre = itemView.findViewById(R.id.txt_nombre_empleado);
            correo = itemView.findViewById(R.id.txt_correo_empleado);
            identidad = itemView.findViewById(R.id.txt_identidad_empleado);
            rol = itemView.findViewById(R.id.chip_rol_empleado);
            estado = itemView.findViewById(R.id.chip_estado_empleado);
            opciones = itemView.findViewById(R.id.btn_opciones_empleado);
        }

        void enlazar(DatosMaqueta.Empleado empleado, AlElegirAccion alElegirAccion) {
            nombre.setText(empleado.nombreCompleto());
            correo.setText(empleado.correo);
            identidad.setText(empleado.identidad);
            iniciales.setText(inicialesDe(empleado));
            rol.setText(empleado.rol);

            estado.setText(empleado.activo
                    ? R.string.empleados_activo : R.string.empleados_inactivo);
            int colorEstado = empleado.activo ? R.color.estado_listo : R.color.estado_entregado;
            estado.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), colorEstado)));
            estado.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));

            opciones.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(v.getContext(), v);
                menu.inflate(R.menu.menu_empleado);
                // La opción alterna según el estado actual del empleado.
                menu.getMenu().findItem(R.id.accion_activar_desactivar)
                        .setTitle(empleado.activo
                                ? R.string.accion_desactivar : R.string.accion_activar);
                menu.setOnMenuItemClickListener(item -> {
                    alElegirAccion.onAccion(empleado, item.getItemId());
                    return true;
                });
                menu.show();
            });
        }

        private String inicialesDe(DatosMaqueta.Empleado empleado) {
            char primera = empleado.nombres.isEmpty() ? '?' : empleado.nombres.charAt(0);
            char segunda = empleado.apellidos.isEmpty() ? ' ' : empleado.apellidos.charAt(0);
            return (String.valueOf(primera) + segunda).trim().toUpperCase();
        }
    }
}

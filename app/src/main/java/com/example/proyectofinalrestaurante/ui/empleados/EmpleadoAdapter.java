package com.example.proyectofinalrestaurante.ui.empleados;

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
import com.example.proyectofinalrestaurante.domain.ReglasEmpleado;
import com.example.proyectofinalrestaurante.domain.model.Empleado;
import com.google.android.material.chip.Chip;

/**
 * Lista de empleados con datos reales (Plan Fase 1d, E5).
 *
 * <p>Cada opción del menú ⋮ se muestra según {@link ReglasEmpleado}, que espeja el
 * trigger del servidor: un admin no puede tocar a otro admin, ni cambiarse su propio
 * rol, ni desactivarse. Si no queda ninguna opción disponible, el botón ⋮ desaparece.</p>
 */
public class EmpleadoAdapter extends ListAdapter<Empleado, EmpleadoAdapter.Holder> {

    public interface AlElegirAccion {
        void onAccion(Empleado empleado, int accionId);
    }

    private final AlElegirAccion alElegirAccion;
    @Nullable private final String rolActor;
    @Nullable private final String idAuthActor;

    public EmpleadoAdapter(AlElegirAccion alElegirAccion,
                           @Nullable String rolActor, @Nullable String idAuthActor) {
        super(DIFF);
        this.alElegirAccion = alElegirAccion;
        this.rolActor = rolActor;
        this.idAuthActor = idAuthActor;
    }

    private static final DiffUtil.ItemCallback<Empleado> DIFF =
            new DiffUtil.ItemCallback<Empleado>() {
                @Override
                public boolean areItemsTheSame(@NonNull Empleado a, @NonNull Empleado b) {
                    return a.getIdEmpleado() == b.getIdEmpleado();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Empleado a, @NonNull Empleado b) {
                    return a.isActivo() == b.isActivo()
                            && a.getRol().equals(b.getRol())
                            && a.nombreCompleto().equals(b.nombreCompleto())
                            && a.getCorreo().equals(b.getCorreo());
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
        holder.enlazar(getItem(position), alElegirAccion, rolActor, idAuthActor);
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

        void enlazar(Empleado empleado, AlElegirAccion alElegirAccion,
                     @Nullable String rolActor, @Nullable String idAuthActor) {
            nombre.setText(empleado.nombreCompleto());
            correo.setText(empleado.getCorreo());
            identidad.setText(empleado.getIdentidad());
            iniciales.setText(inicialesDe(empleado));
            rol.setText(empleado.getRol());

            estado.setText(empleado.isActivo()
                    ? R.string.empleados_activo : R.string.empleados_inactivo);
            int colorEstado = empleado.isActivo()
                    ? R.color.estado_listo : R.color.estado_entregado;
            estado.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), colorEstado)));
            estado.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));

            boolean puedeEditar = ReglasEmpleado.puedeEditar(rolActor, idAuthActor, empleado);
            boolean puedeCambiarRol = ReglasEmpleado.puedeCambiarRol(rolActor, idAuthActor, empleado);
            boolean puedeCambiarEstado =
                    ReglasEmpleado.puedeCambiarEstado(rolActor, idAuthActor, empleado);

            // Sin ninguna acción disponible el botón no tiene razón de existir: mostrar
            // un menú vacío sería peor que no mostrarlo.
            boolean hayAlgo = puedeEditar || puedeCambiarRol || puedeCambiarEstado;
            opciones.setVisibility(hayAlgo ? View.VISIBLE : View.GONE);
            if (!hayAlgo) {
                return;
            }

            opciones.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(v.getContext(), v);
                menu.inflate(R.menu.menu_empleado);
                menu.getMenu().findItem(R.id.accion_editar_empleado).setVisible(puedeEditar);
                menu.getMenu().findItem(R.id.accion_cambiar_rol).setVisible(puedeCambiarRol);
                menu.getMenu().findItem(R.id.accion_activar_desactivar)
                        .setTitle(empleado.isActivo()
                                ? R.string.accion_desactivar : R.string.accion_activar)
                        .setVisible(puedeCambiarEstado);
                menu.setOnMenuItemClickListener(item -> {
                    alElegirAccion.onAccion(empleado, item.getItemId());
                    return true;
                });
                menu.show();
            });
        }

        private String inicialesDe(Empleado empleado) {
            char primera = empleado.getNombres().isEmpty() ? '?' : empleado.getNombres().charAt(0);
            char segunda = empleado.getApellidos().isEmpty() ? ' ' : empleado.getApellidos().charAt(0);
            return (String.valueOf(primera) + segunda).trim().toUpperCase();
        }
    }
}

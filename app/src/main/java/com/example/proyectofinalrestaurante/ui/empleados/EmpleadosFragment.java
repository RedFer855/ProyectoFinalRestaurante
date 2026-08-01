package com.example.proyectofinalrestaurante.ui.empleados;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.core.SesionActual;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.Permisos;
import com.example.proyectofinalrestaurante.domain.model.Empleado;
import com.example.proyectofinalrestaurante.domain.model.NuevoEmpleado;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Módulo Empleados conectado a Supabase (Plan Fase 1d, E5).
 *
 * <p>Solo {@code admin} llega acá: para los demás roles el módulo ni aparece en el
 * menú lateral, y del lado del servidor la RLS bloquea la tabla aunque alguien
 * modifique el APK.</p>
 */
public class EmpleadosFragment extends Fragment implements FormularioEmpleadoDialog.AlGuardar {

    private EmpleadosViewModel viewModel;
    private EmpleadoAdapter adapter;
    private TextView vacio;
    private View progreso;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_empleados, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vacio = view.findViewById(R.id.txt_empleados_vacio);
        progreso = view.findViewById(R.id.progress_empleados);

        viewModel = new ViewModelProvider(this, new EmpleadosViewModelFactory())
                .get(EmpleadosViewModel.class);

        Sesion sesion = SesionActual.obtener();
        adapter = new EmpleadoAdapter(this::alElegirAccion,
                sesion == null ? null : sesion.getRol(),
                sesion == null ? null : sesion.getIdUsuario());

        RecyclerView lista = view.findViewById(R.id.lista_empleados);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adapter);

        TextInputEditText campo = view.findViewById(R.id.txt_buscar_empleado);
        campo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                viewModel.filtrar(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_agregar_empleado);
        VistaPorPermiso.aplicar(fab, Modulo.EMPLEADOS, Accion.CREAR);
        fab.setOnClickListener(v -> abrirFormulario(null));

        viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);

        if (savedInstanceState == null) {
            viewModel.cargar();
        }
    }

    private void render(EstadoEmpleados estado) {
        progreso.setVisibility(estado.isCargando() ? View.VISIBLE : View.GONE);
        adapter.submitList(estado.getEmpleados());

        if (estado.getError() != null) {
            vacio.setVisibility(View.VISIBLE);
            vacio.setText(estado.getError());
            mostrarReintentar(estado.getError());
        } else if (estado.isVacio()) {
            vacio.setVisibility(View.VISIBLE);
            vacio.setText(R.string.empleados_vacio);
        } else {
            vacio.setVisibility(View.GONE);
        }

        String exito = estado.getMensajeExito();
        if (exito != null) {
            Snackbar.make(requireView(), exito, Snackbar.LENGTH_SHORT).show();
            // Se marca como consumido para que no vuelva a aparecer al rotar (P-013).
            viewModel.onMensajeConsumido();
        }
    }

    private void mostrarReintentar(String mensaje) {
        Snackbar.make(requireView(), mensaje, Snackbar.LENGTH_LONG)
                .setAction(R.string.empleado_reintentar, v -> viewModel.cargar())
                .show();
    }

    private void alElegirAccion(Empleado empleado, int accionId) {
        if (accionId == R.id.accion_editar_empleado) {
            abrirFormulario(empleado);
        } else if (accionId == R.id.accion_cambiar_rol) {
            elegirRol(empleado);
        } else if (accionId == R.id.accion_activar_desactivar) {
            viewModel.cambiarEstado(empleado, !empleado.isActivo());
        }
    }

    private void abrirFormulario(@Nullable Empleado empleado) {
        FormularioEmpleadoDialog dialogo = empleado == null
                ? FormularioEmpleadoDialog.paraCrear()
                : FormularioEmpleadoDialog.paraEditar(empleado);
        dialogo.setAlGuardar(this);
        dialogo.show(getChildFragmentManager(), FormularioEmpleadoDialog.TAG);
    }

    /** El rol se cambia desde su propia opción, no desde el formulario de datos. */
    private void elegirRol(Empleado empleado) {
        String[] roles = {Permisos.ROL_ADMIN, Permisos.ROL_MESERO, Permisos.ROL_COCINA};
        int actual = 0;
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equalsIgnoreCase(empleado.getRol())) {
                actual = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.empleado_titulo_cambiar_rol, empleado.nombreCompleto()))
                .setSingleChoiceItems(roles, actual, (d, cual) -> {
                    d.dismiss();
                    if (!roles[cual].equalsIgnoreCase(empleado.getRol())) {
                        viewModel.cambiarRol(empleado, roles[cual]);
                    }
                })
                .setNegativeButton(R.string.empleado_cancelar, null)
                .show();
    }

    @Override
    public void onCrear(NuevoEmpleado nuevo) {
        viewModel.crear(nuevo);
    }

    @Override
    public void onEditar(Empleado editado) {
        viewModel.actualizarDatos(editado);
    }
}

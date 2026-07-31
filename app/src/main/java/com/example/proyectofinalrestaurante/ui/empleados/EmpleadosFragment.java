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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.ui.maqueta.DatosMaqueta;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Módulo Empleados (Plan Fase 1c, Entregable 5) — <b>el caso estrella de la demo</b>:
 * solo {@code admin} lo ve, y para mesero y cocina ni siquiera existe en el menú.
 *
 * <p>El FAB igual se filtra por permiso aunque el módulo ya sea exclusivo de admin: es
 * defensa en profundidad barata, y evita que el día que se agregue un rol nuevo con
 * lectura de empleados aparezca sin querer el botón de crear.</p>
 */
public class EmpleadosFragment extends Fragment {

    private EmpleadoAdapter adapter;
    private TextView vacio;
    private String busqueda = "";

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

        adapter = new EmpleadoAdapter((empleado, accionId) -> avisarMaqueta());
        RecyclerView lista = view.findViewById(R.id.lista_empleados);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adapter);

        TextInputEditText campo = view.findViewById(R.id.txt_buscar_empleado);
        campo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                busqueda = s.toString().trim().toLowerCase(Locale.ROOT);
                refrescar();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_agregar_empleado);
        VistaPorPermiso.aplicar(fab, Modulo.EMPLEADOS, Accion.CREAR);
        fab.setOnClickListener(v -> avisarMaqueta());

        refrescar();
    }

    private void refrescar() {
        List<DatosMaqueta.Empleado> filtrados = new ArrayList<>();
        for (DatosMaqueta.Empleado empleado : DatosMaqueta.empleados()) {
            if (busqueda.isEmpty() || coincide(empleado)) {
                filtrados.add(empleado);
            }
        }
        adapter.submitList(filtrados);
        vacio.setVisibility(filtrados.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean coincide(DatosMaqueta.Empleado empleado) {
        return empleado.nombreCompleto().toLowerCase(Locale.ROOT).contains(busqueda)
                || empleado.identidad.contains(busqueda)
                || empleado.correo.toLowerCase(Locale.ROOT).contains(busqueda)
                || empleado.rol.contains(busqueda);
    }

    private void avisarMaqueta() {
        View raiz = getView();
        if (raiz != null) {
            Snackbar.make(raiz, R.string.maqueta_sin_funcion, Snackbar.LENGTH_SHORT).show();
        }
    }
}

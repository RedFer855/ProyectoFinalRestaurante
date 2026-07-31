package com.example.proyectofinalrestaurante.ui.menu;

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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Módulo Menú (Plan Fase 1c, Entregable 4). La misma pantalla para los tres roles:
 * todos ven el catálogo, pero solo {@code admin} ve el botón de agregar y el menú ⋮.
 */
public class MenuFragment extends Fragment {

    private PlatilloAdapter adapter;
    private TextView vacio;
    private String filtroCategoria = null;
    private String textoBusqueda = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vacio = view.findViewById(R.id.txt_menu_vacio);

        adapter = new PlatilloAdapter(this::alElegirAccion);
        RecyclerView lista = view.findViewById(R.id.lista_platillos);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adapter);

        configurarFiltroCategorias(view.findViewById(R.id.grupo_categorias));
        configurarBusqueda(view.findViewById(R.id.txt_buscar_platillo));

        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_agregar_platillo);
        VistaPorPermiso.aplicar(fab, Modulo.MENU, Accion.CREAR);
        fab.setOnClickListener(v -> avisarMaqueta());

        refrescar();
    }

    private void configurarFiltroCategorias(ChipGroup grupo) {
        grupo.removeAllViews();
        grupo.addView(crearChip(getString(R.string.filtro_todos), null, true));
        for (DatosMaqueta.Categoria categoria : DatosMaqueta.categorias()) {
            grupo.addView(crearChip(categoria.nombre, categoria.nombre, false));
        }
    }

    private Chip crearChip(String etiqueta, @Nullable String valor, boolean seleccionado) {
        Chip chip = new Chip(requireContext());
        chip.setText(etiqueta);
        chip.setCheckable(true);
        chip.setChecked(seleccionado);
        chip.setMinHeight(getResources().getDimensionPixelSize(R.dimen.altura_minima_tactil));
        chip.setOnClickListener(v -> {
            filtroCategoria = valor;
            refrescar();
        });
        return chip;
    }

    private void configurarBusqueda(TextInputEditText campo) {
        campo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                textoBusqueda = s.toString().trim().toLowerCase(Locale.ROOT);
                refrescar();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void refrescar() {
        List<DatosMaqueta.Platillo> filtrados = new ArrayList<>();
        for (DatosMaqueta.Platillo platillo : DatosMaqueta.platillos()) {
            boolean coincideCategoria = filtroCategoria == null
                    || filtroCategoria.equals(platillo.categoria);
            boolean coincideBusqueda = textoBusqueda.isEmpty()
                    || platillo.nombre.toLowerCase(Locale.ROOT).contains(textoBusqueda);
            if (coincideCategoria && coincideBusqueda) {
                filtrados.add(platillo);
            }
        }
        adapter.submitList(filtrados);
        vacio.setVisibility(filtrados.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void alElegirAccion(DatosMaqueta.Platillo platillo, int accionId) {
        avisarMaqueta();
    }

    private void avisarMaqueta() {
        View raiz = getView();
        if (raiz != null) {
            Snackbar.make(raiz, R.string.maqueta_sin_funcion, Snackbar.LENGTH_SHORT).show();
        }
    }
}

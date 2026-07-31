package com.example.proyectofinalrestaurante.ui.clientes;

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
 * Módulo Clientes (Plan Fase 1c, Entregable 5). Cocina no accede.
 *
 * <p>El buscador acepta nombre o identidad porque así se usa en la práctica: al tomar
 * un pedido se pregunta la identidad para reusar el cliente si ya existe (ADR-006).</p>
 */
public class ClientesFragment extends Fragment {

    private ClienteAdapter adapter;
    private TextView vacio;
    private String busqueda = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_clientes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vacio = view.findViewById(R.id.txt_clientes_vacio);

        adapter = new ClienteAdapter((cliente, accionId) -> avisarMaqueta());
        RecyclerView lista = view.findViewById(R.id.lista_clientes);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adapter);

        TextInputEditText campo = view.findViewById(R.id.txt_buscar_cliente);
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

        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_agregar_cliente);
        VistaPorPermiso.aplicar(fab, Modulo.CLIENTES, Accion.CREAR);
        fab.setOnClickListener(v -> avisarMaqueta());

        refrescar();
    }

    private void refrescar() {
        List<DatosMaqueta.Cliente> filtrados = new ArrayList<>();
        for (DatosMaqueta.Cliente cliente : DatosMaqueta.clientes()) {
            if (busqueda.isEmpty() || coincide(cliente)) {
                filtrados.add(cliente);
            }
        }
        adapter.submitList(filtrados);
        vacio.setVisibility(filtrados.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean coincide(DatosMaqueta.Cliente cliente) {
        boolean porNombre = cliente.nombreCompleto().toLowerCase(Locale.ROOT).contains(busqueda);
        boolean porIdentidad = cliente.identidad != null && cliente.identidad.contains(busqueda);
        return porNombre || porIdentidad;
    }

    private void avisarMaqueta() {
        View raiz = getView();
        if (raiz != null) {
            Snackbar.make(raiz, R.string.maqueta_sin_funcion, Snackbar.LENGTH_SHORT).show();
        }
    }
}

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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Módulo Clientes (Fase 2d). Cocina no accede.
 *
 * <p>Ahora usa {@link ClientesViewModel} en vez de {@code DatosMaqueta}. El buscador
 * acepta nombre, apellido, identidad o teléfono porque así se usa en la práctica: al tomar
 * un pedido se pregunta la identidad para reusar el cliente si ya existe (ADR-006).</p>
 */
public class ClientesFragment extends Fragment {

    private ClientesViewModel viewModel;
    private ClienteAdapter adapter;
    private TextView vacio;
    private TextView errorSync;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_clientes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this,
                new ClientesViewModelFactory(requireActivity().getApplication()))
                .get(ClientesViewModel.class);

        vacio = view.findViewById(R.id.txt_clientes_vacio);
        errorSync = view.findViewById(R.id.txt_error_sync_clientes);

        adapter = new ClienteAdapter(this::onAccionCliente);
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
                viewModel.buscar(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_agregar_cliente);
        VistaPorPermiso.aplicar(fab, Modulo.CLIENTES, Accion.CREAR);
        fab.setOnClickListener(v -> mostrarSnackbar(R.string.maqueta_sin_funcion));

        viewModel.getEstado().observe(getViewLifecycleOwner(), this::repintar);

        viewModel.sincronizar();
    }

    private void repintar(EstadoClientes estado) {
        adapter.submitList(estado.getClientes());
        vacio.setVisibility(estado.isVacio() ? View.VISIBLE : View.GONE);
        errorSync.setVisibility(
                estado.getUltimoErrorSync() != null ? View.VISIBLE : View.GONE);
        if (estado.getUltimoErrorSync() != null) {
            errorSync.setText(estado.getUltimoErrorSync());
        }
        if (estado.getMensajeExito() != null) {
            mostrarSnackbar(estado.getMensajeExito());
            viewModel.onMensajeConsumido();
        }
    }

    private void onAccionCliente(Cliente cliente, int accionId) {
        if (accionId == R.id.accion_editar) {
            mostrarSnackbar(R.string.maqueta_sin_funcion);
        } else if (accionId == R.id.accion_eliminar) {
            mostrarSnackbar(R.string.maqueta_sin_funcion);
        }
    }

    private void mostrarSnackbar(int recurso) {
        View raiz = getView();
        if (raiz != null) {
            Snackbar.make(raiz, recurso, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void mostrarSnackbar(String mensaje) {
        View raiz = getView();
        if (raiz != null) {
            Snackbar.make(raiz, mensaje, Snackbar.LENGTH_SHORT).show();
        }
    }
}

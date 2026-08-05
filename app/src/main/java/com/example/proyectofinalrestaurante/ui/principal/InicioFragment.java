package com.example.proyectofinalrestaurante.ui.principal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.core.SesionActual;
import com.example.proyectofinalrestaurante.domain.model.Sesion;

import java.util.Calendar;

/**
 * Inicio con tarjetas de estado, ahora con datos reales (Plan Fase 3c, E9/E10).
 *
 * <p>El filtrado por permiso y el armado de las tarjetas viven en {@link InicioViewModel}
 * desde esta fase; el Fragment solo observa {@link EstadoInicio} y pinta la grilla con
 * {@link TarjetaInicioAdapter}.</p>
 */
public class InicioFragment extends Fragment {

    private InicioViewModel viewModel;
    private TarjetaInicioAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inicio, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Sesion sesion = SesionActual.obtener();
        String nombre = sesion == null ? "" : primerNombre(sesion.getNombre());
        ((TextView) view.findViewById(R.id.txt_saludo))
                .setText(getString(saludoSegunHora(), nombre));

        viewModel = new ViewModelProvider(this,
                new InicioViewModelFactory(requireActivity().getApplication()))
                .get(InicioViewModel.class);

        adapter = new TarjetaInicioAdapter(this::abrir);
        RecyclerView grilla = view.findViewById(R.id.grilla_tarjetas);
        int columnas = getResources().getInteger(R.integer.columnas_tarjetas_inicio);
        grilla.setLayoutManager(new GridLayoutManager(requireContext(), columnas));
        grilla.setNestedScrollingEnabled(false);
        grilla.setAdapter(adapter);

        viewModel.getEstado().observe(getViewLifecycleOwner(),
                estado -> adapter.submitList(estado.getTarjetas()));
    }

    private void abrir(@IdRes int menuId) {
        if (getActivity() instanceof NavegacionModulos) {
            ((NavegacionModulos) getActivity()).abrirModulo(menuId);
        }
    }

    /** Solo el primer nombre — "Buenas tardes, Fernando" lee mejor que el nombre completo. */
    private String primerNombre(String nombreCompleto) {
        if (nombreCompleto == null) {
            return "";
        }
        String limpio = nombreCompleto.trim();
        int espacio = limpio.indexOf(' ');
        return espacio > 0 ? limpio.substring(0, espacio) : limpio;
    }

    private int saludoSegunHora() {
        int hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hora < 12) {
            return R.string.inicio_buenos_dias;
        }
        return hora < 19 ? R.string.inicio_buenas_tardes : R.string.inicio_buenas_noches;
    }
}

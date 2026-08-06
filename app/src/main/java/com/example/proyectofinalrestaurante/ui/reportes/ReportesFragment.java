package com.example.proyectofinalrestaurante.ui.reportes;

import android.os.Bundle;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.RangoReporte;
import com.example.proyectofinalrestaurante.domain.model.ReporteVentas;
import com.example.proyectofinalrestaurante.ui.comun.FormateadorFecha;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/**
 * Módulo Reportes con datos reales (Plan Fase 3c, E8/E10). Solo admin.
 *
 * <p>El {@code ChipGroup} ahora tiene listener (antes era solo visual) y el rango vive en
 * {@link ReportesViewModel}: al recrear la vista, el chip se re-marca <b>desde el estado</b>
 * (§7.3 del plan), nunca al revés.</p>
 */
public class ReportesFragment extends Fragment {

    private ReportesViewModel viewModel;
    private ConteoPlatilloAdapter adapterPlatillos;
    private DesempenoMeseroAdapter adapterMeseros;
    private ChipGroup grupoRango;
    private SwipeRefreshLayout refresco;
    private TextView estadoTexto;
    private TextView ventasValor;
    private TextView pedidosValor;
    private TextView ticketValor;
    private View contenedorDatos;
    private View contenedorVacio;
    private TextView textoVacio;
    private boolean marcandoChipDesdeEstado = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reportes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this,
                new ReportesViewModelFactory(requireActivity().getApplication()))
                .get(ReportesViewModel.class);

        grupoRango = view.findViewById(R.id.grupo_rango);
        refresco = view.findViewById(R.id.refresco_reportes);
        estadoTexto = view.findViewById(R.id.txt_estado_reportes);
        ventasValor = view.findViewById(R.id.txt_ventas_valor);
        pedidosValor = view.findViewById(R.id.txt_pedidos_valor);
        ticketValor = view.findViewById(R.id.txt_ticket_valor);
        contenedorDatos = view.findViewById(R.id.contenedor_datos_reportes);
        contenedorVacio = view.findViewById(R.id.contenedor_vacio_reportes);
        textoVacio = view.findViewById(R.id.txt_reportes_vacio);

        construirChipsDeRango();
        refresco.setOnRefreshListener(() -> viewModel.pullToRefresh());
        view.findViewById(R.id.btn_reintentar_reportes).setOnClickListener(
                v -> viewModel.pullToRefresh());

        adapterPlatillos = new ConteoPlatilloAdapter();
        RecyclerView listaPlatillos = view.findViewById(R.id.lista_mas_pedidos);
        listaPlatillos.setLayoutManager(new LinearLayoutManager(requireContext()));
        listaPlatillos.setNestedScrollingEnabled(false);
        listaPlatillos.setAdapter(adapterPlatillos);

        adapterMeseros = new DesempenoMeseroAdapter();
        RecyclerView listaMeseros = view.findViewById(R.id.lista_desempeno);
        listaMeseros.setLayoutManager(new LinearLayoutManager(requireContext()));
        listaMeseros.setNestedScrollingEnabled(false);
        listaMeseros.setAdapter(adapterMeseros);

        viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
    }

    private void construirChipsDeRango() {
        grupoRango.removeAllViews();
        for (RangoReporte rango : RangoReporte.values()) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(RangoReporteUi.etiqueta(rango));
            chip.setTag(rango);
            chip.setCheckable(true);
            chip.setMinHeight(getResources().getDimensionPixelSize(R.dimen.altura_minima_tactil));
            chip.setOnClickListener(v -> {
                if (!marcandoChipDesdeEstado) {
                    viewModel.cambiarRango(rango);
                }
            });
            grupoRango.addView(chip);
        }
    }

    private void render(EstadoReportes estado) {
        marcarChip(estado.getRango());
        refresco.setRefreshing(estado.isSincronizando());

        ReporteVentas reporte = estado.getReporte();
        // Tres estados excluyentes: cifras, "no hubo ventas" y "no se descargó". Los dos
        // últimos comparten el contenedor vacío y solo cambian el texto; el de "no hubo
        // ventas" conserva la franja "Datos al …" porque ahí el dato sí bajó y es real.
        boolean hayCifras = reporte != null && !estado.isSinVentas();
        contenedorDatos.setVisibility(hayCifras ? View.VISIBLE : View.GONE);
        contenedorVacio.setVisibility(
                estado.isVacio() || estado.isSinVentas() ? View.VISIBLE : View.GONE);
        estadoTexto.setVisibility(reporte != null ? View.VISIBLE : View.GONE);
        textoVacio.setText(estado.isSinVentas()
                ? getString(R.string.reportes_sin_ventas)
                : getString(R.string.reportes_vacio_rango));

        if (reporte == null) {
            return;
        }

        String fecha = FormateadorFecha.fechaHoraCorta(reporte.getGeneradoEnEpochMillis());
        estadoTexto.setText(estado.getUltimoErrorSync() == null
                ? getString(R.string.reportes_datos_al, fecha)
                : getString(R.string.reportes_datos_al_con_error, fecha, estado.getUltimoErrorSync()));

        ventasValor.setText(getString(R.string.formato_lempiras, reporte.getTotalVentas()));
        pedidosValor.setText(String.valueOf(reporte.getCantidadPedidos()));
        ticketValor.setText(getString(R.string.formato_lempiras, reporte.getTicketPromedio()));

        adapterPlatillos.submitList(reporte.getTopPlatillos());
        adapterMeseros.submitList(reporte.getDesempenoMeseros());
    }

    /** Re-marca el chip desde el estado (§7.3): nunca al revés. */
    private void marcarChip(RangoReporte rango) {
        marcandoChipDesdeEstado = true;
        for (int i = 0; i < grupoRango.getChildCount(); i++) {
            Chip chip = (Chip) grupoRango.getChildAt(i);
            chip.setChecked(chip.getTag() == rango);
        }
        marcandoChipDesdeEstado = false;
    }
}

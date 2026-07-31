package com.example.proyectofinalrestaurante.ui.reportes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.ui.maqueta.DatosMaqueta;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/**
 * Módulo Reportes (Plan Fase 1c, Entregable 5). Solo admin.
 *
 * <p>Pantalla de lectura pura: no tiene acciones que filtrar por permiso, porque el
 * módulo entero ya es exclusivo de admin. Los números salen de {@code DatosMaqueta} y
 * no cambian con el rango — el selector es visual, como el resto de la maqueta.</p>
 */
public class ReportesFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reportes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        configurarRangos(view.findViewById(R.id.grupo_rango));

        ((TextView) view.findViewById(R.id.txt_ventas_valor))
                .setText(getString(R.string.formato_lempiras, DatosMaqueta.VENTAS_DEL_DIA));
        ((TextView) view.findViewById(R.id.txt_pedidos_valor))
                .setText(String.valueOf(DatosMaqueta.PEDIDOS_DEL_DIA));
        ((TextView) view.findViewById(R.id.txt_ticket_valor))
                .setText(getString(R.string.formato_lempiras, DatosMaqueta.TICKET_PROMEDIO));

        llenarMasPedidos(view.findViewById(R.id.contenedor_mas_pedidos));
        llenarDesempeno(view.findViewById(R.id.contenedor_desempeno));
    }

    private void configurarRangos(ChipGroup grupo) {
        grupo.removeAllViews();
        int[] etiquetas = {
                R.string.reportes_rango_hoy,
                R.string.reportes_rango_semana,
                R.string.reportes_rango_mes
        };
        for (int i = 0; i < etiquetas.length; i++) {
            Chip chip = new Chip(requireContext());
            chip.setText(etiquetas[i]);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            chip.setMinHeight(getResources().getDimensionPixelSize(R.dimen.altura_minima_tactil));
            grupo.addView(chip);
        }
    }

    private void llenarMasPedidos(LinearLayout contenedor) {
        contenedor.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (DatosMaqueta.ConteoSimple conteo : DatosMaqueta.platillosMasPedidos()) {
            View fila = inflater.inflate(R.layout.item_conteo, contenedor, false);
            ((TextView) fila.findViewById(R.id.txt_conteo_etiqueta)).setText(conteo.etiqueta);
            ((TextView) fila.findViewById(R.id.txt_conteo_valor))
                    .setText(String.valueOf(conteo.cantidad));
            contenedor.addView(fila);
        }
    }

    private void llenarDesempeno(LinearLayout contenedor) {
        contenedor.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (DatosMaqueta.DesempenoMesero desempeno : DatosMaqueta.desempenoMeseros()) {
            View fila = inflater.inflate(R.layout.item_desempeno, contenedor, false);
            ((TextView) fila.findViewById(R.id.txt_desempeno_nombre)).setText(desempeno.nombre);
            ((TextView) fila.findViewById(R.id.txt_desempeno_pedidos))
                    .setText(getString(R.string.reportes_pedidos_atendidos, desempeno.pedidos));
            ((TextView) fila.findViewById(R.id.txt_desempeno_total))
                    .setText(getString(R.string.formato_lempiras, desempeno.total));
            contenedor.addView(fila);
        }
    }
}

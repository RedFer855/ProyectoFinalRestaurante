package com.example.proyectofinalrestaurante.ui.principal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.core.SesionActual;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.ui.maqueta.DatosMaqueta;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.example.proyectofinalrestaurante.domain.model.Sesion;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Inicio con tarjetas de estado (Plan Fase 1c, Entregable 6).
 *
 * <p><b>Cada tarjeta muestra un dato, no es un atajo.</b> Una tarjeta que solo dijera
 * "Pedidos" duplicaría el menú lateral; "4 pedidos pendientes" dice algo que el menú
 * no puede decir. Igual son tocables y llevan a su módulo.</p>
 *
 * <p>La grilla se arma desde {@code Permisos}: agregar un rol nuevo no obliga a tocar
 * este archivo ni el layout, solo la matriz.</p>
 */
public class InicioFragment extends Fragment {

    /** Una tarjeta y el permiso que la habilita. */
    private static final class Tarjeta {
        @DrawableRes final int icono;
        final String valor;
        @StringRes final int etiqueta;
        @IdRes final int menuId;
        final Modulo modulo;
        final Accion accion;

        Tarjeta(@DrawableRes int icono, String valor, @StringRes int etiqueta,
                @IdRes int menuId, Modulo modulo, Accion accion) {
            this.icono = icono;
            this.valor = valor;
            this.etiqueta = etiqueta;
            this.menuId = menuId;
            this.modulo = modulo;
            this.accion = accion;
        }
    }

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

        construirTarjetas(view.findViewById(R.id.grilla_tarjetas));
    }

    /**
     * Filtra las tarjetas por permiso y las coloca en la grilla. El permiso de cada una
     * no es arbitrario: es el que refleja a quién le sirve el dato. "Pedidos pendientes"
     * exige {@code CREAR} porque le importa a quien toma pedidos (admin y mesero); "En
     * preparación" exige {@code CAMBIAR_ESTADO}, que es cocina y admin.
     */
    private void construirTarjetas(GridLayout grilla) {
        int columnas = getResources().getInteger(R.integer.columnas_tarjetas_inicio);
        grilla.setColumnCount(columnas);
        grilla.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int margen = getResources().getDimensionPixelSize(R.dimen.espaciado_minimo) / 2;

        for (Tarjeta tarjeta : tarjetasVisibles()) {
            View vista = inflater.inflate(R.layout.item_tarjeta_inicio, grilla, false);
            ((ImageView) vista.findViewById(R.id.img_icono_tarjeta)).setImageResource(tarjeta.icono);
            ((TextView) vista.findViewById(R.id.txt_valor_tarjeta)).setText(tarjeta.valor);
            ((TextView) vista.findViewById(R.id.txt_etiqueta_tarjeta)).setText(tarjeta.etiqueta);
            vista.setOnClickListener(v -> abrir(tarjeta.menuId));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            params.setMargins(margen, margen, margen, margen);
            vista.setLayoutParams(params);

            grilla.addView(vista);
        }
    }

    private List<Tarjeta> tarjetasVisibles() {
        List<Tarjeta> todas = new ArrayList<>();

        todas.add(new Tarjeta(R.drawable.ic_pedidos,
                String.valueOf(DatosMaqueta.PEDIDOS_PENDIENTES),
                R.string.inicio_pedidos_pendientes,
                R.id.nav_pedidos, Modulo.PEDIDOS, Accion.CREAR));

        todas.add(new Tarjeta(R.drawable.ic_pedidos,
                String.valueOf(DatosMaqueta.PEDIDOS_EN_PREPARACION),
                R.string.inicio_pedidos_preparacion,
                R.id.nav_pedidos, Modulo.PEDIDOS, Accion.CAMBIAR_ESTADO));

        todas.add(new Tarjeta(R.drawable.ic_mesas,
                getString(R.string.inicio_mesas_valor,
                        DatosMaqueta.MESAS_OCUPADAS, DatosMaqueta.MESAS_TOTALES),
                R.string.inicio_mesas_ocupadas,
                R.id.nav_mesas, Modulo.MESAS, Accion.VER));

        todas.add(new Tarjeta(R.drawable.ic_clientes,
                String.valueOf(DatosMaqueta.CLIENTES_REGISTRADOS),
                R.string.inicio_clientes_registrados,
                R.id.nav_clientes, Modulo.CLIENTES, Accion.VER));

        todas.add(new Tarjeta(R.drawable.ic_menu,
                String.valueOf(DatosMaqueta.PLATILLOS_ACTIVOS),
                R.string.inicio_platillos_activos,
                R.id.nav_menu, Modulo.MENU, Accion.CREAR));

        todas.add(new Tarjeta(R.drawable.ic_reportes,
                getString(R.string.formato_lempiras, DatosMaqueta.VENTAS_DEL_DIA),
                R.string.inicio_ventas_hoy,
                R.id.nav_reportes, Modulo.REPORTES, Accion.VER));

        todas.add(new Tarjeta(R.drawable.ic_empleados,
                String.valueOf(DatosMaqueta.EMPLEADOS_ACTIVOS),
                R.string.inicio_empleados_activos,
                R.id.nav_empleados, Modulo.EMPLEADOS, Accion.VER));

        List<Tarjeta> visibles = new ArrayList<>();
        for (Tarjeta tarjeta : todas) {
            if (VistaPorPermiso.puede(tarjeta.modulo, tarjeta.accion)) {
                visibles.add(tarjeta);
            }
        }
        return visibles;
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

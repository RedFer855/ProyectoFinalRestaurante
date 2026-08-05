package com.example.proyectofinalrestaurante.ui.comun;

import android.app.Dialog;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.example.proyectofinalrestaurante.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Base de las hojas modales inferiores de la app, con el aspecto del diseño aprobado
 * ("Restaurant App v2.dc.html": esquinas superiores de 26dp y tope al 85% del alto).
 *
 * <p>Existe por un comportamiento que sorprende: una {@code BottomSheetDialogFragment}
 * <b>abre a medias</b>. Arranca en {@code STATE_COLLAPSED}, mostrando solo la altura de
 * asomo, así que en un formulario largo los botones de abajo quedan fuera de la pantalla
 * y parece que la hoja "no sube lo suficiente". Hay que pedir {@code STATE_EXPANDED}
 * explícitamente.</p>
 *
 * <p>Y no alcanza con expandir: sin {@code setSkipCollapsed(true)}, arrastrar la hoja
 * hacia abajo la devuelve al estado colapsado en vez de cerrarla, y el usuario vuelve a
 * quedarse sin ver los botones.</p>
 *
 * <p>El tope del 85% no es solo estética: deja ver una franja del fondo, que es la pista
 * de que se puede tocar afuera para cerrar. Una hoja a pantalla completa se confunde con
 * una pantalla nueva.</p>
 */
public abstract class HojaModal extends BottomSheetDialogFragment {

    /** Proporción máxima del alto de pantalla que ocupa la hoja (85%, del diseño). */
    private static final float ALTO_MAXIMO = 0.85f;

    /** Aplica las esquinas superiores de 26dp y el fondo del diseño. */
    @Override
    public int getTheme() {
        return R.style.Theme_App_HojaModal;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialogo = getDialog();
        if (!(dialogo instanceof BottomSheetDialog)) {
            return;
        }

        BottomSheetBehavior<FrameLayout> comportamiento =
                ((BottomSheetDialog) dialogo).getBehavior();
        comportamiento.setState(BottomSheetBehavior.STATE_EXPANDED);
        comportamiento.setSkipCollapsed(true);
        comportamiento.setMaxHeight(altoMaximoEnPx());

        ajustarAlTeclado(dialogo);
    }

    /**
     * Hace que la hoja se encoja cuando aparece el teclado, en vez de quedar tapada.
     *
     * <p>{@code SOFT_INPUT_ADJUST_RESIZE} está deprecado desde API 30, pero se usa igual:
     * el reemplazo oficial son callbacks de {@code WindowInsets}, que para un formulario
     * en una hoja modal es bastante más código para el mismo resultado. La hoja vive en su
     * propia ventana, así que no hereda el {@code windowSoftInputMode} de la Activity.</p>
     */
    @SuppressWarnings("deprecation")
    private void ajustarAlTeclado(@NonNull Dialog dialogo) {
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private int altoMaximoEnPx() {
        return Math.round(getResources().getDisplayMetrics().heightPixels * ALTO_MAXIMO);
    }

    /**
     * Atajo para el patrón repetido de cablear un botón que solo cierra la hoja.
     *
     * @param raiz  vista donde buscar
     * @param idBoton id del botón; si no existe en ese layout, no hace nada
     */
    protected void cerrarAlTocar(@NonNull View raiz, int idBoton) {
        View boton = raiz.findViewById(idBoton);
        if (boton != null) {
            boton.setOnClickListener(v -> dismiss());
        }
    }
}

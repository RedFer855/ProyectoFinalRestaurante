package com.example.proyectofinalrestaurante.ui.debug;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.proyectofinalrestaurante.BuildConfig;
import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Permisos;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Selector de rol para demostrar la app (Plan Fase 1c, Entregable 7).
 *
 * <p>Permite ver la interfaz como admin, mesero o cocina sin cerrar sesión y volver a
 * entrar tres veces — que es lo que haría falta para mostrar el sistema de permisos.</p>
 *
 * <p><b>Solo en builds de debug.</b> {@link #estaDisponible()} devuelve
 * {@code BuildConfig.DEBUG}, y {@code MainActivity} no infla el menú si es falso.</p>
 *
 * <p>⚠ Aunque alguien lo forzara en una build de release, <b>cambiar el rol acá no da
 * acceso a nada</b>: las policies RLS de Postgres leen el rol desde la base usando el JWT
 * del usuario, no lo que afirme el cliente. Eso es justamente lo que hace honesta a la
 * demostración — se puede mostrar el cambio visual y después probar por {@code curl} que
 * el servidor sigue negando.</p>
 */
public final class SelectorRolDebug {

    /** Rol elegido en el diálogo. */
    public interface AlElegirRol {
        void onRolElegido(String rol);
    }

    private static final String[] ROLES = {
            Permisos.ROL_ADMIN,
            Permisos.ROL_MESERO,
            Permisos.ROL_COCINA
    };

    private SelectorRolDebug() {
    }

    /** {@code true} solo en builds de debug. */
    public static boolean estaDisponible() {
        return BuildConfig.DEBUG;
    }

    /**
     * Muestra el diálogo con los tres roles, marcando el actual.
     * No hace nada si la build no es de debug.
     */
    public static void mostrar(@NonNull Context contexto, String rolActual,
                               @NonNull AlElegirRol alElegirRol) {
        if (!estaDisponible()) {
            return;
        }

        int seleccionado = indiceDe(rolActual);
        new MaterialAlertDialogBuilder(contexto)
                .setTitle(R.string.debug_titulo_dialogo)
                .setMessage(R.string.debug_aviso_rls)
                .setSingleChoiceItems(ROLES, seleccionado, (dialogo, cual) -> {
                    dialogo.dismiss();
                    alElegirRol.onRolElegido(ROLES[cual]);
                })
                .show();
    }

    private static int indiceDe(String rol) {
        for (int i = 0; i < ROLES.length; i++) {
            if (ROLES[i].equalsIgnoreCase(rol)) {
                return i;
            }
        }
        return -1;
    }
}

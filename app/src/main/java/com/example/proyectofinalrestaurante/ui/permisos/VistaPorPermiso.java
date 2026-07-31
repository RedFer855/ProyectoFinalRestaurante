package com.example.proyectofinalrestaurante.ui.permisos;

import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.core.SesionActual;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.Permisos;
import com.example.proyectofinalrestaurante.domain.model.Sesion;

/**
 * Aplica la matriz de {@link Permisos} sobre vistas (Plan Fase 1c, Entregable 3).
 *
 * <p>Existe para que <b>ningún Fragment compare strings de rol a mano</b>. Un
 * {@code if (rol.equals("admin"))} repetido en diez pantallas es imposible de auditar
 * y se desincroniza en cuanto cambia la matriz; acá el permiso se consulta en un solo
 * lugar.</p>
 *
 * <pre>
 * VistaPorPermiso.aplicar(fabAgregar, Modulo.EMPLEADOS, Accion.CREAR);
 * </pre>
 *
 * <p>Se usa {@link View#GONE} y no {@code INVISIBLE}: un control que el rol no tiene no
 * debe ocupar espacio ni dejar un hueco que delate su existencia.</p>
 *
 * <p>⚠ Esto es experiencia de usuario, <b>no seguridad</b> — ver el aviso en
 * {@link Permisos}.</p>
 */
public final class VistaPorPermiso {

    private VistaPorPermiso() {
    }

    /** Rol de la sesión activa, o {@code null} si no hay sesión. */
    @Nullable
    public static String rolActual() {
        Sesion sesion = SesionActual.obtener();
        return sesion == null ? null : sesion.getRol();
    }

    /** ¿El usuario logueado puede hacer esta acción? */
    public static boolean puede(Modulo modulo, Accion accion) {
        return Permisos.puede(rolActual(), modulo, accion);
    }

    /** Muestra u oculta la vista según el permiso del usuario logueado. */
    public static void aplicar(@Nullable View vista, Modulo modulo, Accion accion) {
        aplicar(vista, rolActual(), modulo, accion);
    }

    /**
     * Variante con el rol explícito. Sirve para previsualizar otro rol sin cambiar de
     * sesión — lo usa el selector de rol de debug (Entregable 7).
     */
    public static void aplicar(@Nullable View vista, @Nullable String rol,
                               Modulo modulo, Accion accion) {
        if (vista == null) {
            return;
        }
        vista.setVisibility(Permisos.puede(rol, modulo, accion) ? View.VISIBLE : View.GONE);
    }

    /** Misma idea para las opciones de un {@code PopupMenu} (editar, eliminar…). */
    public static void aplicar(@Nullable MenuItem item, Modulo modulo, Accion accion) {
        if (item != null) {
            item.setVisible(puede(modulo, accion));
        }
    }
}

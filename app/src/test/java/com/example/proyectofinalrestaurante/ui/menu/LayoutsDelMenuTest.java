package com.example.proyectofinalrestaurante.ui.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import com.example.proyectofinalrestaurante.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Comprueba que los layouts del módulo Menú se inflan de verdad (rediseño 2026-08-04).
 *
 * <p>Existe porque el compilador <b>no</b> ve esta clase de error: una referencia a un
 * estilo inexistente, un atributo que el widget no soporta o un drawable mal armado
 * compilan sin quejarse y revientan al inflar, ya con la app en la mano. Estos layouts
 * estrenaron {@code values/styles.xml} y {@code res/color/}, así que hay bastante
 * referencia nueva que puede quedar colgada en un refactor futuro.</p>
 *
 * <p>Se infla bajo el tema de la app: sin él, cualquier {@code ?attr/} del tema Material
 * no resuelve y el fallo sería del test, no del layout.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LayoutsDelMenuTest {

    private View inflar(int layout) {
        // RuntimeEnvironment y no ApplicationProvider: androidx.test.core no está entre
        // las dependencias de test del proyecto, y el resto de los tests usa esta vía.
        Context contexto = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                R.style.Theme_ProyectoFinalRestaurante);
        return LayoutInflater.from(contexto).inflate(layout, null, false);
    }

    @Test
    public void la_pantalla_del_menu_se_infla() {
        View vista = inflar(R.layout.fragment_menu);
        assertNotNull(vista.findViewById(R.id.txt_buscar_platillo));
        assertNotNull(vista.findViewById(R.id.grupo_categorias));
        assertNotNull(vista.findViewById(R.id.fab_agregar_platillo));
        assertNotNull(vista.findViewById(R.id.btn_categorias));
    }

    @Test
    public void la_tarjeta_de_platillo_se_infla_con_todos_sus_ids() {
        View vista = inflar(R.layout.item_platillo);
        // Los ids que PlatilloAdapter busca por findViewById: si un rediseño renombra
        // alguno, el adapter fallaría con NullPointerException recién en runtime.
        assertNotNull(vista.findViewById(R.id.img_platillo));
        assertNotNull(vista.findViewById(R.id.txt_nombre_platillo));
        assertNotNull(vista.findViewById(R.id.txt_categoria_platillo));
        assertNotNull(vista.findViewById(R.id.txt_estado_platillo));
        assertNotNull(vista.findViewById(R.id.txt_sync_platillo));
        assertNotNull(vista.findViewById(R.id.txt_descripcion_platillo));
        assertNotNull(vista.findViewById(R.id.txt_precio_platillo));
        assertNotNull(vista.findViewById(R.id.grupo_acciones_platillo));
        assertNotNull(vista.findViewById(R.id.btn_editar_platillo));
        assertNotNull(vista.findViewById(R.id.btn_estado_platillo));
    }

    @Test
    public void el_chip_de_filtro_se_infla_con_su_estilo() {
        // Es el layout de una sola etiqueta que existe justamente para que el Chip tome
        // Widget.App.Chip.Filtro — un `new Chip(context)` no aplicaría el estilo.
        assertNotNull(inflar(R.layout.item_chip_filtro));
    }

    @Test
    public void la_hoja_del_formulario_de_platillo_se_infla() {
        View vista = inflar(R.layout.dialog_platillo);
        assertNotNull(vista.findViewById(R.id.txt_titulo_platillo));
        assertNotNull(vista.findViewById(R.id.txt_platillo_nombre));
        assertNotNull(vista.findViewById(R.id.txt_platillo_categoria));
        assertNotNull(vista.findViewById(R.id.txt_platillo_precio));
        assertNotNull(vista.findViewById(R.id.txt_platillo_descripcion));
        assertNotNull(vista.findViewById(R.id.img_platillo_previsualizacion));
        assertNotNull(vista.findViewById(R.id.btn_guardar_platillo));
        assertNotNull(vista.findViewById(R.id.btn_cancelar_platillo));
        assertNotNull(vista.findViewById(R.id.btn_cerrar_platillo));
        // Estado vacío del hueco de la foto: la caja tocable y su aviso.
        assertNotNull(vista.findViewById(R.id.caja_foto_platillo));
        assertNotNull(vista.findViewById(R.id.txt_aviso_foto));
    }

    /**
     * El hueco de la foto arranca en {@code CENTER_INSIDE}, no en {@code CENTER_CROP}.
     *
     * <p>Es la regresión concreta que se arregló: con {@code CENTER_CROP}, el ícono
     * cuadrado del placeholder se escalaba hasta cubrir una caja mucho más ancha que
     * alta y solo se veía una franja horizontal del medio.</p>
     */
    @Test
    public void el_hueco_de_la_foto_no_recorta_el_placeholder() {
        ImageView previsualizacion = inflar(R.layout.dialog_platillo)
                .findViewById(R.id.img_platillo_previsualizacion);
        assertEquals(ImageView.ScaleType.CENTER_INSIDE, previsualizacion.getScaleType());
    }

    @Test
    public void la_hoja_de_categorias_y_su_fila_se_inflan() {
        View hoja = inflar(R.layout.dialog_categorias);
        assertNotNull(hoja.findViewById(R.id.lista_categorias));
        assertNotNull(hoja.findViewById(R.id.txt_categoria_nueva));
        assertNotNull(hoja.findViewById(R.id.btn_agregar_categoria));
        assertNotNull(hoja.findViewById(R.id.btn_cerrar_categorias));

        View fila = inflar(R.layout.item_categoria);
        assertNotNull(fila.findViewById(R.id.txt_nombre_categoria));
        assertNotNull(fila.findViewById(R.id.txt_conteo_categoria));
        assertNotNull(fila.findViewById(R.id.btn_opciones_categoria));

        // El renombrado dejó de reusar el layout de la lista escondiéndole vistas.
        assertNotNull(inflar(R.layout.dialog_categoria_nombre)
                .findViewById(R.id.txt_categoria_nombre));
    }
}

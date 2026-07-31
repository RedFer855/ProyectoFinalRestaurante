package com.example.proyectofinalrestaurante.ui.principal;

import androidx.annotation.IdRes;

/**
 * Contrato para que un Fragment pida abrir otro módulo sin conocer a {@code MainActivity}.
 * Lo usan las tarjetas del inicio, que además de mostrar un dato llevan a su módulo.
 */
public interface NavegacionModulos {

    /** Abre el módulo del ítem de menú indicado, actualizando título y menú lateral. */
    void abrirModulo(@IdRes int menuId);
}

package com.example.proyectofinalrestaurante;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.proyectofinalrestaurante.core.SesionActual;
import com.example.proyectofinalrestaurante.domain.VisibilidadMenu;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.example.proyectofinalrestaurante.ui.login.LoginActivity;
import com.google.android.material.navigation.NavigationView;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Pantalla principal (post-login): menú hamburguesa con ítems filtrados por rol
 * (VisibilidadMenu). Los módulos todavía no construidos muestran un placeholder
 * "Próximamente" — el home real (menú/pedidos/mesas) llega en fases siguientes
 * (ver contexto/40 - Proyecto Restaurante/Roadmap de Fases.md).
 */
public class MainActivity extends AppCompatActivity {

    private static final Map<VisibilidadMenu.Item, Integer> ITEM_IDS =
            new EnumMap<>(VisibilidadMenu.Item.class);

    static {
        ITEM_IDS.put(VisibilidadMenu.Item.INICIO, R.id.nav_inicio);
        ITEM_IDS.put(VisibilidadMenu.Item.PEDIDOS, R.id.nav_pedidos);
        ITEM_IDS.put(VisibilidadMenu.Item.MESAS, R.id.nav_mesas);
        ITEM_IDS.put(VisibilidadMenu.Item.MENU, R.id.nav_menu);
        ITEM_IDS.put(VisibilidadMenu.Item.CLIENTES, R.id.nav_clientes);
        ITEM_IDS.put(VisibilidadMenu.Item.EMPLEADOS, R.id.nav_empleados);
        ITEM_IDS.put(VisibilidadMenu.Item.REPORTES, R.id.nav_reportes);
    }

    private DrawerLayout drawer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        aplicarInsets();

        Sesion sesion = SesionActual.obtener();
        if (sesion == null) {
            // No hay sesión activa (app recién abierta) → volver al login.
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.main_drawer);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.menu_abrir, R.string.menu_cerrar);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navView = findViewById(R.id.nav_view);
        configurarCabecera(navView, sesion);
        filtrarMenu(navView, sesion.getRol());

        navView.setNavigationItemSelectedListener(this::alSeleccionarItem);
        navView.setCheckedItem(R.id.nav_inicio);
        mostrarPlaceholder(toolbar, R.id.nav_inicio);
    }

    private void aplicarInsets() {
        View contenido = findViewById(R.id.main_content);
        ViewCompat.setOnApplyWindowInsetsListener(contenido, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, v.getPaddingBottom());
            return insets;
        });
        View header = ((NavigationView) findViewById(R.id.nav_view)).getHeaderView(0);
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    private void configurarCabecera(NavigationView navView, Sesion sesion) {
        View header = navView.getHeaderView(0);
        ((TextView) header.findViewById(R.id.txt_nombre_header)).setText(sesion.getNombre());
        ((TextView) header.findViewById(R.id.txt_rol_header)).setText(sesion.getRol());
        ((TextView) header.findViewById(R.id.txt_iniciales))
                .setText(iniciales(sesion.getNombre()));
    }

    private String iniciales(String nombre) {
        if (nombre == null) {
            nombre = "";
        }
        String[] partes = nombre.trim().split("\\s+");
        if (partes.length == 1) {
            if (partes[0].isEmpty()) {
                return getString(R.string.nav_header_iniciales);
            }
            return String.valueOf(Character.toUpperCase(partes[0].charAt(0)));
        }
        String primera = String.valueOf(Character.toUpperCase(partes[0].charAt(0)));
        String ultima = String.valueOf(Character.toUpperCase(partes[partes.length - 1].charAt(0)));
        return primera + ultima;
    }

    /** Oculta los ítems que el rol no puede ver (VisibilidadMenu). */
    private void filtrarMenu(NavigationView navView, String rol) {
        Set<VisibilidadMenu.Item> visibles = VisibilidadMenu.itemsVisibles(rol);
        Menu menu = navView.getMenu();
        for (VisibilidadMenu.Item item : VisibilidadMenu.Item.values()) {
            menu.findItem(ITEM_IDS.get(item)).setVisible(visibles.contains(item));
        }
    }

    private boolean alSeleccionarItem(MenuItem item) {
        if (item.getItemId() == R.id.nav_cerrar_sesion) {
            cerrarSesion();
            return true;
        }
        mostrarPlaceholder(findViewById(R.id.toolbar), item.getItemId());
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void mostrarPlaceholder(Toolbar toolbar, int menuId) {
        NavigationView navView = findViewById(R.id.nav_view);
        MenuItem item = navView.getMenu().findItem(menuId);
        toolbar.setTitle(item.getTitle());
        ((TextView) findViewById(R.id.txt_placeholder_descripcion))
                .setText(getString(R.string.main_modulo_construccion, item.getTitle()));
    }

    private void cerrarSesion() {
        SesionActual.limpiar();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

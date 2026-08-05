package com.example.proyectofinalrestaurante;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.example.proyectofinalrestaurante.core.SesionActual;
import com.example.proyectofinalrestaurante.data.sync.SyncScheduler;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.VisibilidadMenu;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.example.proyectofinalrestaurante.ui.clientes.ClientesFragment;
import com.example.proyectofinalrestaurante.ui.debug.SelectorRolDebug;
import com.example.proyectofinalrestaurante.ui.empleados.EmpleadosFragment;
import com.example.proyectofinalrestaurante.ui.login.LoginActivity;
import com.example.proyectofinalrestaurante.ui.menu.MenuFragment;
import com.example.proyectofinalrestaurante.ui.mesas.MesasFragment;
import com.example.proyectofinalrestaurante.ui.pedidos.PedidosFragment;
import com.example.proyectofinalrestaurante.ui.principal.InicioFragment;
import com.example.proyectofinalrestaurante.ui.principal.NavegacionModulos;
import com.example.proyectofinalrestaurante.ui.reportes.ReportesFragment;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Pantalla principal (post-login): menú hamburguesa con ítems filtrados por rol
 * (VisibilidadMenu → Permisos) y un único contenedor donde se intercambian los
 * Fragments de cada módulo.
 *
 * <p><b>Una pantalla por módulo, no una por rol</b> — ver Plan Fase 1c. Los siete
 * módulos tienen su Fragment desde los Entregables 4 y 5; el placeholder que usaban
 * mientras tanto ya se eliminó.</p>
 */
public class MainActivity extends AppCompatActivity implements NavegacionModulos {

    private static final String ESTADO_MODULO = "modulo_actual";

    private static final Map<Modulo, Integer> ITEM_IDS = new EnumMap<>(Modulo.class);

    static {
        ITEM_IDS.put(Modulo.INICIO, R.id.nav_inicio);
        ITEM_IDS.put(Modulo.PEDIDOS, R.id.nav_pedidos);
        ITEM_IDS.put(Modulo.MESAS, R.id.nav_mesas);
        ITEM_IDS.put(Modulo.MENU, R.id.nav_menu);
        ITEM_IDS.put(Modulo.CLIENTES, R.id.nav_clientes);
        ITEM_IDS.put(Modulo.EMPLEADOS, R.id.nav_empleados);
        ITEM_IDS.put(Modulo.REPORTES, R.id.nav_reportes);
    }

    private DrawerLayout drawer;
    private NavigationView navView;
    private Toolbar toolbar;
    private int moduloActualId = R.id.nav_inicio;

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

        // Primera sincronización del arranque, acá y no antes: este es el primer punto del
        // ciclo de vida donde hay token garantizado. El empujón de ProcessLifecycleOwner
        // ocurre en la pantalla de login, cuando todavía no hay sesión y el worker no
        // puede hacer nada (ver SyncApplication.onCreate y el Javadoc de SyncWorker).
        //
        // Solo en el arranque real: con savedInstanceState != null esto es una rotación o
        // una recreación por cambio de configuración, y ahí ya se sincronizó.
        if (savedInstanceState == null) {
            SyncScheduler.solicitar(getApplicationContext());
        }

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.main_drawer);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.menu_abrir, R.string.menu_cerrar);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navView = findViewById(R.id.nav_view);
        configurarCabecera(navView, sesion);
        filtrarMenu(navView, sesion.getRol());
        navView.setNavigationItemSelectedListener(this::alSeleccionarItem);

        if (savedInstanceState != null) {
            moduloActualId = savedInstanceState.getInt(ESTADO_MODULO, R.id.nav_inicio);
        }
        navView.setCheckedItem(moduloActualId);
        actualizarTitulo(moduloActualId);

        // Solo la primera vez: al rotar, el FragmentManager restaura el Fragment solo.
        // Volver a hacer el replace acá lo recrearía y perdería su estado.
        if (savedInstanceState == null) {
            mostrarModulo(moduloActualId);
        }

        configurarBotonAtras();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ESTADO_MODULO, moduloActualId);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // El menú de debug no existe siquiera en builds de release.
        if (SelectorRolDebug.estaDisponible()) {
            getMenuInflater().inflate(R.menu.menu_debug, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.accion_cambiar_rol_debug) {
            Sesion sesion = SesionActual.obtener();
            SelectorRolDebug.mostrar(this,
                    sesion == null ? null : sesion.getRol(),
                    this::cambiarRolDebug);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Cambia el rol de la sesión en memoria y recompone la pantalla. Solo afecta lo que
     * se muestra — el servidor sigue evaluando RLS con el rol real del usuario.
     */
    private void cambiarRolDebug(String nuevoRol) {
        Sesion sesion = SesionActual.obtener();
        if (sesion == null) {
            return;
        }
        SesionActual.guardar(sesion.conRol(nuevoRol));

        configurarCabecera(navView, SesionActual.obtener());
        filtrarMenu(navView, nuevoRol);

        // Si el rol nuevo no puede ver el módulo donde estamos parados, hay que sacarlo
        // de ahí: quedarse mirando Empleados como cocina sería exactamente el agujero
        // que este sistema de permisos quiere evitar.
        Modulo actual = moduloDe(moduloActualId);
        if (actual == null || !VisibilidadMenu.esVisible(nuevoRol, actual)) {
            abrirModulo(R.id.nav_inicio);
        } else {
            // Recrear el Fragment para que vuelva a evaluar los permisos de sus botones.
            mostrarModulo(moduloActualId);
        }

        Snackbar.make(findViewById(R.id.contenedor_contenido),
                getString(R.string.debug_rol_cambiado, nuevoRol),
                Snackbar.LENGTH_SHORT).show();
    }

    @Nullable
    private Modulo moduloDe(int menuId) {
        for (Map.Entry<Modulo, Integer> entrada : ITEM_IDS.entrySet()) {
            if (entrada.getValue() == menuId) {
                return entrada.getKey();
            }
        }
        return null;
    }

    /** Con el menú abierto, "atrás" lo cierra en vez de salir de la app. */
    private void configurarBotonAtras() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    private void aplicarInsets() {
        View contenido = findViewById(R.id.main_content);
        ViewCompat.setOnApplyWindowInsetsListener(contenido, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
        // La barra usa wrap_content + minHeight a propósito: con altura fija, este
        // padding recortaría el ícono de hamburguesa y el título fuera de la caja.
        Toolbar barra = findViewById(R.id.toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(barra, (v, insets) -> {
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

    /** Oculta los módulos que el rol no puede ver (VisibilidadMenu → Permisos). */
    private void filtrarMenu(NavigationView navView, String rol) {
        Set<Modulo> visibles = VisibilidadMenu.itemsVisibles(rol);
        Menu menu = navView.getMenu();
        for (Modulo modulo : Modulo.values()) {
            menu.findItem(ITEM_IDS.get(modulo)).setVisible(visibles.contains(modulo));
        }
    }

    private boolean alSeleccionarItem(MenuItem item) {
        if (item.getItemId() == R.id.nav_cerrar_sesion) {
            cerrarSesion();
            return true;
        }
        abrirModulo(item.getItemId());
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Punto único de cambio de módulo: lo usan tanto el menú lateral como las tarjetas
     * del inicio, para que título, ítem marcado y contenido no se desincronicen.
     */
    @Override
    public void abrirModulo(int menuId) {
        moduloActualId = menuId;
        navView.setCheckedItem(menuId);
        actualizarTitulo(menuId);
        mostrarModulo(menuId);
    }

    /**
     * Reemplaza el Fragment del contenedor. Sin back stack a propósito: los módulos son
     * destinos de primer nivel, no una pila de navegación.
     */
    private void mostrarModulo(int menuId) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.contenedor_contenido, crearFragment(menuId))
                .commit();
    }

    /**
     * Un ítem de menú desconocido cae en Inicio, no en el último módulo de la cadena:
     * si algún día se agrega un ítem y se olvida conectarlo acá, el fallback debe ser
     * la pantalla que todos pueden ver, nunca una de admin.
     */
    private Fragment crearFragment(int menuId) {
        if (menuId == R.id.nav_menu) {
            return new MenuFragment();
        }
        if (menuId == R.id.nav_pedidos) {
            return new PedidosFragment();
        }
        if (menuId == R.id.nav_mesas) {
            return new MesasFragment();
        }
        if (menuId == R.id.nav_clientes) {
            return new ClientesFragment();
        }
        if (menuId == R.id.nav_empleados) {
            return new EmpleadosFragment();
        }
        if (menuId == R.id.nav_reportes) {
            return new ReportesFragment();
        }
        return new InicioFragment();
    }

    private CharSequence tituloDe(int menuId) {
        MenuItem item = navView.getMenu().findItem(menuId);
        return item == null ? "" : item.getTitle();
    }

    private void actualizarTitulo(int menuId) {
        toolbar.setTitle(tituloDe(menuId));
    }

    private void cerrarSesion() {
        SesionActual.limpiar();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

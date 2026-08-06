package com.example.proyectofinalrestaurante.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Matriz de permisos por rol (Plan Fase 1c, Entregable 1). Java puro, sin dependencias
 * de Android — es la fuente única de verdad del cliente sobre qué puede hacer cada rol.
 *
 * <p><b>Una pantalla por módulo, no una pantalla por rol.</b> Los tres roles usan el mismo
 * Fragment; lo que cambia es qué componentes se muestran, consultando esta clase.</p>
 *
 * <p>⚠ <b>Esto no es seguridad.</b> Ocultar un botón solo mejora la experiencia — un APK se
 * puede modificar. La seguridad real son las policies RLS de Postgres, que se evalúan en el
 * servidor con el JWT del usuario. Ver contexto/40 - Proyecto Restaurante/Esquema de Base de Datos.md</p>
 */
public final class Permisos {

    public static final String ROL_ADMIN = "admin";
    public static final String ROL_MESERO = "mesero";
    public static final String ROL_COCINA = "cocina";

    private static final Map<String, Map<Modulo, Set<Accion>>> MATRIZ = construirMatriz();

    private Permisos() {
    }

    private static Map<String, Map<Modulo, Set<Accion>>> construirMatriz() {
        Map<String, Map<Modulo, Set<Accion>>> matriz = new HashMap<>();

        // admin — control total del negocio.
        Map<Modulo, Set<Accion>> admin = new EnumMap<>(Modulo.class);
        admin.put(Modulo.INICIO, EnumSet.of(Accion.VER));
        admin.put(Modulo.MENU, EnumSet.of(Accion.VER, Accion.CREAR, Accion.EDITAR, Accion.ELIMINAR));
        admin.put(Modulo.PEDIDOS, EnumSet.allOf(Accion.class));
        admin.put(Modulo.MESAS, EnumSet.allOf(Accion.class));
        admin.put(Modulo.CLIENTES, EnumSet.of(Accion.VER, Accion.CREAR, Accion.EDITAR, Accion.ELIMINAR));
        admin.put(Modulo.EMPLEADOS, EnumSet.of(Accion.VER, Accion.CREAR, Accion.EDITAR, Accion.ELIMINAR));
        admin.put(Modulo.REPORTES, EnumSet.of(Accion.VER));
        matriz.put(ROL_ADMIN, admin);

        // mesero — opera, no administra: toma pedidos y ocupa mesas, pero no cambia
        // precios del menú ni elimina registros.
        Map<Modulo, Set<Accion>> mesero = new EnumMap<>(Modulo.class);
        mesero.put(Modulo.INICIO, EnumSet.of(Accion.VER));
        mesero.put(Modulo.MENU, EnumSet.of(Accion.VER));
        // CAMBIAR_ESTADO habilita el chip "avanzar" de la tarjeta; ReglasPedido.puedeCambiarA
        // es quien de verdad limita al mesero a Listo → Entregado (ver esa clase).
        mesero.put(Modulo.PEDIDOS,
                EnumSet.of(Accion.VER, Accion.CREAR, Accion.EDITAR, Accion.CAMBIAR_ESTADO));
        mesero.put(Modulo.MESAS, EnumSet.of(Accion.VER, Accion.CAMBIAR_ESTADO));
        mesero.put(Modulo.CLIENTES, EnumSet.of(Accion.VER, Accion.CREAR, Accion.EDITAR));
        matriz.put(ROL_MESERO, mesero);

        // cocina — solo prepara: ve pedidos y los marca listos, y consulta el menú para
        // saber qué lleva un platillo. No toca dinero, clientes ni empleados.
        Map<Modulo, Set<Accion>> cocina = new EnumMap<>(Modulo.class);
        cocina.put(Modulo.INICIO, EnumSet.of(Accion.VER));
        cocina.put(Modulo.MENU, EnumSet.of(Accion.VER));
        cocina.put(Modulo.PEDIDOS, EnumSet.of(Accion.VER, Accion.CAMBIAR_ESTADO));
        matriz.put(ROL_COCINA, cocina);

        return matriz;
    }

    /**
     * ¿El rol puede hacer esta acción en este módulo? Se niega por defecto: un rol
     * desconocido solo puede ver {@link Modulo#INICIO}, y un rol nulo no puede nada.
     */
    public static boolean puede(String rol, Modulo modulo, Accion accion) {
        return accionesDe(rol, modulo).contains(accion);
    }

    /** Acciones del rol sobre el módulo. Conjunto vacío si no tiene ninguna. */
    public static Set<Accion> accionesDe(String rol, Modulo modulo) {
        if (rol == null || modulo == null) {
            return Collections.emptySet();
        }
        Map<Modulo, Set<Accion>> delRol = MATRIZ.get(rol.toLowerCase(Locale.ROOT));
        if (delRol == null) {
            // Rol desconocido: lo mínimo para que la app no quede en blanco.
            return modulo == Modulo.INICIO
                    ? Collections.unmodifiableSet(EnumSet.of(Accion.VER))
                    : Collections.emptySet();
        }
        Set<Accion> acciones = delRol.get(modulo);
        return acciones == null ? Collections.emptySet() : Collections.unmodifiableSet(acciones);
    }

    /**
     * Módulos que el rol puede ver — es decir, los ítems que aparecen en el menú de
     * navegación. Derivado de {@link Accion#VER}, no una segunda tabla que mantener.
     */
    public static Set<Modulo> modulosVisibles(String rol) {
        Set<Modulo> visibles = EnumSet.noneOf(Modulo.class);
        for (Modulo modulo : Modulo.values()) {
            if (puede(rol, modulo, Accion.VER)) {
                visibles.add(modulo);
            }
        }
        return Collections.unmodifiableSet(visibles);
    }
}

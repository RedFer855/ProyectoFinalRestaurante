package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.example.proyectofinalrestaurante.core.SesionActual;
import com.example.proyectofinalrestaurante.domain.model.Sesion;

import org.junit.Test;

/** Tests de {@link SesionActual} — holder en memoria, testeable sin Android. */
public class SesionActualTest {

    @Test
    public void inicialmenteNoHaySesion() {
        SesionActual.limpiar();
        assertNull(SesionActual.obtener());
    }

    @Test
    public void guardarYRecuperar() {
        SesionActual.limpiar();
        Sesion sesion = new Sesion("u1", "admin@restaurante.com", "token", "admin");
        SesionActual.guardar(sesion);
        assertNotNull(SesionActual.obtener());
        assertEquals("admin", SesionActual.obtener().getRol());
        assertEquals("admin@restaurante.com", SesionActual.obtener().getCorreo());
    }

    @Test
    public void limpiarDejaSinSesion() {
        SesionActual.guardar(new Sesion("u1", "a@b.com", "t", "mesero"));
        SesionActual.limpiar();
        assertNull(SesionActual.obtener());
    }
}

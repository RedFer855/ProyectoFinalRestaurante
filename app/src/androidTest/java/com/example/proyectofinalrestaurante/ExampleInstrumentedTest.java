package com.example.proyectofinalrestaurante;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // El packageName en runtime es el applicationId (hn.restaurante.app), no el
        // namespace (com.example.proyectofinalrestaurante) — son cosas distintas desde
        // AGP 7. Ver P-018 en Deuda Técnica - Pendientes.md.
        assertEquals("hn.restaurante.app", appContext.getPackageName());
    }
}
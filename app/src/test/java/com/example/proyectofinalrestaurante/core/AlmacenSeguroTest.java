package com.example.proyectofinalrestaurante.core;

import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Tests de {@link AlmacenSeguro} (P-009, [[Plan Fase 0b - Cierre de la deuda P0]] §4.2).
 *
 * <p><b>Cobertura parcial, y por qué:</b> Robolectric 4.16.1 (la versión del proyecto, ya
 * usada por {@code MesaDaoTest} para Room) <b>no implementa</b>
 * {@code KeyGenerator.getInstance("AES", "AndroidKeyStore")} — lanza
 * {@code NoSuchAlgorithmException} envuelta en {@code KeyStoreException}. Verificado en vivo
 * el 2026-08-05: {@code KeyStore.getInstance("AndroidKeyStore")} y {@code .load(null)}
 * <b>sí</b> funcionan bajo Robolectric, pero generar la clave AES falla siempre.
 *
 * <p>Consecuencia: el camino feliz de cifrar/descifrar (guardar un texto y leerlo igual) —
 * lo que el plan llama D1 — <b>no se puede verificar en este entorno</b>. Es la misma
 * categoría de límite que ya dejaron P-004 (necesita un teléfono físico) y la salvedad de
 * P-024 (Robolectric emula {@code BitmapFactory}, no lo ejecuta): acá Robolectric ni
 * siquiera emula el {@code KeyGenerator} del keystore. Falta verificar D1 en un dispositivo o
 * emulador real corriendo la app — un test instrumentado en {@code androidTest/}, no uno de
 * {@code testDebugUnitTest}.</p>
 *
 * <p>Lo que sí se puede probar sin clave real es el <b>contrato de resiliencia</b>: ante
 * cualquier fallo (Keystore no disponible, dato sin el formato esperado, almacén vacío),
 * {@link AlmacenSeguro} nunca lanza — devuelve {@code null} y deja el almacén limpio. Eso es
 * exactamente lo que este entorno permite ejercitar de verdad, porque el Keystore roto es la
 * situación real bajo Robolectric, no una simulación.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AlmacenSeguroTest {

    private static final String PREFS = "almacen_seguro";
    private static final String CLAVE_VALOR = "valor_cifrado";

    private AlmacenSeguro almacen() {
        return new AlmacenSeguro(RuntimeEnvironment.getApplication());
    }

    private SharedPreferences prefsCrudas() {
        return RuntimeEnvironment.getApplication().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // D2
    @Test
    public void leer_conElAlmacenVacio_devuelveNullSinExcepcion() {
        assertNull(almacen().leer());
    }

    /**
     * Bajo Robolectric el Keystore nunca llega a generar una clave (ver el Javadoc de la
     * clase), así que {@code guardar()} cae en su propio manejo de error — nunca lanza, y no
     * deja nada a medio escribir.
     */
    @Test
    public void guardar_conElKeystoreNoDisponible_noLanzaYNoDejaNadaEscrito() {
        AlmacenSeguro almacen = almacen();

        almacen.guardar("un-token-cualquiera");

        assertNull(prefsCrudas().getString(CLAVE_VALOR, null));
        assertNull(almacen.leer());
    }

    // D3 (parcial: el dato nunca tuvo el formato "iv:cifrado" porque guardar() no pudo
    // escribir nada bajo este entorno — ver el Javadoc de la clase).
    @Test
    public void leer_conUnValorSinSeparador_devuelveNullYLimpiaElAlmacen() {
        prefsCrudas().edit().putString(CLAVE_VALOR, "esto-no-tiene-el-formato-iv-dos-puntos-cifrado").apply();

        assertNull(almacen().leer());
        assertNull(prefsCrudas().getString(CLAVE_VALOR, null));
    }

    @Test
    public void leer_conBase64Invalido_devuelveNullYLimpiaElAlmacen() {
        // Formato correcto (tiene el separador) pero ninguno de los dos lados es Base64
        // válido: IllegalArgumentException al decodificar, mismo contrato que un fallo de
        // descifrado — nunca se propaga.
        prefsCrudas().edit().putString(CLAVE_VALOR, "no-es-base64:tampoco-esto").apply();

        assertNull(almacen().leer());
        assertNull(prefsCrudas().getString(CLAVE_VALOR, null));
    }

    @Test
    public void guardarConNull_equivaleABorrar() {
        AlmacenSeguro almacen = almacen();
        prefsCrudas().edit().putString(CLAVE_VALOR, "algo-preexistente").apply();

        almacen.guardar(null);

        assertNull(prefsCrudas().getString(CLAVE_VALOR, null));
        assertNull(almacen.leer());
    }

    @Test
    public void borrar_dejaElAlmacenVacio() {
        AlmacenSeguro almacen = almacen();
        prefsCrudas().edit().putString(CLAVE_VALOR, "algo-preexistente").apply();

        almacen.borrar();

        assertNull(almacen.leer());
    }
}

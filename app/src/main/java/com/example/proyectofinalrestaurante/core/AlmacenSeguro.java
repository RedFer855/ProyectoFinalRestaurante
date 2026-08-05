package com.example.proyectofinalrestaurante.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Persiste un texto cifrado en disco con una clave AES-256 del <b>Android Keystore</b> (P-009).
 *
 * <p>Reemplaza a {@code EncryptedSharedPreferences}: Google deprecó <b>todas</b> las APIs de
 * {@code androidx.security:security-crypto} en {@code 1.1.0-alpha07} (abril 2025) "in favour
 * of existing platform APIs and direct use of Android Keystore" — literalmente esto. Usarlo
 * hoy viola la regla #3 del Estándar de Ingeniería Android (cero APIs deprecadas). Ver
 * [[Plan Fase 0b - Cierre de la deuda P0]] §4.1 y [[Seguridad y Privacidad Android]].</p>
 *
 * <p>La clave AES-256/GCM nunca sale del keystore — en dispositivos con TEE/StrongBox queda
 * respaldada por hardware. GCM genera un <b>IV aleatorio por cifrado</b>: se guarda junto al
 * texto cifrado (formato {@code base64(iv):base64(cifrado)}) en un {@code SharedPreferences}
 * común. Guardar el IV no compromete nada; <b>reutilizarlo sí</b>, y por eso nunca se fija
 * a mano — cada llamada a {@link #guardar} pide un IV nuevo a {@code Cipher}.</p>
 *
 * <p><b>Contrato ante cualquier fallo de descifrado: borra todo y devuelve {@code null}</b> —
 * nunca una excepción a quien llama. Cambiar el bloqueo de pantalla, restaurar un backup en
 * otro dispositivo, o un fallo del TEE invalidan la clave
 * ({@code KeyPermanentlyInvalidatedException}, subclase de {@code GeneralSecurityException});
 * la respuesta correcta es "no hay sesión guardada", no un crash. Mismo espíritu que el
 * {@code catch (SecurityException)} que cerró P-022.</p>
 */
public final class AlmacenSeguro {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "restaurante_sesion_key";
    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private static final int TAMANO_TAG_GCM_BITS = 128;
    private static final char SEPARADOR = ':';

    private static final String PREFS = "almacen_seguro";
    private static final String CLAVE_VALOR = "valor_cifrado";

    private final SharedPreferences prefs;

    public AlmacenSeguro(Context contexto) {
        this.prefs = contexto.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Cifra {@code texto} y lo persiste. Un {@code texto} {@code null} equivale a {@link #borrar()}. */
    public void guardar(@Nullable String texto) {
        if (texto == null) {
            borrar();
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.ENCRYPT_MODE, clave());
            byte[] iv = cipher.getIV();
            byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            String valor = Base64.encodeToString(iv, Base64.NO_WRAP) + SEPARADOR
                    + Base64.encodeToString(cifrado, Base64.NO_WRAP);
            prefs.edit().putString(CLAVE_VALOR, valor).apply();
        } catch (GeneralSecurityException | IOException ex) {
            // No hay nada razonable que hacer con un fallo al cifrar: no se persiste nada,
            // que es el estado seguro (equivale a "sin sesión guardada").
            borrar();
        }
    }

    /** Lee y descifra. Ante cualquier fallo (clave invalidada, dato corrupto) borra todo y devuelve {@code null}. */
    @Nullable
    public String leer() {
        String valor = prefs.getString(CLAVE_VALOR, null);
        if (valor == null) {
            return null;
        }
        int separador = valor.indexOf(SEPARADOR);
        if (separador < 0) {
            borrar();
            return null;
        }
        try {
            byte[] iv = Base64.decode(valor.substring(0, separador), Base64.NO_WRAP);
            byte[] cifrado = Base64.decode(valor.substring(separador + 1), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.DECRYPT_MODE, clave(), new GCMParameterSpec(TAMANO_TAG_GCM_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
            // GeneralSecurityException: clave invalidada o tag de GCM que no verifica (dato
            // corrupto/manipulado). IOException: keyStore.load(). IllegalArgumentException:
            // Base64 inválido. En los tres casos el contrato es el mismo — nunca se propaga,
            // se trata como "sin sesión".
            borrar();
            return null;
        }
    }

    public void borrar() {
        prefs.edit().remove(CLAVE_VALOR).apply();
    }

    /** Devuelve la clave existente en el keystore, o la genera si es la primera vez. */
    private SecretKey clave() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        SecretKey existente = (SecretKey) keyStore.getKey(ALIAS, null);
        if (existente != null) {
            return existente;
        }
        KeyGenerator generador = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // El SyncWorker lee la sesión en segundo plano: exigir autenticación del
                // usuario (huella, PIN) dejaría la clave inutilizable sin la app en primer
                // plano, y el drenado del outbox nunca podría autenticarse.
                .setUserAuthenticationRequired(false)
                .build();
        generador.init(spec);
        return generador.generateKey();
    }
}

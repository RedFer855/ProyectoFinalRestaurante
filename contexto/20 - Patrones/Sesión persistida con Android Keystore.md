---
title: Sesión persistida con Android Keystore
tags:
  - patron
  - seguridad
  - android
lifecycle: verified
---

# Sesión persistida con Android Keystore

> [!abstract] Definición
> Para guardar un secreto en disco (un token de sesión, y cualquier dato personal que llegue
> después — ver **P-027**) sin `EncryptedSharedPreferences` (deprecado desde
> `androidx.security:security-crypto 1.1.0-alpha07`, abril 2025): generar una clave AES-256
> directo en el `AndroidKeyStore`, cifrar con `AES/GCM/NoPadding`, y guardar
> `base64(iv):base64(cifrado)` en un `SharedPreferences` común. La clave nunca sale del
> keystore. Ante cualquier fallo de descifrado, **borrar todo y devolver `null`** — nunca una
> excepción a quien llama.

Implementado por primera vez en **P-009** ([[Deuda Técnica - Pendientes]],
[[Plan Fase 0b - Cierre de la deuda P0]] §4.2), para persistir la `Sesion` (access token,
refresh token, vencimiento).

---

## Por qué no `EncryptedSharedPreferences`

Google deprecó **todas** las APIs de `androidx.security:security-crypto` en `1.1.0-alpha07`
(abril 2025) y lo repitió en `1.1.0-beta01` (junio 2025):

> *"Deprecated all APIs in favour of existing platform APIs and direct use of Android
> Keystore."*

Motivos conocidos: violaciones de StrictMode por I/O en el hilo principal, y excepciones de
*keyset corruption* de Tink. Usarla hoy está en la [[Lista Negra de APIs Android]].

Alternativas consideradas y descartadas — ver la tabla completa en
[[Plan Fase 0b - Cierre de la deuda P0]] §4.1:

| Opción | Por qué no |
|---|---|
| DataStore + Tink | API idiomática Kotlin `Flow`; el puente Java arrastra RxJava3 entero. Ver [[Librerias Java-Friendly vs Kotlin-Only]] |
| Fork comunitario de `security-crypto` | Dependencia no mantenida por Google, para código de seguridad — peor que escribirlo |
| **Android Keystore directo** | ✅ Es literalmente lo que la nota de deprecación recomienda. ~130 líneas de Java puro, cero dependencias nuevas |

---

## La pieza: `AlmacenSeguro`

```java
public final class AlmacenSeguro {

    private SecretKey clave() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        SecretKey existente = (SecretKey) keyStore.getKey(ALIAS, null);
        if (existente != null) return existente;

        KeyGenerator generador = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generador.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false) // el SyncWorker lee en 2° plano
                .build());
        return generador.generateKey();
    }

    public void guardar(@Nullable String texto) {
        if (texto == null) { borrar(); return; }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave());
            byte[] iv = cipher.getIV();                       // aleatorio, uno por cifrado
            byte[] cifrado = cipher.doFinal(texto.getBytes(UTF_8));
            prefs.edit().putString(CLAVE, b64(iv) + ":" + b64(cifrado)).apply();
        } catch (GeneralSecurityException | IOException ex) {
            borrar();  // estado seguro: nada persistido equivale a "sin sesión"
        }
    }

    @Nullable
    public String leer() {
        // ... Base64.decode + Cipher.DECRYPT_MODE con el IV guardado ...
        // catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
        //     borrar(); return null;   // clave invalidada o dato corrupto: nunca se propaga
        // }
    }
}
```

### Las cuatro reglas que importan

1. **`setUserAuthenticationRequired(false)`.** Si el `SyncWorker` corre en segundo plano y la
   clave exigiera biometría/PIN, quedaría inutilizable sin la app en primer plano.
2. **El IV nunca se fija a mano.** GCM genera uno aleatorio por cifrado (`cipher.getIV()`
   después de `init(ENCRYPT_MODE, ...)`); reutilizarlo con la misma clave compromete el
   cifrado. Guardarlo junto al texto cifrado no es un problema — reutilizarlo sí.
3. **El contrato de `leer()` es "nunca lanza".** `KeyPermanentlyInvalidatedException` (cambiar
   el bloqueo de pantalla, restaurar un backup en otro dispositivo, fallo del TEE) es
   *esperable*, no excepcional: la respuesta correcta es "no hay sesión", nunca un crash.
   Mismo espíritu que el `catch (SecurityException)` que cerró **P-022**.
4. **Excluir el `SharedPreferences` del backup** (`data_extraction_rules.xml` +
   `backup_rules.xml`, `domain="sharedpref"`). La clave no se exporta entre dispositivos, así
   que un valor restaurado sería cifrado indescifrable — `leer()` ya lo trataría como
   corrupto y lo borraría solo, pero es más prolijo no restaurar basura.

---

## La capa de arriba: refresh proactivo y single-flight

Persistir el token es la mitad del problema; la otra mitad es refrescarlo sin que dos
sincronizadores corriendo en paralelo se pisen. Ver `core/ProveedorDeToken.java`:

```java
public String get() {
    Sesion sesion = SesionActual.obtener();
    if (sesion == null) return null;
    if (vigente(sesion)) return sesion.getAccessToken();
    return refrescar();  // synchronized, con re-chequeo de vencimiento adentro
}
```

- **Proactivo, no reactivo** (no es un `Authenticator` de OkHttp): un WebSocket necesita un
  token válido *antes* de conectar, y un `Authenticator` solo reacciona a un 401 que ahí
  nunca llega.
- **Single-flight bajo `synchronized`, con doble chequeo** — mismo patrón que la
  inicialización perezosa de un singleton (`SyncApplication.baseDeDatos()`). Importa porque
  Supabase **rota** el refresh token: sin el lock, la primera carrera gana y las demás usan
  un refresh token ya muerto.
- **Transitorio vs. permanente en la respuesta del refresh:** un 401/400 (refresh token
  muerto) cierra la sesión; un 5xx o sin red **no la toca** — el próximo `get()` reintenta.

---

## Verificación: el límite real de Robolectric

Robolectric 4.16.1 (la versión del proyecto) **no implementa**
`KeyGenerator.getInstance("AES", "AndroidKeyStore")` — lanza `NoSuchAlgorithmException`
envuelta en `KeyStoreException`, aunque `KeyStore.getInstance("AndroidKeyStore")` y
`.load(null)` sí funcionan. Verificado en vivo el 2026-08-05.

Consecuencia: el camino feliz de cifrar/descifrar no se puede probar con
`testDebugUnitTest` en este entorno — solo el contrato de resiliencia (almacén vacío, dato
corrupto, Keystore no disponible → nunca lanza). Falta un test instrumentado en
`androidTest/` contra un dispositivo o emulador real. Es el mismo tipo de límite que
**P-004** (verificación en teléfono físico) y la salvedad de **P-024** (Robolectric emula
`BitmapFactory`, no lo ejecuta) — no es una falla del diseño, es lo que Robolectric no cubre.

---

## Anti-patrones

- `EncryptedSharedPreferences` en código nuevo — deprecado, ver [[Lista Negra de APIs Android]].
- Guardar el token en `SharedPreferences` plano "hasta que haya tiempo de cifrarlo".
- Reutilizar el mismo IV entre cifrados (a mano o cacheándolo).
- Dejar que `KeyPermanentlyInvalidatedException` se propague a la UI como si fuera un bug.
- Refrescar el token sin lock cuando varios componentes pueden pedirlo a la vez.

---

## Relaciones

- [[Deuda Técnica - Pendientes]] — P-009, P-027 (el próximo dato sensible sin cifrar)
- [[Plan Fase 0b - Cierre de la deuda P0]] §4
- [[Seguridad y Privacidad Android]]
- [[Lista Negra de APIs Android]]
- [[Catálogo de Patrones Android]]
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]] — de donde sale que el refresh es manual

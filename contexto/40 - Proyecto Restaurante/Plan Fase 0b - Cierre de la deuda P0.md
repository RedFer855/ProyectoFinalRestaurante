---
title: Plan Fase 0b — Cierre de la deuda P0
tags:
  - restaurante
  - plan
  - fase0
  - deuda-tecnica
  - seguridad
date: 2026-08-04
lifecycle: draft
---

# Plan Fase 0b — Cierre de la deuda P0

> [!success] Cerrado — 2026-08-05
> Los cuatro ítems están resueltos: **P-018** ([[Deuda Técnica - Pendientes]]),
> **P-029** (ídem), **P-004** (verificado en teléfono físico por el usuario) y **P-009**
> (`AlmacenSeguro` + `ProveedorDeToken`, documentado en detalle en
> [[Sesión persistida con Android Keystore]]). `./gradlew testDebugUnitTest assembleDebug` →
> BUILD SUCCESSFUL, 420 tests (piso del plan: ≥400). Rama `fix/fase0b-deuda-p0`.
>
> Única salvedad: `AlmacenSeguroTest` no pudo verificar el cifrado/descifrado real —
> Robolectric 4.16.1 no implementa `KeyGenerator.getInstance("AES", "AndroidKeyStore")`. Ver
> el callout en [[Deuda Técnica - Pendientes]] → P-009. Falta un test instrumentado en un
> dispositivo o emulador real.

> [!danger] Leé primero [[Protocolo de Ejecución de un Plan]]
> Contrato completo: división Parte A / Parte B, orden de lectura, reglas de oro y qué
> significa "terminado".

> [!abstract] Qué cierra este plan
> Los cuatro ítems clasificados **P0** el 2026-08-04: **P-018**, **P-004**, **P-009** y
> **P-029**. Son los que bloquean el merge de la Fase 1, los que bloquean la Fase 3, y el
> único riesgo **irreversible** del proyecto.
>
> Es una rama de remediación, no de producto: `fix/fase0b-deuda-p0`. Continúa la Fase 0 de
> [[Roadmap de Fases]], que cerró P-003/P-004/P-005/P-006/P-012/P-013/P-020.

> [!warning] Ninguno de estos toca Supabase
> **No hay Parte A.** Los cuatro son código Android o verificación en dispositivo. Cualquier
> agente puede ejecutar los tres de código sin acceso al conector.

---

## 0. Orden de ejecución y por qué

| # | Ítem | Esfuerzo | Depende de | Por qué en esta posición |
|---|---|---|---|---|
| 1 | **P-018** · `applicationId` | ~15 min | — | Riesgo **irreversible** y arreglo de una línea. Se hace primero para sacarlo de la cabeza |
| 2 | **P-029** · delta que pierde filas | ~3 h | — | Independiente y acotado. Deja el terreno limpio para el sincronizador de Pedidos |
| 3 | **P-009** · sesión persistida y refresh | ~1 día | — | El grande. Va último porque es el que puede desbordarse |
| 4 | **P-004** · verificación física | ~10 min | nada | **Tuyo**, con un teléfono. Se puede hacer en paralelo, hoy mismo |

**No hay dependencias entre ellos**, así que el orden es por riesgo y por tamaño, no por
técnica. Se pueden partir en tres commits (o tres ramas) independientes.

---

## 1. P-018 — `applicationId` real

### El problema

```kotlin
applicationId = "com.example.proyectofinalrestaurante"
```

Google Play **rechaza** cualquier `applicationId` que empiece con `com.example`. Y una vez
publicada, **no se puede cambiar**: cambiarlo equivale a publicar una app nueva y perder la
existente con todos sus usuarios y reseñas.

### La solución, y por qué es de una línea

`applicationId` y `namespace` son **cosas distintas** desde AGP 7. El `namespace` es el
paquete Java (de donde sale `BuildConfig`, `R`, y los `import`); el `applicationId` es la
identidad de la app en el dispositivo y en Play. **Solo hay que cambiar el segundo.**

```kotlin
android {
    namespace = "com.example.proyectofinalrestaurante"   // ← NO se toca
    defaultConfig {
        applicationId = "hn.restaurante.app"             // ← el único cambio
    }
}
```

Verificado que nada más depende del valor: el único lugar del manifest que lo usa es
`android:authorities="${applicationId}.androidx-startup"`, que ya está **parametrizado**. No
hay deep links, ni `google-services.json`, ni providers con el paquete escrito a mano.

> [!question] Decisión pendiente: qué id
> `hn.restaurante.app` es lo que sugiere [[Deuda Técnica - Pendientes]] y sirve. Alternativas
> razonables si hay un dominio real: `hn.<dominio>.restaurante`, o el nombre del negocio.
> **Lo único que importa es que no empiece con `com.example`** y que se elija ahora, no
> después de publicar. Confirmalo antes de ejecutar este punto.

**Efecto colateral aceptable:** la app se instala como una **app distinta**. Cualquier
instalación de prueba existente queda en paralelo y hay que desinstalarla a mano. Como no
está publicada, hoy es gratis; en cualquier momento futuro, no.

### Verificación

```bash
./gradlew assembleDebug
aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | head -1
# debe decir: package: name='hn.restaurante.app' ... (y NO com.example)
```

Mismo método con el que se verificó P-003 — el binario, no el `.kts`.

---

## 2. P-004 — verificación en teléfono físico (tuya)

El código ya está: `LoginActivity` llama `EdgeToEdge.enable(this)` y aplica insets de
`systemBars() | ime()` desde el 2026-07-29. Lo que falta es **mirarlo en un teléfono real**.
Es lo único que separa a `feat/fase1-login` de un merge a `master`.

### Checklist

| # | Qué mirar | Cómo | ✅ |
|---|---|---|---|
| 1 | El título del login **no** queda bajo la barra de estado | Abrir la app | ☐ |
| 2 | El botón "Ingresar" **no** queda bajo la barra de navegación | Mirar el pie de la pantalla | ☐ |
| 3 | Con el teclado abierto, "Ingresar" **sigue visible** | Tocar el campo de contraseña | ☐ |
| 4 | Ídem en **modo gestos** y en **modo 3 botones** | Ajustes → Navegación del sistema | ☐ |
| 5 | Ídem en **horizontal** | Rotar | ☐ |
| 6 | Ídem con **fuente al 200 %** | Ajustes → Pantalla → Tamaño de fuente | ☐ |

Los puntos 4 a 6 son donde suele romperse: la barra de gestos y la de 3 botones tienen
alturas distintas, y el punto 6 es el que además cubre la mitad pendiente de **P-010**.

**Si algo falla:** anotá qué y en qué configuración. No es un bug de lógica, es de layout, y
se arregla en `activity_login.xml` + `aplicarInsets()`.

**Si todo pasa:** marcá P-004 como verificado y **mergeá `feat/fase1-login` a `master`**.

---

## 3. P-029 — el delta que pierde filas

### El problema, en concreto

Tres de los cuatro sincronizadores paginan el delta avanzando la marca de agua **dentro** del
bucle:

```java
// SincronizadorMesas.bajarDelta() — el patrón roto
while (true) {
    resultado = remoto.listarMesasDesde(marca);      // pide gt.marca
    for (MesaDto dto : pagina) {
        marca = mayor(marca, dto.getActualizadoEn()); // ← la marca avanza acá
    }
    guardarMarca(marca);                              // ← y se guarda acá
    if (pagina.size() < LIMITE_DELTA) break;
}
```

Si hay **más de 50 filas con el mismo `actualizado_en`**, la segunda consulta pide
`gt.<esa marca>` y las excluye por definición. **Esas filas no se bajan nunca.** No hay error,
no hay log: simplemente no están.

Y no es un caso raro: es exactamente lo que produce un `INSERT` masivo, donde todas las filas
comparten timestamp. O sea que **se rompe justo en la instalación desde cero**.

`SincronizadorMenu` ya se corrigió el 2026-08-04. Los otros tres no.

| Sincronizador | `offset` | Tope de páginas | Página en una transacción |
|---|---|---|---|
| `SincronizadorMenu` | ✅ | ✅ | ✅ |
| `SincronizadorEmpleados` | ❌ | ❌ | ❌ |
| `SincronizadorMesas` | ❌ | ❌ | ❌ |
| `SincronizadorClientes` | ❌ | ❌ | ❌ |

### La solución: portar el bucle del Menú

**No se inventa nada** — se copia `SincronizadorMenu.bajarPlatillos()`, que ya está probado:

```java
private ResultadoSync bajarDelta(@Nullable String errorPermanenteDelDrenado) {
    String marcaInicial = leerMarca();      // FIJA durante toda la pasada
    String marcaMaxima  = marcaInicial;
    boolean huboConflictoLocal = false;
    int desplazamiento = 0;

    for (int pagina = 0; pagina < MAX_PAGINAS; pagina++) {
        ResultadoRed<List<MesaDto>> resultado =
                remoto.listarMesasDesde(marcaInicial, desplazamiento);   // ← offset
        if (!resultado.isExitoso()) return convertirFalloDelta(resultado);

        List<MesaDto> filas = resultado.getValor();
        for (MesaDto dto : filas) {
            if (dto.getActualizadoEn() != null) {
                marcaMaxima = mayor(marcaMaxima, dto.getActualizadoEn());
            }
        }
        huboConflictoLocal |= aplicarPagina(filas);   // ← en UNA transacción
        desplazamiento += filas.size();
        if (filas.size() < MesaRemoto.LIMITE_DELTA) break;
    }

    guardarMarca(marcaMaxima);   // ← AL FINAL, no dentro del bucle
    …
}
```

Cuatro cambios, y cada uno arregla algo distinto:

| Cambio | Qué arregla |
|---|---|
| Marca **fija** + `offset` que avanza | El agujero de las filas con timestamp repetido |
| `order=actualizado_en.asc,id_X.asc` | Sin orden total, el `offset` puede repetir o saltear filas entre pedidos |
| `guardarMarca` **al final** | Un corte a mitad re-baja desde la marca vieja. Es el precio correcto: aplicar filas es idempotente (se busca por `id_servidor`), perder filas no se arregla solo |
| `MAX_PAGINAS` como tope | Cinturón contra un bucle infinito si el progreso se estanca |
| `aplicarPagina` en una transacción | 50 filas eran 50 `fsync` y **50 repintados** del `RecyclerView`. Agrupado: una sola re-emisión por página |

### Archivos a tocar

| Archivo | Cambio |
|---|---|
| `data/remote/SupabaseMesaApi` · `SupabaseClienteApi` · `SupabaseEmpleadoApi` | Agregar `@Query("offset")` al método de listado |
| `data/repository/MesaRemoto` · `ClienteRemoto` · `EmpleadoRemoto` | Propagar `desplazamiento`; agregar el desempate por id al `order` |
| `data/sync/SincronizadorMesas` · `SincronizadorClientes` · `SincronizadorEmpleados` | Portar el bucle; recibir `EjecutorDeTransaccion` por constructor |
| `core/SyncApplication` | Pasar `base::runInTransaction` a los tres constructores |

`EjecutorDeTransaccion` **ya existe** (se creó para el Menú). No se agrega nada nuevo.

### Pruebas

| # | Caso | Esperado |
|---|---|---|
| C1 | Delta de 120 filas, **todas con el mismo `actualizado_en`** | Las 120 quedan en Room |
| C2 | Delta de 120 filas con marcas distintas | Las 120, y la marca guardada es la máxima |
| C3 | Segunda página falla con error transitorio | La marca **no** avanzó: la próxima pasada re-baja desde la vieja |
| C4 | Más de `MAX_PAGINAS` páginas | Corta sin colgarse |
| C5 | Una página se aplica | **Una** sola invalidación de tabla, no N |

C1 es **el** test: es el que hoy falla en los tres y pasa en el Menú. Uno por módulo.

---

## 4. P-009 — sesión persistida y refresh de token

Es el más grande de los cuatro y el de mayor valor de producto: hoy **hay que iniciar sesión
en cada arranque de la app**, y sin token el `SyncWorker` no drena la cola.

### 4.1 Corrección previa: la solución documentada quedó obsoleta

> [!danger] `EncryptedSharedPreferences` está **deprecado** — no se usa
> [[Deuda Técnica - Pendientes]] (P-009) y [[Seguridad y Privacidad Android]] prescriben
> *"`SesionLocalDataSource` sobre `EncryptedSharedPreferences`"*. **Esa indicación ya no vale.**
>
> Google deprecó **todas** las APIs de `androidx.security:security-crypto` en
> **1.1.0-alpha07** (abril 2025) y lo repitió en **1.1.0-beta01** (junio 2025). El texto
> oficial de la nota de versión es:
>
> > *"Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore."*
>
> Los motivos conocidos: violaciones de StrictMode por I/O en el hilo principal, y
> excepciones de *keyset corruption* de Tink. Usarlo hoy viola la regla #3 del
> [[Estándar de Ingeniería Android]] ("cero APIs deprecadas") y entra en la
> [[Lista Negra de APIs Android]].

**Alternativas evaluadas:**

| Opción | Veredicto |
|---|---|
| `EncryptedSharedPreferences` | ❌ Deprecada. Es lo que dice el ítem viejo |
| **DataStore + Tink** (lo que sugiere la comunidad) | ❌ La API idiomática es Kotlin `Flow`; el puente Java es `datastore-preferences-rxjava3`, que mete **RxJava3** entero en un proyecto que usa `ExecutorService`. Proto DataStore además exige codegen de protobuf. Desproporcionado |
| Fork comunitario de la librería | ❌ Meter una dependencia no mantenida por Google **para código de seguridad** es peor que escribir el código |
| **Android Keystore directo + `SharedPreferences` con el texto cifrado** | ✅ Es **literalmente lo que Google recomienda** en la nota de deprecación. ~80 líneas de Java puro, cero dependencias nuevas, testeable |

### 4.2 `AlmacenSeguro` — el reemplazo

Es lo que `EncryptedSharedPreferences` hacía por dentro, menos la gestión de *keysets* de
Tink (que es justamente lo que fallaba).

- Clave AES de 256 bits generada en el **`AndroidKeyStore`** con alias fijo. Nunca sale del
  keystore; en dispositivos con TEE/StrongBox queda respaldada por hardware.
- `KeyGenParameterSpec` con `AES/GCM/NoPadding`, `setUserAuthenticationRequired(false)`
  (si no, el `SyncWorker` en segundo plano no podría leer nada).
- GCM genera un **IV aleatorio por cifrado**: se guarda junto al texto cifrado
  (`base64(iv) + ":" + base64(cipher)`), en un `SharedPreferences` común. Guardar el IV no
  compromete nada; **reutilizarlo sí**, y por eso nunca se fija a mano.
- Disponible desde **API 23**; el `minSdk` es 24. ✅

> [!warning] El keystore se puede invalidar, y no puede tumbar la app
> Cambiar el bloqueo de pantalla, restaurar un backup en otro dispositivo o un fallo del TEE
> dejan la clave inutilizable (`KeyPermanentlyInvalidatedException`, `GeneralSecurityException`).
> El contrato de `AlmacenSeguro` es: **ante cualquier fallo de descifrado, borra todo y
> devuelve "no hay sesión"** → el usuario vuelve al login. Nunca una excepción a la UI.
> Es el mismo espíritu que el `catch (SecurityException)` que cerró **P-022**.

### 4.3 Lo que hoy se tira a la basura

```java
// LoginResponseDto — solo parsea esto:
@SerializedName("access_token") private String accessToken;
@SerializedName("user")         private UsuarioDto user;
```

**`refresh_token` y `expires_in` ni siquiera se deserializan.** El arreglo empieza acá:

| Clase | Cambio |
|---|---|
| `LoginResponseDto` | Sumar `refresh_token`, `expires_in` |
| `Sesion` (domain) | Sumar `refreshToken` y `expiraEnMillis` (epoch absoluto, no duración) |
| `SupabaseAuthApi` | Sumar `@POST("auth/v1/token?grant_type=refresh_token")` |
| `RefrescarRequestDto` | Nuevo: `{"refresh_token": "..."}` |

`expires_in` viene en **segundos relativos**; se guarda como instante absoluto para que no
dependa de cuándo se lea.

### 4.4 Dónde se enchufa el refresh — el punto de reutilización

> [!success] Cero call sites tocados
> Todos los remotos ya reciben el token por un **`Supplier<String>` inyectado**:
> ```java
> new MesaRemoto(SupabaseClient.getMesaApi(), SyncApplication::tokenDeLaSesion)
> ```
> **Se reemplaza ese `Supplier` por uno que refresca**, y no cambia ni una línea de
> `MesaRemoto`, `ClienteRemoto`, `MenuRemoto`, `EmpleadoRemoto` ni del futuro `PedidoRemoto`.
> Es el mismo principio que la Fase 3: enchufar encima de la costura que ya existe, no al lado.

`ProveedorDeToken implements Supplier<String>`:

```
get():
  sesion = SesionActual.obtener()
  si sesion == null                        → null
  si sesion.expiraEnMillis - ahora > 60 s  → devolver el access_token
  si no                                    → refrescar (bajo lock) y devolver el nuevo
```

**Refresco proactivo, no reactivo (`Authenticator` de OkHttp).** Tres razones:

1. El WebSocket de la Fase 3 necesita un token válido **antes** de conectar. Un `Authenticator`
   reacciona a un 401 que ahí nunca llega — la conexión simplemente falla.
2. Un `Authenticator` viviría en `OkHttpClient`, y hoy hay **siete** (P-028). Habría que
   ponerlo en los siete, o arreglar P-028 primero.
3. El `Supplier` ya existe y ya está inyectado en todos lados. Es la costura correcta.

> [!danger] El refresh tiene que ser *single-flight*
> Cinco sincronizadores corren en fila dentro del `SyncWorker`, y el socket puede pedir token
> a la vez. Sin lock, un token vencido dispara **seis refreshes en paralelo**. Y como
> **Supabase rota el refresh token** —cada refresh devuelve uno nuevo y **invalida el
> anterior**— las cinco carreras perdedoras usarían un refresh token ya muerto: la sesión se
> cae sola.
>
> Un `synchronized` con **re-chequeo de vencimiento adentro** (el mismo doble chequeo que ya
> usa `SupabaseClient` para su init perezoso). El que entra segundo encuentra el token ya
> renovado y no pide nada.

**Y el refresh token nuevo se persiste sí o sí.** Si se pierde, la sesión está muerta aunque
el access token siga vivo.

### 4.5 Arranque de la app

```
SyncApplication.onCreate()
  └─ SesionActual.guardar( almacen.leerSesion() )   ← ANTES de todo lo demás
```

Tiene que ocurrir **antes** de que `ProcessLifecycleOwner.onStart` dispare, porque ese guard
ya pregunta `if (SesionActual.obtener() != null)`. Hoy la respuesta siempre es "no" en el
arranque, y por eso el sync nunca se encola desde ahí.

`LoginActivity` sigue siendo `LAUNCHER` (cambiar eso es territorio de **P-015**), pero en
`onCreate` pregunta: si hay sesión válida → `MainActivity` + `finish()`. Es la mínima
intervención que da el efecto buscado: **la app abre en el tablero, no en el login.**

`cerrarSesion()` gana una línea: `almacen.borrar()`.

> [!note] `allowBackup="true"` y el token
> El manifest permite backup. Un token cifrado con una clave del keystore **no es restaurable
> en otro dispositivo** (la clave no se exporta), así que el backup queda inútil y se descarta
> solo — que es el comportamiento correcto. Igual conviene excluir el archivo en
> `data_extraction_rules.xml` para no restaurar basura.

### 4.6 Archivos

| Archivo | Acción |
|---|---|
| `core/AlmacenSeguro.java` | **Nuevo** — Keystore AES/GCM + SharedPreferences |
| `core/ProveedorDeToken.java` | **Nuevo** — `Supplier<String>` con refresh single-flight |
| `data/repository/SesionLocal.java` | **Nuevo** — serializa `Sesion` ↔ almacén |
| `domain/repository/SesionRepository.java` | **Nuevo** — el contrato |
| `domain/model/Sesion.java` | `refreshToken`, `expiraEnMillis`; **borrar `conRol()`** (su único consumidor era el selector de rol que elimina la Fase 3) |
| `data/remote/SupabaseAuthApi.java` | Endpoint de refresh |
| `data/remote/dto/LoginResponseDto.java` | `refresh_token`, `expires_in` |
| `data/remote/dto/RefrescarRequestDto.java` | **Nuevo** |
| `core/SesionActual.java` | Se hidrata al arranque; documentar que sigue siendo el caché en memoria |
| `core/SyncApplication.java` | Hidratar en `onCreate`; cambiar el `Supplier` de los 4 remotos |
| `ui/login/LoginActivity.java` | Redirigir si ya hay sesión; persistir al loguear |
| `MainActivity.java` | `cerrarSesion()` borra el almacén |

### 4.7 Pruebas

| # | Caso | Esperado |
|---|---|---|
| D1 | Guardar y leer una sesión | Vuelve idéntica |
| D2 | Leer con el almacén vacío | `null`, sin excepción |
| D3 | Texto cifrado corrupto a propósito | `null` + almacén limpio, **sin excepción** |
| D4 | El texto en disco **no** contiene el token en claro | Verificar la cadena literal |
| D5 | Token con 10 min de vida | `get()` lo devuelve sin llamar a la red |
| D6 | Token vencido | `get()` refresca y devuelve el nuevo |
| D7 | **6 hilos piden token vencido a la vez** | **Una** sola llamada de refresh |
| D8 | El refresh devuelve un `refresh_token` nuevo | Queda persistido |
| D9 | El refresh falla con 401 | `get()` → `null`, sesión borrada |
| D10 | El refresh falla sin red | `get()` → `null`, **sesión NO borrada** (es transitorio) |
| D11 | Arranque con sesión válida | `LoginActivity` redirige a `MainActivity` |
| D12 | Arranque con sesión vencida y refresh muerto | Se queda en el login |

D7 y D10 son los que atrapan los errores caros. D3 y D4 son los que hacen que esto cuente
como seguridad y no como decoración.

**D3, D4 y todo `AlmacenSeguro` necesitan Robolectric** (el `AndroidKeyStore` no existe en la
JVM) — ya está en el proyecto desde la 2b, sin dependencia nueva.

---

## 5. Entregables

| # | Entregable | Ítem | Pruebas |
|---|---|---|---|
| **E1** | `applicationId` + verificación con `aapt2` | P-018 | — |
| **E2** | `offset` en los 3 APIs y los 3 remotos | P-029 | ~6 |
| **E3** | Bucle del Menú portado a los 3 sincronizadores + `EjecutorDeTransaccion` | P-029 | ~15 (C1 ×3) |
| **E4** | `AlmacenSeguro` | P-009 | ~8 |
| **E5** | `Sesion`/DTOs/endpoint de refresh + `SesionLocal` | P-009 | ~8 |
| **E6** | `ProveedorDeToken` con single-flight | P-009 | ~10 |
| **E7** | Enganche: `SyncApplication`, `LoginActivity`, `MainActivity` | P-009 | ~6 |
| **E8** | Correcciones a la bóveda (§6) | todos | — |

**Piso de la suite:** hoy hay **345 tests**. Al cerrar: **≥ 400**.
`./gradlew testDebugUnitTest assembleDebug` en BUILD SUCCESSFUL + el
[[Gate de Autoverificación]] impreso ítem por ítem.

---

## 6. Correcciones a la bóveda (parte del entregable)

Dos notas prescriben hoy una API deprecada. Dejarlas así manda al próximo agente contra la
pared:

| Nota | Corrección |
|---|---|
| [[Deuda Técnica - Pendientes]] → P-009 | Reemplazar *"solución: `EncryptedSharedPreferences`"* por Keystore directo |
| [[Seguridad y Privacidad Android]] §1 y §7 | Ídem — dice *"los tokens se guardan en `EncryptedSharedPreferences`/Jetpack Security"* |
| [[Lista Negra de APIs Android]] | **Agregar** `androidx.security:security-crypto` (todas sus APIs) |
| [[Deuda Técnica - Pendientes]] | Etiquetas de plazo vencidas: P-009 *"requerido en Fase 2"*, P-015 *"antes de Fase 4"*, P-002/P-017 *"decidir en Fase 2"* |

Un patrón nuevo sale de acá y conviene registrarlo en `20 - Patrones`: **"Sesión persistida
con Android Keystore"**, porque se va a consultar cada vez que aparezca un dato sensible en
disco (**P-027**, clientes sin cifrar, es el próximo).

---

## 7. Lo que este plan NO hace

- **No arregla P-028** (los 7 `OkHttpClient`). El refresh proactivo lo esquiva a propósito, y
  la decisión queda documentada en §4.4.
- **No toca P-015** (Navigation Component). `LoginActivity` sigue siendo `LAUNCHER` y solo
  redirige. Meter Navigation acá desbordaría la rama.
- **No arregla P-025** (`now()` en el trigger): no hay Parte A. Va en la Parte A de la Fase 3.
- **No cifra la base de Room** (**P-027**). Es otro alcance, aunque `AlmacenSeguro` deja el
  camino hecho.

---

## Relaciones

- [[Deuda Técnica - Pendientes]] — P-018, P-004, P-009, P-029 (y P-010, que el checklist toca)
- [[Protocolo de Ejecución de un Plan]] · [[Gate de Autoverificación]] · [[Estándar de Ingeniería Android]]
- [[Plan Fase 3 - Pedidos en Tiempo Real]] — P-009 y P-029 la desbloquean
- [[Offline-First con Room y Outbox]] — la regla del delta que P-029 incumple
- [[Seguridad y Privacidad Android]] · [[Lista Negra de APIs Android]] — se corrigen acá
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]] — de donde sale que el refresh es manual
- [[Roadmap de Fases]] · [[Arquitectura Actual]]

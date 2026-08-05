---
title: "Sesión 2026-08-04 — La carga inicial del Menú y el trabajo único envenenado"
tags:
  - sesion
  - rendimiento
  - offline-first
  - workmanager
  - menu
date: 2026-08-04
branch: feat/fase2cd-mesas-clientes
autor_cambios: Claude (Opus 5)
---

# Sesión 2026-08-04 — La carga inicial del Menú y el trabajo único envenenado

## El reporte

> "Al instalar desde 0 y abrir Menú no cargó ni sincronizó al inicio, más bien se trabó.
> Le di manualmente y tardó 10 s en cargar, cargó lento los items y al rato cargó las
> imágenes."

Tres síntomas que parecían uno solo. Resultaron ser **dos causas independientes** más una
brecha de UX que hacía que se percibiera peor de lo que era.

---

## Causa A — la primera sincronización nunca corría

Una cadena de cuatro eslabones, cada uno razonable por su cuenta:

1. `SyncApplication:107` — `ProcessLifecycleOwner.onStart` encolaba el trabajo único
   `"sync-menu"` al pasar la app a primer plano. En instalación desde cero eso ocurre **en
   la pantalla de login, sin sesión**.
2. `SyncWorker:47-49` — sin token devolvía `Result.retry()`. El trabajo quedaba `ENQUEUED`
   con backoff exponencial: **vivo, no terminado**.
3. `SyncScheduler:71` — `ExistingWorkPolicy.KEEP`. Habiendo un trabajo vivo, **todo pedido
   posterior se descartaba en silencio**: el sync-on-launch de los ViewModels y el
   pull-to-refresh.
4. Nadie disparaba sync después del login.

Resultado: la primera bajada quedaba a merced de un backoff que había arrancado **antes**
del login — 15 s, 45 s, 1:45, 3:45… Los "10 s" que esperó el usuario fueron caer dentro de
una ventana de reintento, no el tiempo de la descarga.

> [!note] El comentario mentía
> El javadoc de `SyncScheduler:41-42` afirmaba que el sync-on-launch cubría la primera
> sincronización. Era exactamente lo que `KEEP` rompía. Un recordatorio de que un comentario
> describe la intención, no el comportamiento.

**Afectaba a los cuatro módulos**, no solo al Menú: Mesas, Clientes y Empleados llaman
`sincronizar()` en su constructor y pasan por el mismo trabajo único.

### El arreglo

El invariante que faltaba: **un worker que no puede hacer nada no debe retener el trabajo
único.** Sin token no hay trabajo posible, así que la pasada terminó — con éxito vacío.

| Archivo | Cambio |
|---|---|
| `SyncWorker` | `Result.retry()` → `Result.success()` sin token |
| `MainActivity` | Dispara `SyncScheduler.solicitar` tras confirmar sesión, solo si `savedInstanceState == null` (no en cada rotación) |
| `SyncApplication` | El empujón de foreground ahora chequea que haya sesión antes de encolar |

No se pierde nada al no reintentar: **las operaciones viven en `operaciones_pendientes`,
lo que se descarta es el disparador**, y hay cuatro caminos que lo recrean.

### Lo que NO se hizo, y por qué

`ExistingWorkPolicy.REPLACE` parecía el arreglo obvio. **Es el peor de los candidatos:**
cancela el worker en ejecución. Si lo mata entre el `POST /rest/v1/platillo` y el
`outbox.marcarExito`, la operación sigue en la cola y se vuelve a postear → **platillo
duplicado y foto huérfana en el bucket**. Y como el pull-to-refresh usa ese mismo camino,
cada swipe sería una oportunidad de duplicar. Queda prohibido hasta que el outbox tenga
clave de idempotencia.

---

## Causa B — ítems lentos y fotos tardías

**Ítems:** el delta insertaba **fila por fila sin transacción**. Cada insert invalida la
tabla → Room re-dispara las consultas observadas → se remapea la lista → se repinta el
`RecyclerView`. Con 50 platillos eran decenas de repintados, más 50 transacciones SQLite
con su `fsync`.

Arreglado con `EjecutorDeTransaccion`, una interfaz funcional de una línea. Se eligió eso y
no pasarle la `AppDatabase` al sincronizador porque este recibe DAOs sueltos a propósito —
los tests le pasan fakes en memoria, y pedirle la base rompía esa costura.

**Fotos:** **no existe ninguna configuración global de Glide** en el proyecto (cero
`@GlideModule`, nada en el manifest — fue una decisión deliberada para evitar el
`annotationProcessor`). Consecuencia no prevista: Glide baja por `HttpURLConnection` con un
**timeout por defecto de 2500 ms**. Con red intermitente la primera tanda expiraba entera y
solo se reintentaba al re-bindear la fila. *Eso* era el "al rato cargaron".

Arreglado con dos líneas en la cadena que ya existía: `.timeout(20_000)` y
`.diskCacheStrategy(ALL)`. **No hizo falta `AppGlideModule` ni annotationProcessor**:
`BaseRequestOptions.timeout()` actúa sobre el loader por defecto.

---

## La brecha de UX: la pantalla mentía

`MenuViewModel` ponía `EstadoMenu.cargando()`, pero `getEstadoSincronizacion()` es un
`MutableLiveData` con valor inicial `(false, null)` que emite apenas hay observador →
`recalcular()` → `conDatos(listaVacía)` → `isVacio()`. **El estado `cargando` moría antes
de pintarse** y el Menú mostraba "Todavía no hay platillos en el menú" como estado terminal
y falso.

Se agregó `esperandoPrimeraSincronizacion`, que se apaga en el **flanco de bajada** de la
sincronización (no basta con `isSincronizando() == false`: ese también es el valor inicial).
`isVacio()` quedó **intacto** a propósito — meterlo adentro habría roto `isVacioPorFiltro()`.

---

## El bug de correctitud que apareció de paso

La paginación del delta avanzaba **solo por marca de agua**: traer una página, subir la
marca al máximo visto, pedir `gt.<esa marca>`. Si ≥50 filas comparten `actualizado_en` —
**exactamente lo que produce sembrar el catálogo con un INSERT masivo**— la consulta
siguiente las excluye por definición y las filas 51 en adelante **no se bajan nunca**.

Se rompía justo en el escenario del reporte: instalación desde cero.

Arreglado: la marca queda **fija** durante la pasada, avanza el `offset`, la marca máxima se
guarda **al final**, el `order` lleva el id como desempate (sin orden total el `offset`
puede repetir o saltear) y hay un tope `MAX_PAGINAS` como cinturón — que además elimina el
bucle infinito potencial si una página llena venía sin `actualizado_en`.

Trade-off aceptado: un corte a mitad de pasada re-baja desde la marca vieja. Es inofensivo
porque aplicar filas es idempotente, y es el precio de no perder datos.

---

## Verificación

- `:app:assembleDebug` — ✅
- `:app:testDebugUnitTest` — ✅ **370 tests, 0 fallas** (357 antes; **13 nuevos**)
- Instalado en emulador y teléfono físico — ✅

Tests nuevos destacables:

| Test | Qué protege |
|---|---|
| `SyncWorkerTest.sinToken_terminaConExitoParaNoRetenerElTrabajoUnico` | La regresión de la causa A |
| `sinToken_noLlamaANingunSincronizador` | Que no se queme un intento con un 401 |
| `errorTransitorio_sigueDevolviendoRetry` | Que el cambio fue **solo** para el caso sin token |
| `delta_cincuentaFilasConLaMismaMarca_noPierdeLasQueSiguen` | El bug de paginación |
| `delta_servidorQueSiempreDevuelvePaginasLlenas_cortaPorElTopeDePaginas` | El bucle infinito |
| `delta_aplicaCadaPaginaEnUnaSolaTransaccion` | 1 transacción por página, no por fila |
| `sinPlatillosYSinHaberSincronizado_esEsperaYNoMenuVacio` | La pantalla que mentía |

Un test viejo se reescribió a propósito:
`delta_paginaCompleta_sigueConsultandoConLaMarcaDeAgua` afirmaba literalmente el
comportamiento que causaba la pérdida de filas. Ahora es
`delta_paginaCompleta_pideLaSiguienteConOffsetYLaMismaMarca`.

> [!warning] Verificación en dispositivo — pendiente de la persona
> Ningún agente de este proyecto puede iniciar sesión. Queda por confirmar a mano, con
> `adb uninstall` previo: que los platillos aparezcan en la primera pasada sin esperar 15 s,
> que mientras carga se lea "Cargando el menú…" y nunca "Todavía no hay platillos", y que
> las fotos entren durante el primer scroll. La métrica a reportar es **tiempo desde el
> login hasta el primer platillo pintado**, como pide
> [[Presupuestos de Rendimiento en Gama Baja]].

---

## Lo que quedó afuera (P-028)

`OkHttpClient` único (hoy se crea uno por cada uno de los 7 APIs), caché HTTP, timeouts
incompletos, thumbnails del lado del servidor y estado de sincronización derivado de
`WorkInfo`. Registrado en [[Deuda Técnica - Pendientes]].

---

## Relaciones

- [[Offline-First con Room y Outbox]] — actualizado con las tres reglas que salieron de acá
- [[Módulo Menú]]
- [[Presupuestos de Rendimiento en Gama Baja]]
- [[Deuda Técnica - Pendientes]] — P-028
- [[Sesión 2026-08-04 - Módulo Menú rediseñado contra el mockup v2]] — el trabajo previo del día

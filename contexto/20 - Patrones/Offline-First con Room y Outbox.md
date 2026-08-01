---
title: "Offline-First con Room y Outbox"
tags:
  - patron
  - offline
  - sincronizacion
  - room
date: 2026-07-29
lifecycle: draft
---

# Offline-First con Room y Outbox

> [!abstract] Principio
> El requisito "que funcione con poco internet" **no se resuelve con timeouts más largos: se resuelve con arquitectura.** La app debe ser plenamente usable sin red, y sincronizar cuando pueda.

## Por qué es requisito #1 en este proyecto

Un restaurante en Honduras con Wi-Fi intermitente no puede depender de la red para tomar un pedido. Si el mesero no puede registrar una orden porque "no hay señal", la app no sirve — vuelven al papel.

---

## Las 8 reglas

### 1. Room es la única fuente de verdad

La UI **observa Room**, nunca la red. La red solo **actualiza** Room.

```
UI ←── LiveData ←── Room ←── (actualiza) ←── Red
```

Nunca: `UI ←── Red`.

### 2. Escritura optimista

La operación del usuario se escribe local **de inmediato** con `sync_state = PENDIENTE`. La UI responde al instante. `WorkManager` sincroniza después.

El mesero toca "Agregar al pedido" y el ítem aparece **ya**, haya red o no.

### 3. Cola de salida (outbox)

```sql
operaciones_pendientes(
    id INTEGER PRIMARY KEY,
    tipo TEXT,            -- CREAR_PEDIDO, ACTUALIZAR_MESA, …
    payload_json TEXT,
    intentos INTEGER,
    ultimo_error TEXT,
    created_at INTEGER
)
```

Un **`SyncWorker` único** (`ExistingWorkPolicy.KEEP`) la drena con `Constraints.NETWORK_CONNECTED` y backoff exponencial. Único = nunca dos workers compitiendo por la misma cola.

### 4. Sync delta, no full

El cliente guarda `last_sync_at` y pide **solo los cambios**:

```
GET /rest/v1/productos?select=id,nombre,precio&updated_at=gt.{last_sync_at}
```

**Nunca descargar la tabla completa.** En 3G, bajar 500 productos cada vez que se abre la app es la diferencia entre 2 segundos y 40.

### 5. Resolución de conflictos declarada

Por defecto *last-write-wins* con el `updated_at` **del servidor**. Si el dominio no lo tolera (ej. stock), `ConflictResolver` explícito y documentado. Lo que no se vale es no decidir: el conflicto va a ocurrir.

### 6. Borrado lógico

`deleted = true`, nunca `DELETE` físico — si no, el borrado no se puede propagar a los otros dispositivos.

### 7. Migraciones explícitas y probadas

`MigrationTestHelper` para cada migración. **`fallbackToDestructiveMigration()` está prohibido en release**: borra los datos del usuario sin avisar. Ver [[Lista Negra de APIs Android]].

### 8. Estado visible para el usuario

La UI muestra **siempre** si el dato está pendiente de sincronizar, sincronizado, o en error. El usuario nunca queda con la duda de "¿se guardó o no?".

Un ícono de nube con tres estados en cada fila resuelve el 90% de los reclamos de soporte.

---

## Red con poco ancho de banda

| Regla | Valor |
|---|---|
| Timeouts | connect 15 s · read 30 s · write 30 s · callTimeout 45 s. **Nunca infinitos** |
| Reintentos | Backoff exponencial + jitter, máx. 3, **solo** en errores transitorios (timeout, 5xx, sin red) |
| POST no idempotente | **Nunca** reintentar sin *idempotency key* — se duplican pedidos |
| Caché HTTP | OkHttp ~10 MB + `max-stale` cuando no hay red |
| Payload | `?select=id,nombre,precio` siempre; paginación ≤ 50 filas |
| Imágenes | Comprimidas y redimensionadas **en el dispositivo** antes de subir |
| Ahorro de datos | Respetar `ConnectivityManager.getRestrictBackgroundStatus` |
| Deduplicación | Una sola llamada por recurso, aunque la pidan 3 pantallas a la vez |

---

## Estado en este proyecto

> [!success] Implementado (2026-08-01) — **P-014 cerrado**
> **Menú** y **Empleados** son local-first: la UI observa Room y el `SyncWorker` drena el
> outbox y baja el delta. Ver [[Módulo Menú]] y [[Módulo Empleados]].
>
> Infraestructura: `data/local` (Room 2.8.4, esquema v2), `data/outbox` **particionado por
> módulo** y `data/sync` con un **worker único** — la regla 3 de esta misma nota.
>
> El **login** queda fuera por definición: autenticar exige red. Lo que le falta es persistir
> la sesión, que es **P-009**, no cachearla.

> [!tip] La advertencia se cumplió, y salió barata por poco
> Esta nota decía que retro-adaptar offline-first sobre módulos ya escritos contra la red
> *"es una reescritura, no un refactor"*. Pasó exactamente eso: la Fase 2a escribió el Menú
> contra la red a propósito, y la 2b **reescribió** su capa `data` completa. Se pudo pagar
> porque eran dos módulos, no cinco. Mesas, Clientes y Pedidos nacen ya sobre esta base.

---

## Relaciones

- [[Repository Pattern]] — el repositorio es quien orquesta local + remoto
- [[Catálogo de Patrones Android]] — Strategy para `ConflictResolver` y `RetryPolicy`
- [[Presupuestos de Rendimiento en Gama Baja]]
- [[Result Pattern]]
- [[Roadmap de Fases]] — Fase 2
- [[Deuda Técnica - Pendientes]] — P-014

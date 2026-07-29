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

> [!warning] No implementado — es el gran pendiente arquitectónico
> Hoy la app **no tiene Room, ni WorkManager, ni outbox**. El login va directo a la red y falla si no hay conexión; no hay nada que cachear todavía porque no hay más pantallas.
>
> **Esto debe implementarse en la Fase 2 (Menú)**, no después: retro-adaptar offline-first sobre 5 módulos ya escritos contra la red es una reescritura, no un refactor. Registrado como **P-014** en [[Deuda Técnica - Pendientes]].

---

## Relaciones

- [[Repository Pattern]] — el repositorio es quien orquesta local + remoto
- [[Catálogo de Patrones Android]] — Strategy para `ConflictResolver` y `RetryPolicy`
- [[Presupuestos de Rendimiento en Gama Baja]]
- [[Result Pattern]]
- [[Roadmap de Fases]] — Fase 2
- [[Deuda Técnica - Pendientes]] — P-014

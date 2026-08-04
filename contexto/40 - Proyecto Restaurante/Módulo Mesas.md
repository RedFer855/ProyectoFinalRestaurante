---
title: Módulo Mesas
tags:
  - restaurante
  - modulo
  - mesas
  - fase2c
date: 2026-08-03
---

# Módulo Mesas

> [!success] Funcional desde 2026-08-03 (Fase 2c) · local-first desde 2026-08-03 (Fase 2c)
> CRUD de mesas contra Supabase, con estados operativos (Libre/Ocupada/Reservada) y baja
> lógica (nunca borrado físico). Reemplazó la maqueta de la Fase 1c.
>
> La UI lee de Room y escribe optimista; el `SyncWorker` drena el outbox y baja el delta.

## Qué hace

| # | Historia | Rol |
|---|---|---|
| 1 | Ver la grilla de mesas con color por estado | admin, mesero |
| 2 | Filtrar por estado y buscar por número o ubicación | admin, mesero |
| 3 | Cambiar el estado operativo (Libre ↔ Ocupada ↔ Reservada) | admin, mesero |
| 4 | Crear una mesa (número, capacidad, ubicación) | solo admin |
| 5 | Editar datos de una mesa | solo admin |
| 6 | Dar de baja / reactivar una mesa (activo = false/true) | solo admin |
| 7 | Nunca borrar físicamente una mesa | — |

Los dos roles usan **la misma pantalla**; lo que cambia es qué controles se muestran,
vía `Permisos` + `VistaPorPermiso`. Eso es experiencia de usuario, **no seguridad**: quien
impide que un mesero cree una mesa es la policy RLS de Postgres.

## Archivos clave

### `domain/`
```
model/Mesa.java            — idLocal, idServidor, numeroMesa, capacidad, ubicacion,
                             estadoMesa, activo, estadoSync
model/NuevaMesa.java       — lo que se manda al crear/editar (sin id, sin estado, sin fecha)
model/EstadoMesa.java      — enum LIBRE/OCUPADA/RESERVADA con idServidor
ValidadorMesa.java         — numero > 0, capacidad > 0 → Set<ErrorMesa>
ReglasMesa.java            — espejo en el cliente de las reglas del servidor
repository/MesaRepository.java — el contrato: observar, crear, editar, cambiarEstado, etc.
```

### `data/`
```
remote/SupabaseMesaApi.java      — PostgREST: lee de vista_mesas, escribe en mesa, RPC
remote/dto/MesaDto.java          — lee de la vista (con joins)
remote/dto/CrearMesaDto.java     — POST a mesa
remote/dto/ActualizarMesaDto.java — PATCH parcial con factories
remote/dto/CambiarEstadoMesaDto.java — RPC cambiar_estado_mesa
repository/MesaRepositorioLocal.java — Room + outbox (Fase 2c)
repository/MesaRemoto.java          — la cara remota; orquesta base
local/entity/MesaEntity.java       — Room entity
local/dao/MesaDao.java             — observarTodas, porIdLocal, porIdServidor, etc.
local/mapper/MesaMapper.java       — conversión Entity ↔ Dominio ↔ DTO
```

### `ui/mesas/`
```
MesasFragment.java         — grilla con GridLayoutManager, chips de filtro, FAB
MesaAdapter.java           — ListAdapter + DiffUtil; color + texto por estado
MesasViewModel.java        — LiveData<EstadoMesas>; filtro y búsqueda viven acá
EstadoMesas.java           — estado único inmutable (cargando/datos/vacío/error)
MesasViewModelFactory.java — DI manual (P-002)
```

### Recursos
```
layout/fragment_mesas.xml · item_mesa.xml
values/colors.xml          — estado_mesa_libre, estado_mesa_ocupada, estado_mesa_reservada
values-night/colors.xml    — variantes oscuras
values/strings.xml         — estado_mesa_libre/ocupada/reservada, mesas_*
```

## Contra qué habla — servidor

Aplicado el 2026-08-03, ver [[Esquema de Base de Datos]].

- Se **lee** de `vista_mesas` (`security_invoker = on`), que resuelve el nombre del estado.
- Se **escribe** en la tabla `mesa` (admin) y por RPC `cambiar_estado_mesa` (admin + mesero).
- `SELECT` para cualquier rol con sesión; `INSERT`/`UPDATE`/`DELETE` solo para `admin`.
- La RLS del mesero **no tiene** `UPDATE` sobre `mesa`: escribe únicamente por el RPC.

### Reglas que impone el servidor y la app espeja

| Objeto del servidor | Espejo en el cliente |
|---|---|
| `CHECK (numero_mesa > 0)` implícito | `ValidadorMesa` |
| `uq_mesa_numero` | `ReglasMesa.existeOtraMesaConNumero` |
| `trg_mesa_no_borrar` | `ReglasMesa.puedeBorrarse` siempre `false` |
| `cambiar_estado_mesa` rechaza mesa inactiva | `ReglasMesa.puedeCambiarEstado` |
| `cambiar_estado_mesa` valida estado existente | `EstadoMesa.porId` |
| `trg_mesa_actualizado_en` | la app **no manda** ese campo |

## Offbox y sync

| Operación | Tipo de outbox | Módulo |
|---|---|---|
| Crear mesa | `CREAR_MESA` | `MESAS` |
| Editar datos | `ACTUALIZAR_MESA` | `MESAS` |
| Cambiar estado | `CAMBIAR_ESTADO_MESA` | `MESAS` |
| Dar de baja / reactivar | `CAMBIAR_BAJA_MESA` | `MESAS` |

El `SincronizadorMesa` drena la cola FIFO y baja el delta con marca de agua.
Conflicto last-write-wins: si el servidor tiene una marca más reciente que el local,
el local se sobreescribe y la operación pendiente se descarta.

## Trampas conocidas

1. **El filtro que esconde lo que acabás de hacer**: cambiar el estado con un filtro
   activo hace desaparecer la mesa. `MesasViewModel.descartarFiltroQueEsconde` la suelta.
2. **El RPC no devuelve la fila**: `cambiar_estado_mesa` devuelve `void`. La escritura
   local ya ocurrió; el sync la sube después.
3. **`estado_mesa` es un catálogo**: no se pide al servidor en cada apertura; se cachea
   en Room.

## Relaciones

- [[Plan Fase 2c - CRUD de Mesas]]
- [[Plan Fase 2b - Offline-First con Room y Outbox]]
- [[Módulo Menú]] — patrón replicado
- [[Esquema de Base de Datos]]
- [[Deuda Técnica - Pendientes]]
- [[Roadmap de Fases]]

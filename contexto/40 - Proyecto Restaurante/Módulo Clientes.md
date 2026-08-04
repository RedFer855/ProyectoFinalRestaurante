---
title: Módulo Clientes
tags:
  - restaurante
  - modulo
  - clientes
  - fase2d
date: 2026-08-03
---

# Módulo Clientes

> [!success] Funcional desde 2026-08-03 (Fase 2d) · local-first desde 2026-08-03 (Fase 2d)
> CRUD de clientes contra Supabase, con datos personales (nombre, identidad, teléfono)
> y baja lógica (activo = false). Reemplazó la maqueta de la Fase 1c.
>
> La UI lee de Room y escribe optimista; el `SyncWorker` drena el outbox y baja el delta.

## Qué hace

| # | Historia | Rol |
|---|---|---|
| 1 | Ver la lista de clientes con nombre, identidad y teléfono | admin, mesero |
| 2 | Filtrar por estado (activo/inactivo) y buscar por nombre, apellido, identidad o teléfono | admin, mesero |
| 3 | Crear un cliente (nombre, apellido, identidad, teléfono) | admin, mesero |
| 4 | Editar datos de un cliente | solo admin |
| 5 | Dar de baja / reactivar un cliente (activo = false/true) | solo admin |
| 6 | Borrar físicamente un cliente (solo si tiene 0 pedidos) | solo admin |

Los dos roles usan **la misma pantalla**; lo que cambia es qué controles se muestran,
vía `Permisos` + `VistaPorPermiso`. Eso es experiencia de usuario, **no seguridad**: quien
impide que un mesero borre un cliente es la policy RLS de Postgres.

## Archivos clave

### `domain/`
```
model/Cliente.java            — idLocal, idServidor, nombre, apellido, identidad,
                                telefono, activo, cantidadPedidos, estadoSync
model/NuevoCliente.java       — lo que se manda al crear/editar (sin id, sin estado)
ValidadorCliente.java         — nombre y apellido obligatorios, identidad ≥ 13 dígitos
ReglasCliente.java            — espejo en el cliente de las reglas del servidor
repository/ClienteRepository.java — el contrato: observar, crear, editar, borrar, etc.
```

### `data/`
```
remote/SupabaseClienteApi.java      — PostgREST: lee de vista_clientes, escribe en cliente
remote/dto/ClienteDto.java          — lee de la vista (con id_estado, activo, cantidad_pedidos)
remote/dto/CrearClienteDto.java     — POST a cliente
remote/dto/ActualizarClienteDto.java — PATCH parcial con factories
repository/ClienteRepositorioLocal.java — Room + outbox (Fase 2d)
repository/ClienteRemoto.java          — la cara remota; orquesta base
local/entity/ClienteEntity.java       — Room entity
local/dao/ClienteDao.java             — observarTodos, porIdLocal, porIdServidor, etc.
local/mapper/ClienteMapper.java       — conversión Entity ↔ Dominio ↔ DTO
```

### `ui/clientes/`
```
ClientesFragment.java         — lista con RecyclerView, chips de filtro, FAB
ClienteAdapter.java           — ListAdapter + DiffUtil; muestra nombre, identidad, teléfono
ClientesViewModel.java        — LiveData<EstadoClientes>; filtro y búsqueda viven acá
EstadoClientes.java           — estado único inmutable (cargando/datos/vacío/error)
ClientesViewModelFactory.java — DI manual (P-002)
```

### Recursos
```
layout/fragment_clientes.xml · item_cliente.xml
values/strings.xml            — clientes_buscar, clientes_agregar, clientes_vacio, etc.
```

## Contra qué habla — servidor

Aplicado el 2026-08-03, ver [[Esquema de Base de Datos]].

- Se **lee** de `vista_clientes` (`security_invoker = on`), que resuelve el estado y cuenta pedidos.
- Se **escribe** en la tabla `cliente` (admin) y por RPC `buscar_o_crear_cliente` (mesero en pedido).
- `SELECT` para cualquier rol con sesión; `INSERT`/`UPDATE`/`DELETE` solo para `admin`.
- `buscar_o_crear_cliente` retorna el id del cliente (nuevo o existente) y es la única escritura del mesero.

### Datos personales — consideraciones

- **nombre** y **apellido** son NOT NULL en la tabla.
- **identidad** se normaliza antes de guardar: solo dígitos, sin guiones ni espacios.
- **telefono** es opcional.
- El trigger `trg_clientes_no_borrar_con_pedidos` impide DELETE si tiene pedidos.
- El trigger `trg_clientes_actualizado_en` actualiza el timestamp automáticamente.

### Reglas que impone el servidor y la app espeja

| Objeto del servidor | Espejo en el cliente |
|---|---|
| `NOT NULL` en nombre y apellido | `ValidadorCliente` |
| `regexp_replace(identidad, '[^0-9]', '', 'g')` | `ReglasCliente.normalizarIdentidad` |
| `trg_clientes_no_borrar_con_pedidos` | `ReglasCliente.puedeBorrarse` (cantidadPedidos == 0) |
| `trg_clientes_actualizado_en` | la app **no manda** ese campo |

## Outbox y sync

| Operación | Tipo de outbox | Módulo |
|---|---|---|
| Crear cliente | `CREAR_CLIENTE` | `CLIENTES` |
| Editar datos | `ACTUALIZAR_CLIENTE` | `CLIENTES` |
| Cambiar estado (activo/inactivo) | `CAMBIAR_ESTADO_CLIENTE` | `CLIENTES` |
| Borrar físicamente | `BORRAR_CLIENTE` | `CLIENTES` |

El `SincronizadorCliente` drena la cola FIFO y baja el delta con marca de agua.
Conflicto last-write-wins: si el servidor tiene una marca más reciente que el local,
el local se sobreescribe y la operación pendiente se descarta.

## Trampas conocidas

1. **El filtro que esconde lo que acabás de hacer**: cambiar el estado con un filtro
   activo hace desaparecer el cliente. `ClientesViewModel` suelta el filtro cuando
   la operación tiene éxito.
2. **La identidad no es única a nivel de app**: dos clientes pueden tener la misma
   identidad si el servidor lo permite. `ReglasCliente.existeOtroConIdentidad` es un
   guard, no una restricción de integridad.
3. **`buscar_o_crear_cliente` es idempotente**: si el mesero busca por identidad y ya
   existe, retorna el id existente. No crea duplicados.

## Relaciones

- [[Plan Fase 2d - CRUD de Clientes]]
- [[Plan Fase 2b - Offline-First con Room y Outbox]]
- [[Módulo Menú]] — patrón replicado
- [[Módulo Mesas]] — patrón replicado
- [[Esquema de Base de Datos]]
- [[Deuda Técnica - Pendientes]]
- [[Roadmap de Fases]]

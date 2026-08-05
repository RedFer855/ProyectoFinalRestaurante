---
title: Módulo Pedidos
tags:
  - restaurante
  - modulo
  - pedidos
  - offline-first
date: 2026-08-05
lifecycle: verified
---

# Módulo Pedidos

> [!success] Estado
> 🟢 **Funcional y local-first** (2026-08-05). El tablero en tiempo real es de la
> [[Plan Fase 3 - Pedidos en Tiempo Real]] (Fase 3); la **toma del pedido** que crea pedidos
> nuevos con carrito, mesa y cliente es de la [[Plan Fase 3b - Toma del Pedido]] (Fase 3b,
> Parte B completa: 625 tests, BUILD SUCCESSFUL). La Parta A (servidor, RPC `crear_pedido`,
> migraciones) queda fuera del alcance de esta sesión de codificación.

---

## Qué hace

| Historia | Offline | Quién |
|---|---|---|
| Ver el tablero (número, referencia, hora, total, estado) | ✅ lee de Room | admin, mesero, cocina |
| Avanzar el estado (Pendiente → En preparación → Listo → Entregado / Cancelado) | ✅ se encola por RPC | admin, **cocinero** |
| **Tomar un pedido nuevo** (carrito + mesa + cliente + tipo) | ✅ se encola `CREAR_PEDIDO` | `PEDIDOS/CREAR` (admin, **mesero**) |
| Ver el detalle de un pedido (líneas + precios sellados) | ✅ bajo demanda | los tres |
| Buzón de notificaciones con contador | ✅ | los tres |

El editar un pedido **no es parte de la Fase 3b** (ver [[Plan Fase 3b - Toma del
Pedido]] §1.2 y la deuda "editar pedido" en [[Deuda Técnica - Pendientes]]).

---

## Arquitectura

```
ui/pedidos/          PedidosFragment · PedidoAdapter · PedidosViewModel · EstadoPedidos
                     PedidosViewModelFactory · EstadoPedidoUi
ui/nuevopedido/      NuevoPedidoFragment · CarritoHoja · SelectorPlatilloHoja
                     SelectorMesaHoja · SelectorClienteHoja · LineaCarritoAdapter
                     Selector*Adapter · NuevoPedidoViewModel · EstadoNuevoPedido
                     NuevoPedidoViewModelFactory · TipoPedidoUi · ErrorNuevoPedidoUi
ui/detallepedido/    DetallePedidoHoja · LineaPedidoAdapter · DetallePedidoViewModel
                     EstadoDetallePedido · DetallePedidoViewModelFactory
domain/              model/Carrito · LineaCarrito · NuevoPedido · TipoPedido · LineaPedido
                     Pedido · EstadoPedido · TipoNotificacion
                     ValidadorPedido · ReglasPedido · repository/PedidoRepository
data/local/          entity/PedidoEntity · entity/DetallePedidoEntity · dao/DetallePedidoDao
                     mapper/DetallePedidoMapper · dao/PedidoDao
data/repository/     PedidoRepositorioLocal (local-first) · PedidoRemoto (red)
data/sync/           SincronizadorPedidos · payload/PayloadCrearPedido · PayloadOperacion
```

---

## La toma del pedido (Fase 3b)

### Flujo

Interactivo desde el tablero: el **FAB "+ Nuevo pedido"** (visible con `PEDIDOS/CREAR`) abre
`NuevoPedidoFragment`. El `NuevoPedidoViewModel` vive en su scope y aguanta una rotación; las
4 hojas (carrito y los 3 selectores) reciben **el mismo** ViewModel por `recibir(vm)`, así el
carrito no se pierde al navegar.

```
FAB → NuevoPedidoFragment
        ├─ selector Tipo (En mesa / Para llevar)
        ├─ SelectorPlatilloHoja  → agregarPlatillo (platillos filtrados por puedePedirse)
        ├─ SelectorMesaHoja      → seleccionarMesa  (lee Room, módulo Mesas)
        ├─ SelectorClienteHoja   → seleccionarCliente (lee Room, módulo Clientes)
        ├─ CarritoHoja           → cambiarCantidad/quitarPlatillo · subtotal
        └─ confirmar()           → ValidadorPedido → repositorio.crear → vuelta al tablero
```

### Reglas de negocio (dominio)

- **Reglas**: `ValidadorPedido.validar(nuevoPedido)` valida carrito no vacío, **máx. 50
  líneas** y cantidades válidas. `ReglasPedido.puedePedirse(platillo)` filtra activos y
  visibles para el selector.
- **`puedeConfirmar`** (en `EstadoNuevoPedido`) es derivado: carrito no vacío y pedido válido.
  El **admin y el mesero** pueden tomar pedidos; cocina no (`PEDIDOS.CREAR`).
- **Regla de tipo** — `TipoPedido`: `EN_MESA` pide mesa; `PARA_LLEVAR` puede ir sin mesa. El cliente
  es opcional ([[ADR-006 - Clientes sin cuenta propia, captura de datos al pedido]]).

### Escritura y resolución de ids

1. `NuevoPedidoViewModel.confirmar()` genera un `UUID` como clave de idempotencia.
2. `PedidoRepositorioLocal.crear()` escribe en **una transacción de Room**: la cabecera
   `PENDIENTE` + las N líneas `DetallePedidoEntity` + **una** fila `CREAR_PEDIDO` en el outbox,
   y llama `sincronizar()`. (atómica, caso de aceptación B1).
3. El `SincronizadorPedidos` drena por orden global. Al resolver el `CREAR_PEDIDO` (ADR-009):
   - si el pedido referencia una mesa/cliente con `idServidor` nulo, declara `PEDIDO_SIN_MESA`/
     `PEDIDO_SIN_CLIENTE`; un platillo con `idPlatillo<=0` → el pedido va a **ERROR**.
   - el RPC `crear_pedido` con la clave de idempotencia (B2) devuelve el mismo id si se repite.
   - B3/B4/B5: orden de sincronizadores (el de Clientes drena antes).
4. La marca de agua se solapa **−2 s** (ADR-011) para no perder la cabecera en el corte.

### El detalle bajo demanda (E9)

`PedidoAdapter` abre `DetallePedidoHoja` al tocar una tarjeta. La carga es **bajo demanda**:
el tablero no baja líneas hasta que se abre la hoja; `PedidoRepository.observarDetalle(id)`
alimenta la hoja con `LineaPedido` (incl. el **precio sellado por el servidor**, ADR-010).

---

## Arquitectura — sync y RPC de escritura

- El outbox de Pedidos vive en el **Módulo de la partición `Pedidos`** (como el tablón de la
  Fase 3) — `Outbox(TipoOperacion.Modulo.PEDIDOS)`.
- `SyncApplication` ordena: se **pasa el outbox de Clientes al `SincronizadorPedidos`** para
  que drene antes y resuelva `idCliente` cuando el pedido cuyo cliente recién se creó offline
  (B3/B4/B5).
- El sincronizador del delta es un **reloj `clock_timestamp()` + solapamiento −2 s** (ADR-011);
  re-aplicar es idempotente (upsert por `idServidor` + LWW), caso B8.
- El `ClasificadorDeError` trata el 400 del RPC como permanente: el outbox descarta el pedido
  inválido en vez de reintentar 3 veces (ADR-009).

### Permisos del módulo

De `Permisos.java`:

| Acción | admin | mesero | cocina |
|---|---|---|---|
| `VER` | ✅ | ✅ | ✅ |
| `CREAR` (tomar pedido) | ✅ | ✅ | ❌ |
| `EDITAR` | ✅ | ✅ | ❌ (sin editar en 3b) |
| `CAMBIAR_ESTADO` | ✅ | ❌ | ✅ |
| `ELIMINAR` (cancelar) | ✅ | ❌ | ❌ |

El `Fab` del tablero usa `VistaPorPermiso.aplicar(fab, Modulo.PEDIDOS, Accion.CREAR)` y el
`EstadoPedido` (el chip) permite avanzar estado solo si `CAMBIAR_ESTADO`. La matriz fina de
transiciones la vuelve a validar el ViewModel con `ReglasPedido`.

---

## Notas

- **Sin la Fase 3c**: esta entrega no cubre el dashboard/reportes de la Fase 3c; la deuda
  **"editar pedido"** queda abierta en `[[Deuda Técnica]]`.
- La parte A (servidor: RPC, RLS, migraciones) se levanta en una sesión con acceso a Supabase
  — ver [[Esquema de Base de Datos]].

---

## Relaciones

- [[Plan Fase 3 - Pedidos en Tiempo Real]] (el tablero base) · [[Plan Fase 3b - Toma del
  Pedido]] (la toma) · [[Plan Fase 3c - Dashboard y Reportes]] (siguiente)
- [[ADR-009 - RPC transaccional con clave de idempotencia para crear un pedido]] ·
  [[ADR-010 - El servidor sella el precio y el dispositivo estima]] ·
  [[ADR-011 - El cursor del sync delta es un reloj clock_timestamp mas solapamiento]]
- [[Deuda Técnica - Pendientes]] (cierra P-025/026, abre P-030 + "editar pedido")
- [[Módulo Clientes]] · [[Módulo Mesas]] · [[Módulo Menú]] · [[Esquema de Base de Datos]]
---
title: "Sesión 2026-08-01 — Fase 2c y 2d completas, Parte B: Mesas y Clientes"
tags:
  - sesion
  - restaurante
  - fase2c
  - fase2d
  - mesas
  - clientes
  - offline-first
date: 2026-08-01
branch: feat/fase2cd-mesas-clientes
autor_cambios: claude
---

# Sesión 2026-08-01 — Fase 2c y 2d completas, Parte B: Mesas y Clientes

> [!success] Resultado
> [[Plan Fase 2c - CRUD de Mesas]] y [[Plan Fase 2d - CRUD de Clientes]] completos en su
> **Parte B** (código Android): domain, data (Room + remoto), sincronizador, ViewModel, UI y
> pruebas de los dos módulos. **345 tests** en verde (eran 243), `./gradlew testDebugUnitTest
> assembleDebug` → **BUILD SUCCESSFUL**.
>
> **La Parte A (servidor) no se ejecutó en ninguna de las dos fases** — sin acceso a
> Supabase en esta sesión. Ver [[Módulo Mesas]] y [[Módulo Clientes]] para el detalle exacto
> de qué falta correr contra la base real antes de que cualquiera de los dos módulos
> funcione de punta a punta.

---

## Punto de partida: qué había y qué se pidió

El usuario pidió revisar qué había quedado de una ejecución anterior de **opencode**, que se
había "quedado trabado a media ejecución". La revisión encontró:

- **E1 y E2 del Plan Fase 2c** (capa `domain` y `data` remota de Mesas) completos y
  documentados en [[Sesión 2026-08-01 - E1 del Plan Fase 2c - capa domain de Mesas]] — 243
  tests en verde.
- **E3 de Mesas** (`MesaEntity`, `MesaDao`, `MesaMapper`, migración v2→v3, sus tests) ya
  escrito en el árbol de trabajo, **sin commitear y sin bitácora**: es el punto exacto donde
  opencode se cortó. El código en sí estaba completo y correcto — se revisó a fondo (DAO,
  mapper, migración, sus 17 tests) y no se encontró ningún bug; el problema era solo que
  faltaba documentarlo y seguir con E4 en adelante.

El usuario pidió entonces terminar **toda la Fase 2c y toda la Fase 2d**, con todos sus
entregables, dependiendo de lo que la revisión encontrara.

---

## Fase 2c — Mesas: E4 a E7

- **E4** — `data/repository/MesaRemoto.java` (ejecutor de red), `MesaRepositorioLocal.java`
  (implementa `MesaRepository` + `ObservadorSincronizacion`), `data/sync/SincronizadorMesas.java`.
  Wiring en `SyncApplication` (outbox particionado, worker factory).
- **E5** — `ui/mesas/MesasViewModel.java`, `EstadoMesas.java`, `MesasViewModelFactory.java`.
- **E6** — `MesaAdapter.java` y `MesasFragment.java` reescritos contra el dominio real (ya no
  `DatosMaqueta.Mesa`); `FormularioMesaDialog.java` nuevo; `EstadoMesaUi.java` nuevo (mapea
  `EstadoMesa` → `@StringRes`/`@ColorRes`, porque el dominio no puede ver `R`); layouts
  `dialog_mesa.xml`, `menu_mesa.xml`; `item_mesa.xml` y `fragment_mesas.xml` actualizados con
  indicador de sincronización, búsqueda y refresco.
- **E7** — `MesaRemotoTest` (11 casos) y `MesasViewModelTest` (19 casos) nuevos, sumados a los
  26 de `Validador`/`Reglas`/`EstadoMesa` y los 17 de Room que ya existían de E1-E3.
  **73 tests** en total para el módulo.
- `DatosMaqueta.Mesa`, `DatosMaqueta.EstadoMesa` y `DatosMaqueta.mesas()` eliminados.

### Decisiones de diseño no cubiertas por el plan al pie de la letra

- **El catálogo `estado_mesa` se siembra local, no se sincroniza.** El plan (§5.4) pedía
  cachear el catálogo, pero el contrato HTTP (§3) no define un endpoint para listarlo. Se
  siembra con los tres valores fijos de `EstadoMesa` en el constructor de
  `MesaRepositorioLocal`. Documentado en el Javadoc de la clase y en [[Módulo Mesas]].
- **Cambiar el estado de una mesa que todavía no se subió.** `CrearMesaDto` no lleva
  `id_estado_mesa` (el servidor default a Libre). Si el mesero cambia el estado antes del
  primer sync, `SincronizadorMesas.crearMesa()` hace un segundo viaje al RPC después del
  `POST` para no perder el cambio. La baja/alta lógica **no** tiene esta compensación —
  se juzgó un caso demasiado raro para la complejidad extra.
- **`ADR-007 - Estados operativos en catálogos propios, separados de estado_general`**
  escrito, con estado `propuesto` (no `aceptado`): documenta la decisión que la Parte A
  todavía no ejecutó.

---

## Fase 2d — Clientes: E1 a E7 completo

Módulo construido desde cero, mismo patrón:

- **E1** — `domain/model/Cliente.java`, `NuevoCliente.java`, `ValidadorCliente.java`
  (nombre/apellido obligatorios, identidad opcional con mínimo 13 dígitos tras normalizar),
  `ReglasCliente.java` (`normalizarIdentidad` espeja el `regexp_replace` del servidor,
  `puedeBorrarse` por `cantidadPedidos`), `repository/ClienteRepository.java`.
- **E2** — `SupabaseClienteApi`, `ClienteDto`, `CrearClienteDto`, `ActualizarClienteDto`,
  `BuscarOCrearClienteDto`. `SupabaseClient.getClienteApi()`.
- **E3** — `ClienteEntity`, `ClienteDao` (con `@Delete`, a diferencia de Mesas), `ClienteMapper`,
  outbox (`CREAR_CLIENTE`/`ACTUALIZAR_CLIENTE`/`CAMBIAR_ESTADO_CLIENTE`/`BORRAR_CLIENTE`),
  migración Room v3→v4 (`DE_3_A_4`).
- **E4** — `ClienteRemoto.java`, `ClienteRepositorioLocal.java`, `SincronizadorClientes.java`.
  Wiring en `SyncApplication`.
- **E5** — `ClientesViewModel.java`, `EstadoClientes.java`, `ClientesViewModelFactory.java`.
  Filtro por activo/inactivo además de la búsqueda (ver trampa del §5.5 más abajo).
- **E6** — `ClienteAdapter.java` y `ClientesFragment.java` reescritos contra el dominio real;
  `FormularioClienteDialog.java` nuevo; `dialog_cliente.xml` nuevo; `item_cliente.xml` y
  `fragment_clientes.xml` actualizados (chips de estado/sync, búsqueda, refresco, filtro).
- **E7** — `ValidadorClienteTest` (7, incluida la normalización), `ReglasClienteTest` (7),
  `ClienteMapperTest` (6), `ClienteDaoTest` (7), `ClienteRemotoTest` (11),
  `ClientesViewModelTest` (16). **55 tests** para el módulo completo.
- `DatosMaqueta.Cliente` y `DatosMaqueta.clientes()` eliminados.

### Decisiones de diseño

- **`borrarCliente` es un borrado real**, condicionado a `ReglasCliente.puedeBorrarse`
  (`cantidadPedidos == 0`) — mismo patrón que `MenuRepositorioLocal.borrarCategoria`. El menú
  ⋮ decide dinámicamente entre "Reactivar" / "Eliminar" / "Dar de baja" según el estado del
  cliente (`ClienteAdapter.accionarEliminar`), reusando `menu_acciones.xml` en vez de crear
  un menú de tres opciones.
- **`buscarOCrearCliente` no toca Room ni el outbox.** Por diseño explícito del plan (§5.1):
  el id lo genera el servidor. `ClienteRepositorioLocal.buscarOCrearCliente(...)` llama
  directo a `ClienteRemoto.buscarOCrear(...)`, sin escritura local. Queda expuesto en el
  contrato sin consumidor — registrado como **P-026** para que la Fase 4 (Pedidos) no se lo
  encuentre de sorpresa.
- **Filtro por activo/inactivo con la misma trampa del §5.1 de Mesas** (§5.5 del plan de
  Clientes): dar de baja a un cliente con el filtro "Activos" puesto no puede esconderlo de
  la pantalla. `ClientesViewModel.descartarFiltroQueEsconde(...)` replica el criterio.
- **Cifrado de la base local diferido a propósito** — el plan (§5.4) pide registrarlo, no
  improvisarlo. Ver **P-027**.
- El `PayloadOperacion.borrarCategoria`/`idServidorDe` existentes (del Menú) se **reusaron**
  para `BORRAR_CLIENTE`: el payload es genéricamente "un id de servidor", más allá de su
  nombre. Se documentó la reutilización en vez de duplicar la clase o renombrarla (renombrar
  tocaría el Menú, que ya funciona).

---

## Verificación

```
./gradlew testDebugUnitTest assembleDebug
BUILD SUCCESSFUL
345 tests, 0 failures (eran 243 al empezar la sesión)
```

Compilación y suite corridas en pasos intermedios después de cada entregable (E1, E3, E4,
E6, E7 de cada módulo) para aislar errores temprano, no solo al final.

**No se probó en un emulador/dispositivo.** Es intencional: sin la Parte A, ninguna pantalla
puede hablar con un servidor real — probar la UI hoy solo confirmaría que no crashea, no que
el flujo funciona. Queda fuera del alcance de esta sesión, igual que el plan ya lo dejaba
fuera del alcance del agente.

---

## Gate de Autoverificación

Aplicado a los dos módulos juntos (ver [[Gate de Autoverificación]]):

```
[x] Compila mentalmente y de verdad: ./gradlew assembleDebug → BUILD SUCCESSFUL
[x] Toda dependencia usada ya estaba en libs.versions.toml — no se agregó ninguna
[x] Ninguna API deprecada nueva
[x] domain/ sin imports de android.*/androidx.* salvo androidx.annotation y
    androidx.lifecycle.LiveData
[x] La UI no llama a DAO/ApiService directo — solo al repositorio vía ViewModel
[x] I/O fuera del hilo principal — ExecutorService inyectado en cada ViewModel
[x] ViewModel sin Context/Activity/Fragment/Resources/View
[➖] Binding anulado en onDestroyView() — N/A: el proyecto no usa ViewBinding en ningún
    Fragment existente, se siguió el patrón findViewById ya establecido
[x] Observers con getViewLifecycleOwner()
[x] Cero strings/colores/dimens hardcodeados — todo en recursos, incluidos los nuevos
    EstadoMesaUi (colores) y strings.xml (mensajes)
[x] Cero secretos ni URLs de producción
[x] Nulabilidad anotada (@NonNull/@Nullable) en toda API pública nueva
[x] Errores traducidos a Result — ningún catch vacío
[x] Nomenclatura respetada (mismo estilo que Mesas/Menú/Empleados existentes)
[x] Room: migraciones DE_2_A_3 y DE_3_A_4 escritas y con test contra MigrationTestHelper;
    sin fallbackToDestructiveMigration
[x] Escrituras offline-first: local primero + encolado — excepción documentada:
    buscarOCrearCliente (P-026), igual que el alta de Empleados
[x] Listas con ListAdapter + DiffUtil — MesaAdapter y ClienteAdapter
[➖] Insets/edge-to-edge — N/A para estos Fragments: se montan sobre el mismo
    NavigationView/MainActivity que ya los maneja (P-004, deuda global preexistente)
[x] Accesibilidad: contentDescription en los ImageButton nuevos; altura_minima_tactil en
    todos los objetivos táctiles nuevos (chips, botones de opciones)
[x] Pruebas incluidas con aserciones reales — 128 tests nuevos entre los dos módulos
[x] Sin trabajo pesado agregado a Application.onCreate() — el wiring de SyncApplication
    solo registra fábricas, no ejecuta nada
```

Sin ❌. Los `➖ N/A` están justificados arriba.

---

## Lo que NO cambió

- `data/`, `ui/` de los demás módulos (Menú, Empleados, Login) intactos.
- El plan no se tocó; las decisiones de arriba están dentro de lo que cada plan permite
  decidir a la Parte B, o quedaron registradas como deuda cuando no.

---

## Relaciones

- [[Plan Fase 2c - CRUD de Mesas]] · [[Plan Fase 2d - CRUD de Clientes]]
- [[Sesión 2026-08-01 - E1 del Plan Fase 2c - capa domain de Mesas]] — de dónde venía el trabajo
- [[Módulo Mesas]] · [[Módulo Clientes]]
- [[ADR-007 - Estados operativos en catálogos propios, separados de estado_general]]
- [[Módulo Menú]] · [[Módulo Empleados]] — los patrones replicados
- [[Deuda Técnica - Pendientes]] — P-026, P-027
- [[Protocolo de Ejecución de un Plan]] · [[Gate de Autoverificación]]
- [[Arquitectura Actual]] · [[Roadmap de Fases]] · [[Esquema de Base de Datos]]

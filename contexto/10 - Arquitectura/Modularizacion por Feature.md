---
title: "Modularización por Feature"
tags:
  - arquitectura
  - modularizacion
  - gradle
date: 2026-07-29
---

# Modularización por Feature

> [!abstract] Principio
> Se agrupa **por feature primero, por capa después** (`feature.pedidos.data`), nunca al revés (`data.pedidos`). Un feature completo debe caber en una carpeta.

---

## Estructura objetivo (multi-módulo)

```
raiz/
├── build.gradle.kts              # solo plugins con apply=false
├── settings.gradle.kts
├── gradle/libs.versions.toml     # ÚNICA fuente de versiones
├── build-logic/                  # convention plugins (composite build)
├── app/                          # SOLO ensamblaje: Application, DI raíz, nav_graph, manifest
├── core/
│   ├── common/                   # Result, AppExecutors, AppException
│   ├── model/                    # modelos de dominio compartidos
│   ├── data/                     # repos compartidos, sincronizador, política de reintento
│   ├── database/                 # Room: AppDatabase, DAOs, entidades, migraciones
│   ├── network/                  # Retrofit/OkHttp, interceptores, DTOs, mappers
│   ├── datastore/                # preferencias
│   ├── designsystem/             # tema, colores, tipografías, componentes reutilizables
│   ├── ui/                       # BaseFragment, adapters genéricos
│   └── testing/                  # fakes, reglas JUnit, datos de prueba
├── feature/
│   ├── auth/  ├── menu/  ├── pedidos/  ├── mesas/  ├── reportes/
│   │   src/main/java/hn/restaurante/app/feature/pedidos/
│   │     ├── ui/       PedidosFragment, PedidosViewModel, PedidosUiState, PedidosAdapter
│   │     ├── domain/   RegistrarPedidoUseCase, PedidoRepository (interfaz)
│   │     └── data/     PedidoRepositoryImpl, PedidoLocalDataSource, PedidoRemoteDataSource
├── benchmark/                    # Macrobenchmark
└── baselineprofile/              # generador de Baseline + Startup Profile
```

## Reglas de modularización

1. **`feature:X` no depende de `feature:Y`.** Si necesitan compartir algo, se sube a `core:`. Sin esta regla, en seis meses todo depende de todo.
2. **`app` depende de todos los features; ningún feature depende de `app`.**
3. Todo módulo nuevo aplica un **convention plugin** de `build-logic`. **Prohibido copiar/pegar bloques de configuración** entre `build.gradle.kts`.
4. Nombre de módulo Gradle = ruta con `:` (`:feature:pedidos`); `namespace` = `hn.restaurante.app.feature.pedidos`.
5. `domain` de cada feature **sin imports de `android.*`** — verificable con lint custom en CI.

---

## Módulo único (lo que este proyecto usa hoy)

Con menos de ~10 pantallas, un solo módulo `app` es la elección correcta — ver [[ADR-001 - Arquitectura por capas (ui-domain-data) en Android con Java]]. Pero se aplica **la misma jerarquía por paquetes**:

```
com.example.proyectofinalrestaurante
├── core/                     ← SupabaseClient, AppExecutors
├── domain/                   ← Result, model/, repository/
├── data/                     ← remote/, repository/
└── ui/
    ├── login/                ← LoginActivity, LoginViewModel, EstadoLogin
    ├── menu/                 (Fase 2)
    └── pedidos/              (Fase 3)
```

> [!warning] Deuda estructural conocida
> Hoy los paquetes están organizados **por capa primero** (`domain/model`, `data/repository`) y no por feature. Con un solo feature (login) no molesta, pero cuando entren Menú, Pedidos y Mesas, `data/repository/` va a acumular repositorios de todo el sistema sin relación entre sí.
>
> **Momento de decidir: al arrancar la Fase 2.** O se migra a feature-first, o se acepta explícitamente el layer-first para un proyecto chico. Registrado como **P-017** en [[Deuda Técnica - Pendientes]].

## Cuándo partir a multi-módulo

Señales de que llegó el momento:
- El build incremental supera ~1 minuto.
- Dos personas tocan el mismo `build.gradle.kts` en cada PR.
- Un cambio en una feature obliga a recompilar todo.
- Más de ~10 pantallas.

> [!tip]
> Migrar de módulo único a multi-módulo **cuesta 10× más** después que hacerlo desde el inicio. Si el proyecto va a crecer a 5 features, conviene modularizar antes de la Fase 3.

---

## Relaciones

- [[Clean Architecture]]
- [[ADR-001 - Arquitectura por capas (ui-domain-data) en Android con Java]]
- [[Toolchain Android 2026 - AGP, Gradle y JDK]]
- [[Arquitectura Actual]]
- [[Deuda Técnica - Pendientes]] — P-017

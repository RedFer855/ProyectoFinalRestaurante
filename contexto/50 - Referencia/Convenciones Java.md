---
title: "Convenciones Java (Proyecto Restaurante)"
tags:
  - referencia
  - convenciones
  - java
date: 2026-07-29
lifecycle: verified
---

# Convenciones Java — Proyecto Restaurante

> [!info] Fuente
> Convenciones propias del proyecto, derivadas del [[Estándar de Ingeniería Android]] y análogas a las de `Convenciones C#` del proyecto Bimbo.

---

## 1. Nomenclatura Java

| Elemento | Convención | Ejemplo |
|---|---|---|
| Paquete | minúsculas, sin guiones ni `_`, **feature-first** | `hn.restaurante.app.feature.pedidos.data` |
| Clase | `PascalCase`, sustantivo | `PedidoRepositoryImpl` |
| Interfaz | `PascalCase` **sin prefijo `I`** (convención Java, ≠ C#) | `PedidoRepository`, no `IPedidoRepository` |
| Implementación | nombre significativo; si no lo hay, prefijo `Default` | `OfflineFirstPedidoRepository`, `DefaultPedidoRepository` |
| Fake de prueba | prefijo `Fake` | `FakePedidoRepository` |
| Método | `camelCase`, **frase verbal** | `registrarPedido()` |
| Método que devuelve stream | `get{Modelo}Stream()` / plural si es lista | `getPedidosStream()` |
| Método booleano | `is` / `has` / `can` | `isSincronizado()`, `hasPendientes()` |
| Campo | `camelCase`, **`private final` por defecto** | `pedidoRepository` |
| Constante | `UPPER_SNAKE_CASE`, `static final` | `MAX_REINTENTOS` |
| Parámetro | `camelCase`, nunca abreviado | `producto`, no `p` |
| Genérico | `T`, `R`, `K`, `V` | |

### Prohibido

- Prefijos húngaros: `mNombre`, `sInstancia`, `strNombre`.
- Nombres cajón de sastre: `data`, `info`, `manager` genérico, paquete `utils`/`helpers`.
- Abreviaturas inventadas: `prod`, `usr`, `qty`.
- Paquetes raíz por tipo: `activities/`, `fragments/`, `models/`. **Se agrupa por feature primero, por capa después** (`feature.pedidos.data`, nunca `data.pedidos`).

### Idioma

**Un solo idioma en todo el código.** Identificadores de dominio en **español** (`Pedido`, `registrarVenta`), términos técnicos y de framework en **inglés** (`Repository`, `UseCase`, `ViewModel`, `Dto`). Nunca mezclar dentro de un mismo identificador: `guardarUser()` está mal.

---

## 2. Sufijos por rol (no negociables)

`...Activity` · `...Fragment` · `...ViewModel` · `...UiState` · `...Adapter` · `...ViewHolder` · `...Repository` / `...RepositoryImpl` · `...LocalDataSource` · `...RemoteDataSource` · `...UseCase` · `...Mapper` · `...Dto` · `...Entity` · `...Dao` · `...ApiService` · `...Worker` · `...Module` · `...Interceptor` · `...Test`

---

## 3. Recursos Android — patrón `tipo_feature_descripcion`

| Recurso | Patrón | Ejemplo |
|---|---|---|
| Layout | `{tipo}_{feature}_{descripcion}.xml` | `fragment_pedidos_lista.xml`, `item_producto.xml` |
| ID de vista | `{tipo}{Descripcion}` en `camelCase` | `btnGuardar`, `rvProductos`, `tvTotal`, `etCantidad` |
| String | `{feature}_{descripcion}` | `pedidos_titulo`, `error_sin_conexion`, `comun_reintentar` |
| Color | **rol semántico**, no el color | `color_superficie`, `color_error` — nunca `azul_claro` |
| Dimen | `{uso}_{tamano}` | `espaciado_md`, `texto_titulo` |
| Drawable | `ic_`, `bg_`, `shape_`, `selector_` | `ic_carrito_24` |
| Estilo/Tema | `Theme.MiApp.X`, `Widget.MiApp.Boton.Primario` | |

> [!danger] Regla dura
> **Cero strings, dimens o colores hardcodeados** en XML o Java. Todo texto visible va en `strings.xml`.

> [!note] Deuda actual
> El proyecto usa IDs en `snake_case` (`txt_correo`, `btn_login`) en vez del patrón `camelCase` de arriba, y el color de error está hardcodeado (`#D32F2F`) en `activity_login.xml`. Ver **P-011** en [[Deuda Técnica - Pendientes]].

---

## 4. Reglas de código (aplican a cada línea)

1. **Inmutabilidad por defecto:** campos `private final`; colecciones expuestas con `Collections.unmodifiableList(...)` o copia defensiva. Modelos de dominio y `UiState` **siempre** inmutables.
2. **Nulabilidad explícita:** `@NonNull` / `@Nullable` (`androidx.annotation`) en **toda** API pública. Validar en frontera con `Objects.requireNonNull(x, "x == null")`. Nunca devolver `null` de una colección: devolver `Collections.emptyList()`.
3. **Constructor injection siempre.** Inyección por campo solo donde el framework obliga (`@Inject` en `Activity`/`Fragment`/`Worker`).
4. **Límites duros:** método ≤ 40 líneas · clase ≤ 400 líneas · parámetros ≤ 4 · anidamiento ≤ 3 niveles · complejidad ciclomática ≤ 10. Si se excede, se extrae.
5. **Una responsabilidad por clase.** Un `Fragment` solo infla, observa estado, renderiza y delega clicks. **Cero lógica de negocio, cero red, cero BD en la UI.**
6. **Threading:** todo I/O en `AppExecutors.io()`; resultado con `postValue()`. Ver [[Asincronia en Java para Android]].
7. **Errores:** un solo lugar captura, se traduce a `AppException`, se propaga como `Result.Error`. Ver [[Result Pattern]].
8. **Ciclo de vida:** en `Fragment`, `binding = null` en `onDestroyView()`; observers con `getViewLifecycleOwner()`; callbacks registrados se desregistran. Cero referencias estáticas a `Context`/`View`/`Activity`.
9. **Comparaciones:** `Objects.equals(a, b)`, nunca `==` para objetos. `equals`/`hashCode` juntos en modelos. `switch` sobre enums con `default` que falla ruidosamente.
10. **Documentación:** JavaDoc en clases públicas de `domain` y `data`, y en cualquier método cuyo *porqué* no sea evidente. Un comentario que explica *qué hace* el código = código mal nombrado; se refactoriza en vez de comentar.
11. **Recursos:** `try-with-resources` para `Cursor`, streams y ficheros.
12. **Tiempo y aleatoriedad se inyectan** (`Clock`, `TiempoDataSource`) para poder testear.

---

## 5. Base de datos y API

- Tablas `snake_case` **plural** (`pedidos_detalle`); columnas `snake_case` **singular** (`producto_id`).
- Toda entidad Room lleva `@PrimaryKey`, índices en columnas de búsqueda y FK, y campos de sincronización: `created_at`, `updated_at`, `sync_state`, `remote_id`, `deleted`.
- **Borrado lógico** (`deleted = true`), nunca físico, si hay sincronización.
- Endpoints REST en `kebab-case` plural; parámetros en `snake_case`.

---

## 6. Git

- Ramas: `main`/`master`, `feature/{issue}-{slug}`, `fix/{issue}-{slug}`, `chore/...`, `release/x.y.z`. En este proyecto además `feat/faseN-...` por fase — ver [[Roadmap de Fases]].
- Commits: **Conventional Commits** (`feat(pedidos): agregar filtro por fecha`), imperativo, asunto ≤ 72 caracteres.
- **Un PR = un propósito.** Prohibido mezclar refactor + feature.

---

## Relaciones

- [[Estándar de Ingeniería Android]]
- [[Lista Negra de APIs Android]]
- [[Result Pattern]]
- [[Asincronia en Java para Android]]
- [[MVVM en Android (ViewModel + LiveData)]]
- [[Deuda Técnica - Pendientes]] — P-011

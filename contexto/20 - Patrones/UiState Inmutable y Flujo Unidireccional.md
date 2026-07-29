---
title: "UiState Inmutable y Flujo Unidireccional (UDF)"
tags:
  - patron
  - mvi
  - udf
  - estado
date: 2026-07-29
lifecycle: verified
---

# UiState Inmutable y Flujo Unidireccional (UDF)

> [!abstract] Definición
> El estado de una pantalla es **un solo objeto inmutable**. El estado baja (`LiveData<UiState>`), los eventos suben (llamadas a métodos del ViewModel). Nunca al revés.

```
        estado ↓                    eventos ↑
Fragment ←──── LiveData<UiState> ──── ViewModel
   │                                      ▲
   └──────── vm.onSubmitClicked() ────────┘
```

## Cuándo usarlo

**Siempre.** Es el modelo de estado por defecto de toda pantalla del proyecto.

## Regla central: un objeto, no N banderas

> [!bug] Anti-patrón
> ```java
> // ❌ Cuatro LiveData que pueden quedar en combinaciones imposibles
> LiveData<Boolean> cargando;
> LiveData<Boolean> hayError;
> LiveData<String>  mensajeError;
> LiveData<List<Producto>> productos;
> ```
> Con 4 booleanos hay 16 combinaciones, y al menos 10 son estados que nunca deberían existir (`cargando=true` **y** `hayError=true`). Tarde o temprano la UI aterriza en una de ellas.

> [!success] Correcto
> ```java
> // ✅ Un objeto: los estados imposibles no se pueden representar
> LiveData<PedidosUiState> estado;
> ```

Esta es la misma lección que el proyecto Bimbo aprendió por las malas con su `ShowSuggestions` booleano (su ítem P-026): **una sola señal, no varias que hay que mantener coherentes entre sí**.

## Cómo se implementa

```java
public final class PedidosUiState {

    private final boolean cargando;
    private final List<Pedido> pedidos;
    private final String error;

    private PedidosUiState(boolean cargando, List<Pedido> pedidos, String error) {
        this.cargando = cargando;
        this.pedidos = pedidos == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(pedidos));
        this.error = error;
    }

    public static PedidosUiState inicial()               { return new PedidosUiState(false, null, null); }
    public static PedidosUiState cargando()              { return new PedidosUiState(true,  null, null); }
    public static PedidosUiState error(String mensaje)   { return new PedidosUiState(false, null, mensaje); }
    public static PedidosUiState datos(List<Pedido> ps)  { return new PedidosUiState(false, ps,   null); }

    public boolean isCargando()      { return cargando; }
    public List<Pedido> getPedidos() { return pedidos; }
    @Nullable public String getError() { return error; }
}
```

La `Activity`/`Fragment` renderiza el estado completo en un solo método:

```java
private void render(PedidosUiState estado) {
    progressBar.setVisibility(estado.isCargando() ? View.VISIBLE : View.GONE);
    adapter.submitList(estado.getPedidos());
    tvError.setVisibility(estado.getError() != null ? View.VISIBLE : View.GONE);
    tvError.setText(estado.getError());
}
```

> [!tip] Regla de oro del render
> `render()` debe poder ejecutarse con **cualquier** estado en **cualquier** orden y dejar la UI correcta. Si depende de qué había antes, hay estado escondido en las vistas.

## Eventos de un solo disparo (navegar, snackbar)

El ViewModel **no emite eventos hacia la UI**: procesa el evento y publica un estado nuevo. Un evento que debe consumirse una sola vez se modela como campo del estado con bandera de consumido:

```java
// En el estado
@Nullable public Sesion getSesionParaNavegar() { return sesion; }

// En el Fragment, tras navegar
vm.onNavegacionConsumida();
```

Sin esto, al rotar la pantalla el observer vuelve a disparar y se navega dos veces.

## Dónde está en el proyecto

- `ui/login/EstadoLogin.java` — ya sigue este patrón (factories estáticas, campos `final`).
- ⚠️ **Falta** el manejo de "evento consumido": hoy `LoginActivity` navega cada vez que `getSesion() != null`. Si la Activity se recreara con ese estado vivo, navegaría de nuevo. Registrado como **P-013** en [[Deuda Técnica - Pendientes]].

## Anti-patrones

- Exponer `MutableLiveData` públicamente (la UI podría escribir estado).
- Mutar la lista que ya se publicó en el estado.
- Un `boolean` como señal de "mostrar popup": `LiveData` no notifica si el valor no cambia, y la UI se congela.
- Lógica de decisión en el `Fragment` (`if (a && !b) mostrar...`) — esa decisión pertenece al ViewModel.

---

## Relaciones

- [[MVVM en Android (ViewModel + LiveData)]]
- [[Catálogo de Patrones Android]]
- [[Result Pattern]]
- [[Estrategia de Pruebas Android]]
- [[Deuda Técnica - Pendientes]] — P-013

---
title: "MVVM en Android (ViewModel + LiveData)"
tags:
  - patron
  - mvvm
  - android
date: 2026-07-29
lifecycle: verified
aliases:
  - ViewModel + LiveData
---

# MVVM en Android (ViewModel + LiveData)

> [!abstract] Definición
> Equivalente Android al MVVM con `CommunityToolkit.Mvvm` de un proyecto WPF: la View (`Activity`/`Fragment`) observa un `ViewModel` que expone estado vía `LiveData`, y **nunca** contiene lógica de negocio.

`LiveData` es el equivalente Java de `StateFlow` — ver [[Librerias Java-Friendly vs Kotlin-Only]].

---

## El ViewModel

```java
public class LoginViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final Executor executor;                       // ← inyectado, no creado adentro
    private final MutableLiveData<EstadoLogin> estado =    // ← privado y mutable
            new MutableLiveData<>(EstadoLogin.inicial());

    @Inject
    public LoginViewModel(@NonNull AuthRepository authRepository, @NonNull Executor executor) {
        this.authRepository = authRepository;
        this.executor = executor;
    }

    public LiveData<EstadoLogin> getEstado() { return estado; }   // ← público e inmutable

    public void login(String correo, String contrasenia) {
        estado.setValue(EstadoLogin.cargando());
        executor.execute(() -> {
            Result<Sesion> r = authRepository.login(correo, contrasenia);
            estado.postValue(r.isSuccess()
                    ? EstadoLogin.exito(r.getValue())
                    : EstadoLogin.error(r.getError()));
        });
    }
}
```

### Reglas del ViewModel

1. **`MutableLiveData` privado, `LiveData` público.** Si la UI puede escribir el estado, el flujo deja de ser unidireccional.
2. **Cero `Context`, `Activity`, `Fragment`, `Resources` o `View`.** Si el VM necesita un string, expone un `@StringRes` o un código de error y la View lo resuelve. `AndroidViewModel` está prohibido justo porque invita a lo contrario.
3. **El `Executor` se inyecta** — ver [[Asincronia en Java para Android]]. Sin eso el VM no es testeable.
4. Trabajo de red **nunca** en el hilo principal; `setValue()` desde el hilo principal, `postValue()` desde el de fondo.
5. Argumentos de navegación por **`SavedStateHandle`**, nunca por setter público.
6. Un ViewModel **por pantalla**, no por componente reutilizable. Para compartir entre pantallas de un mismo flujo, ViewModel con alcance de grafo de navegación.

---

## La View

```java
// Fragment — con ViewBinding
@Override
public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    binding.btnLogin.setOnClickListener(v -> viewModel.login(
            binding.etCorreo.getText().toString(),
            binding.etContrasenia.getText().toString()));

    viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
}

private void render(EstadoLogin estado) {
    binding.progressLogin.setVisibility(estado.isCargando() ? View.VISIBLE : View.GONE);
    binding.btnLogin.setEnabled(!estado.isCargando());
    binding.tvError.setText(estado.getError());
    binding.tvError.setVisibility(estado.getError() != null ? View.VISIBLE : View.GONE);
}

@Override
public void onDestroyView() {
    super.onDestroyView();
    binding = null;                    // ← obligatorio: evita fuga de la jerarquía de vistas
}
```

### Reglas de la View

1. **`getViewLifecycleOwner()`, nunca `this`** al observar desde un `Fragment`. Con `this` el observer sobrevive a la vista destruida y se acumulan observers en cada `onCreateView`.
2. **`binding = null` en `onDestroyView()`.** Sin esto se filtra toda la jerarquía de vistas.
3. **ViewBinding, nunca `findViewById`.**
4. La View **solo**: infla, observa, renderiza, delega clicks. Cero decisiones. Ver [[UiState Inmutable y Flujo Unidireccional]].
5. Insets y edge-to-edge se manejan en la View, y son **obligatorios** — ver [[Android 16 y 17 - Cambios de Comportamiento]].

---

## Anti-patrones

| ❌ | Por qué |
|---|---|
| Lógica de negocio en el `onClickListener` | Intesteable, se duplica entre pantallas |
| Llamar Retrofit/DAO desde la UI | Rompe las capas; ver [[Clean Architecture]] |
| Varias `LiveData` sueltas para un mismo estado | Estados imposibles; ver [[UiState Inmutable y Flujo Unidireccional]] |
| `AndroidViewModel` para tener `Context` | Acopla el VM al framework |
| `MutableLiveData` público | Rompe el flujo unidireccional |
| Observar con `this` en un Fragment | Fuga + observers duplicados |

---

## Dónde está en el proyecto

- `ui/login/LoginViewModel.java` · `ui/login/LoginActivity.java` · `ui/login/EstadoLogin.java`

> [!warning] Diferencias con el estándar
> El código actual usa **`Activity` + `findViewById`**, no `Fragment` + ViewBinding + Navigation Component, y el `Executor` no está inyectado. Es aceptable para una sola pantalla, pero debe corregirse antes de que se replique a los módulos de la Fase 2. Ver **P-005** y **P-015** en [[Deuda Técnica - Pendientes]].

---

## Relaciones

- [[UiState Inmutable y Flujo Unidireccional]]
- [[Asincronia en Java para Android]]
- [[Repository Pattern]]
- [[Result Pattern]]
- [[Clean Architecture]]
- [[Catálogo de Patrones Android]]
- [[Módulo Login]]

---
title: MVVM en Android (ViewModel + LiveData)
tags:
  - patron
  - mvvm
  - android
aliases:
  - ViewModel + LiveData
---

# MVVM en Android (ViewModel + LiveData)

> [!abstract] Definición
> Equivalente Android al MVVM con `CommunityToolkit.Mvvm` de un proyecto WPF: la `Activity` (View) observa un `ViewModel` que expone estado vía `LiveData`, y nunca contiene lógica de negocio.

---

## Siempre usar en ViewModels

```java
public class LoginViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final MutableLiveData<EstadoLogin> estado = new MutableLiveData<>(EstadoLogin.inicial());

    public LoginViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public LiveData<EstadoLogin> getEstado() { return estado; }

    public void login(String correo, String contrasenia) {
        estado.setValue(EstadoLogin.cargando());
        // ejecutar en background (ExecutorService), postValue() en el resultado
    }
}
```

```java
// En la Activity — solo observa, nunca decide
viewModel.getEstado().observe(this, estadoLogin -> {
    progressBar.setVisibility(estadoLogin.isCargando() ? View.VISIBLE : View.GONE);
    if (estadoLogin.getError() != null) txtError.setText(estadoLogin.getError());
    if (estadoLogin.getSesion() != null) irAPantallaPrincipal();
});
```

- El `ViewModel` sobrevive a cambios de configuración (rotación) — no recrear estado en `onCreate`.
- Trabajo de red **nunca** en el hilo principal: usar `ExecutorService`/`Executors.newSingleThreadExecutor()` y `postValue()` desde el hilo de fondo (o migrar a Coroutines/RxJava en una fase posterior si se adopta Kotlin).
- La `Activity` **no** decide si mostrar error o éxito con `if/else` disperso — todo el estado sale de un único objeto observado (`EstadoLogin`), igual que el principio "una sola señal" documentado en Bimbo para su buscador.

**NO usar:**
- Lógica de negocio dentro de la `Activity`/`onClickListener`.
- Llamadas a Retrofit directamente desde la UI.
- Múltiples `LiveData` sueltas para representar el mismo estado (loading/error/éxito) cuando un solo objeto de estado alcanza — evita que la UI quede en una combinación inconsistente.

---

## Dónde está en el proyecto

- `ui/login/LoginViewModel.java`
- `ui/login/LoginActivity.java`

---

## Relaciones

- [[Repository Pattern]]
- [[Result Pattern]]
- [[Clean Architecture]]
- [[Módulo Login]]

# Proyecto Final Restaurante — Contexto para Claude

## ¿Qué es este proyecto?

App móvil Android (Java) para gestión de un restaurante: login, menú, pedidos, mesas. Backend **Supabase** (Postgres + Auth REST, consumido directo con Retrofit — sin el SDK Kotlin, ver [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]]).

## Ubicación

`C:\Users\fbara\ProyectoFinalRestaurante\`
Módulo Gradle único: `app`

---

## Arquitectura de capas (paquetes dentro de `app`)

```
ui → domain ← data → core
```

| Paquete | Rol | Dependencias |
|---|---|---|
| `ui/` | Activities, ViewModels, adapters | `domain` |
| `domain/` | Entidades, interfaces de repositorio, `Result` | — |
| `data/` | Implementaciones concretas (Retrofit, DTOs) | `domain`, `core` |
| `core/` | Cliente HTTP/Supabase compartido | — |

> **Regla de oro:** `domain` **nunca** referencia `data`. La dependencia va en sentido contrario — `data` implementa contratos definidos en `domain`.

---

## Entry point / navegación

- `LoginActivity` es la actividad `LAUNCHER` (`AndroidManifest.xml`).
- Login exitoso → `startActivity(MainActivity.class)` + `finish()`.
- Para agregar una pantalla nueva: `Activity` + `ViewModel` en su propio subpaquete de `ui/`, layout en `res/layout/`, registrar en `AndroidManifest.xml`.

---

## Inyección de dependencias (manual, sin framework — Fase 1)

```java
// LoginViewModelFactory.java
AuthRepository authRepository = new SupabaseAuthRepository(SupabaseClient.getAuthApi());
LoginViewModel viewModel = new LoginViewModel(authRepository);
```

Los ViewModels se resuelven en la Activity con `ViewModelProvider`:
```java
viewModel = new ViewModelProvider(this, new LoginViewModelFactory())
        .get(LoginViewModel.class);
```

---

## Patrón MVVM (`androidx.lifecycle`)

**Siempre usar en ViewModels:**

```java
public class LoginViewModel extends ViewModel {
    private final MutableLiveData<EstadoLogin> estado = new MutableLiveData<>(EstadoLogin.inicial());
    public LiveData<EstadoLogin> getEstado() { return estado; }
    public void login(String correo, String contrasenia) { ... }
}
```

- Un único objeto de estado por pantalla (`EstadoLogin`), no `LiveData` sueltas por cada campo — ver [[MVVM en Android (ViewModel + LiveData)]].
- Trabajo de red en `ExecutorService`, nunca en el hilo principal.

**NO usar:**
- Lógica de negocio o llamadas Retrofit directamente en la `Activity`.
- Múltiples señales booleanas para representar el mismo estado combinado (ver la lección documentada en Bimbo sobre `ShowSuggestions` — misma idea acá).

---

## Repositorios — Convenciones

```java
public interface AuthRepository {
    Result<Sesion> login(String correo, String contrasenia);
}

public class SupabaseAuthRepository implements AuthRepository {
    // ver Result Pattern y Supabase Auth REST - Login Android
}
```

- Todo método de red retorna `Result`/`Result<T>` — nunca deja propagar una excepción cruda a la UI.
- Credenciales de Supabase vienen de `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY`, nunca hardcodeadas.

---

## Base de datos: Supabase

Todavía no hay tablas propias del dominio del restaurante (menú, pedidos, mesas) — Fase 1 solo usa Auth. Cuando se agregue el módulo Menú (Fase 2), documentar acá el esquema real, siguiendo el mismo formato que Bimbo documentó sus tablas (`snake_case` en BD, mapeo explícito a Java).

---

## Relaciones

- [[Arquitectura Actual]]
- [[Módulo Login]]
- [[Roadmap de Fases]]

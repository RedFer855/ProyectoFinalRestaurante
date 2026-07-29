---
title: Repository Pattern
tags:
  - patron
  - datos
  - java
aliases:
  - Patrón Repositorio
---

# Repository Pattern

> [!abstract] Definición
> Abstrae el acceso a datos detrás de una interfaz definida en `domain`. El `ViewModel` no sabe si los datos vienen de Supabase, de un mock para tests, o de otro backend.

---

## Contrato — Domain Layer

```java
public interface AuthRepository {
    Result<Sesion> login(String correo, String contrasenia);
}
```

## Implementación — Data Layer

```java
public class SupabaseAuthRepository implements AuthRepository {

    private final SupabaseAuthApi api;

    public SupabaseAuthRepository(SupabaseAuthApi api) {
        this.api = api;
    }

    @Override
    public Result<Sesion> login(String correo, String contrasenia) {
        // ver detalle completo en Result Pattern y en
        // "Supabase Auth REST - Login Android"
    }
}
```

## Cómo se conectan (DI manual — sin Hilt/Koin todavía)

```java
// Composition root simple, ej. en LoginViewModelFactory
AuthRepository authRepository = new SupabaseAuthRepository(SupabaseClient.getAuthApi());
```

> [!note] Por qué DI manual y no Hilt/Dagger
> Para Fase 1 (una sola pantalla) agregar un framework de DI es sobre-ingeniería. Si el número de repositorios/ViewModels crece en fases siguientes, reevaluar — ver [[Deuda Técnica - Pendientes]].

---

## Beneficios concretos

| Beneficio | Ejemplo |
|---|---|
| **Testeable** | Se puede implementar un `FakeAuthRepository` para testear `LoginViewModel` sin red |
| **Intercambiable** | Cambiar Supabase por otro backend: solo se reescribe la implementación en `data` |
| **Seguro** | La UI nunca arma requests HTTP directamente |

---

## Anti-patrones a evitar

> [!bug] No hagas esto
> ```java
> // ❌ Llamar a Retrofit directamente desde la Activity/ViewModel
> api.login(dto).enqueue(new Callback<>() { ... }); // sin pasar por AuthRepository
> ```

> [!success] Haz esto
> ```java
> // ✅ El ViewModel solo conoce la interfaz de domain
> Result<Sesion> resultado = authRepository.login(correo, contrasenia);
> ```

---

## Relaciones

- [[Clean Architecture]]
- [[SOLID]]
- [[Result Pattern]]
- [[MVVM en Android (ViewModel + LiveData)]]
- [[Caso 01 - Login con Supabase Auth]]

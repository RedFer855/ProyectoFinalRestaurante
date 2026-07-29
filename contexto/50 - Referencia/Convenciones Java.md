---
title: "Convenciones Java (Proyecto Restaurante)"
tags:
  - referencia
  - convenciones
  - java
date: 2026-07-29
lifecycle: draft
---

# Convenciones Java — Proyecto Restaurante

> [!info] Fuente
> Convenciones propias del proyecto, análogas a las de `Convenciones C#` en el proyecto Bimbo, adaptadas a Java/Android.

## Paquetes

`com.example.proyectofinalrestaurante.{ui,domain,data,core}.<módulo>` — ver [[Arquitectura Actual]].

## Nombres

- Clases: `PascalCase` (`LoginViewModel`, `SupabaseAuthRepository`).
- Métodos/variables: `camelCase` (`login`, `correoUsuario`).
- Interfaces sin prefijo `I` (convención Java, a diferencia de C#): `AuthRepository`, no `IAuthRepository`.
- DTOs de red con sufijo `Dto`: `LoginRequestDto`, `LoginResponseDto`.
- IDs de recursos Android en `snake_case` (convención estándar de Android): `activity_login`, `txt_correo`.

## ViewModels

- Extender `androidx.lifecycle.ViewModel`.
- Exponer un único objeto de estado por pantalla vía `LiveData<EstadoX>` en vez de múltiples `LiveData` sueltas — ver [[MVVM en Android (ViewModel + LiveData)]].
- Nunca importar `android.app.Activity` ni nada de UI directamente en el ViewModel.

## Repositorios

- Interfaz en `domain.repository`, implementación en `data.repository`.
- Todo método que hace red retorna `Result`/`Result<T>` — nunca deja propagar una excepción de Retrofit/OkHttp cruda. Ver [[Result Pattern]].

## Hilos

- Llamadas de red **nunca** en el hilo principal. Usar `ExecutorService` (`Executors.newSingleThreadExecutor()`) desde el ViewModel, `postValue()` para volver al hilo principal.

## Recursos y strings

- Textos de UI siempre en `res/values/strings.xml`, nunca hardcodeados en el layout o en Java.

---

## Relaciones

- [[Arquitectura Actual]]
- [[MVVM en Android (ViewModel + LiveData)]]
- [[Result Pattern]]

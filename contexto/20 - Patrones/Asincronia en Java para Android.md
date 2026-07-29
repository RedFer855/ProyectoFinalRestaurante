---
title: "Asincronía en Java para Android"
tags:
  - patron
  - java
  - concurrencia
date: 2026-07-29
lifecycle: verified
---

# Asincronía en Java para Android

> [!abstract] El problema
> Toda la documentación moderna de Android está escrita para **corrutinas de Kotlin**. En un proyecto Java hay que traducir esa recomendación, y las traducciones ingenuas (`AsyncTask`, `new Thread()`) están prohibidas.

## Opciones válidas, en orden

| Opción | Cuándo | Nota |
|---|---|---|
| **`ExecutorService` inyectado + `LiveData.postValue()`** | Default para operaciones simples | Lo que usa el proyecto hoy |
| **Guava `ListenableFuture`** | Cuando hay encadenamiento o cancelación | Soporte oficial de Room y Retrofit para Java |
| **RxJava 3** | Solo si el equipo ya la domina | Potente pero con curva de aprendizaje alta |

> [!bug] Prohibido
> `AsyncTask` · `new Thread()` para I/O · `Handler` como planificador · `.get()` bloqueante en el hilo principal · `Thread.sleep()`. Ver [[Lista Negra de APIs Android]].

---

## `AppExecutors` — el pool compartido

```java
@Singleton
public class AppExecutors {

    private final Executor io;
    private final Executor computation;
    private final Executor main;

    @Inject
    public AppExecutors() {
        this.io = Executors.newFixedThreadPool(3);
        this.computation = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors());
        this.main = new MainThreadExecutor();
    }

    public Executor io()          { return io; }
    public Executor computation() { return computation; }
    public Executor main()        { return main; }
}
```

**Todo I/O** (red, disco, BD, JSON grande, criptografía) va en `io()`. El resultado se publica con `postValue()` — nunca se toca una vista fuera del hilo principal.

## Regla crítica: el Executor se INYECTA

> [!danger] Por qué importa
> Un ViewModel que hace `Executors.newSingleThreadExecutor()` dentro de sí mismo **no se puede testear**: el test termina antes de que el hilo de fondo publique el resultado, y la aserción falla de forma intermitente.
>
> Con el Executor inyectado, el test pasa `MoreExecutors.directExecutor()` y todo corre síncrono y determinista.

```java
// ❌ Intesteable
public LoginViewModel(AuthRepository repo) {
    this.executor = Executors.newSingleThreadExecutor();
}

// ✅ Testeable
public LoginViewModel(AuthRepository repo, Executor executor) {
    this.executor = executor;
}
```

> [!warning] Estado en este proyecto
> `LoginViewModel` **crea su propio executor internamente** — el anti-patrón de arriba. Es la razón por la que hoy no tiene test. Registrado como **P-005** en [[Deuda Técnica - Pendientes]].

## Ciclo de vida

- El `ExecutorService` creado por el ViewModel se cierra en `onCleared()`.
- Si el pool es compartido vía DI (`@Singleton AppExecutors`), **no** se cierra desde un ViewModel.
- Trabajo que debe sobrevivir a la pantalla (subir un pedido) **no va en el ViewModel**: va en `WorkManager`. Ver [[Offline-First con Room y Outbox]].

## Con desugaring: `CompletableFuture`

Con `isCoreLibraryDesugaringEnabled = true` y `minSdk 24`, `java.util.concurrent.CompletableFuture` está disponible y permite encadenar sin callbacks anidados:

```java
CompletableFuture
    .supplyAsync(() -> repo.obtenerMenu(), executors.io())
    .thenAcceptAsync(resultado -> estado.setValue(map(resultado)), executors.main());
```

Ver [[Toolchain Android 2026 - AGP, Gradle y JDK]].

---

## Relaciones

- [[MVVM en Android (ViewModel + LiveData)]]
- [[Librerias Java-Friendly vs Kotlin-Only]]
- [[Estrategia de Pruebas Android]]
- [[Lista Negra de APIs Android]]
- [[Deuda Técnica - Pendientes]] — P-005

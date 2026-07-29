---
title: "Estrategia de Pruebas Android"
tags:
  - referencia
  - pruebas
  - calidad
date: 2026-07-29
lifecycle: verified
---

# Estrategia de Pruebas Android

> [!abstract] Regla
> **Todo código nuevo trae su prueba.** Sin prueba, la entrega está incompleta. La cobertura es piso, no meta.

## Qué se prueba y con qué

| Capa | Herramienta | Qué se cubre |
|---|---|---|
| `domain` | JUnit4 + Truth (JVM puro, sin Robolectric) | Todos los `UseCase`, mappers, reducers, validaciones |
| `ui` (ViewModel) | JUnit4 + `InstantTaskExecutorRule` + fakes | Transiciones de `UiState`: inicial → cargando → éxito/error |
| `data` (DAO) | Room in-memory + `androidTest` | Queries, índices, borrado lógico |
| `data` (migraciones) | `MigrationTestHelper` | **Toda** migración de esquema |
| `data` (repositorio) | Fakes de los DataSource | Política offline-first, resolución de conflictos |
| UI end-to-end | Espresso | *Happy path* de cada pantalla + regresión de navegación |
| Rendimiento | Macrobenchmark | Arranque y scroll (ver [[Presupuestos de Rendimiento en Gama Baja]]) |

## Reglas

1. **Prefiere *fakes* sobre *mocks*.** Un `FakeAuthRepository` que devuelve `Result.ok(...)` es más legible y más robusto que tres líneas de `when(...).thenReturn(...)`. Mockea solo fronteras que no controlás.
2. **Aserciones reales.** `assertTrue(true)` y tests que solo verifican que no explota no cuentan.
3. **Nada de red real ni de reloj del sistema.** El tiempo y la aleatoriedad **se inyectan** (`Clock`, `TiempoDataSource`) precisamente para poder testear.
4. **Cobertura mínima: 80% en `domain` y `data`, 60% global.**
5. Un test que falla intermitentemente se arregla o se borra — un test *flaky* es peor que ningún test porque entrena al equipo a ignorar el rojo.

## CI obligatorio

```
assembleDebug → lint → test → connectedCheck → bundleRelease
```

- Android Lint con `abortOnError = true`.
- Formato con Spotless / google-java-format.
- **PR rojo no se mergea.** Sin excepciones.

## Ejemplo — test de ViewModel con fake

```java
public class LoginViewModelTest {

    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    @Test
    public void login_conCredencialesInvalidas_publicaError() {
        FakeAuthRepository repo = FakeAuthRepository.queFalla("Credenciales inválidas");
        LoginViewModel vm = new LoginViewModel(repo, MoreExecutors.directExecutor());

        vm.login("a@b.com", "mala");

        EstadoLogin estado = vm.getEstado().getValue();
        assertThat(estado.getError()).isEqualTo("Credenciales inválidas");
        assertThat(estado.isCargando()).isFalse();
        assertThat(estado.getSesion()).isNull();
    }
}
```

> [!warning] Estado en este proyecto
> **No hay ni una sola prueba propia.** Solo quedan los dos archivos de ejemplo que generó Android Studio (`ExampleUnitTest`, `ExampleInstrumentedTest`), que no prueban nada del proyecto.
>
> Además, `LoginViewModel` **no es testeable hoy**: crea su propio `ExecutorService` internamente en vez de recibirlo inyectado, así que un test no puede forzar ejecución síncrona. Registrado como **P-005** en [[Deuda Técnica - Pendientes]].

---

## Relaciones

- [[Gate de Autoverificación]]
- [[MVVM en Android (ViewModel + LiveData)]]
- [[Asincronia en Java para Android]]
- [[Presupuestos de Rendimiento en Gama Baja]]
- [[Deuda Técnica - Pendientes]] — P-005

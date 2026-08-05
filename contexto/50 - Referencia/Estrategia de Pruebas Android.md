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
| `data` (DAO) | Robolectric (`@Config(sdk=35)`) + Room in-memory | Queries, índices, borrado lógico — en la JVM local, sin emulador |
| `data` (migraciones) | `MigrationTestHelper` (constructor `SQLiteDriver` + `AndroidSQLiteDriver`) | **Toda** migración de esquema; ver [[Room 2.8.4 y MigrationTestHelper en Windows (Robolectric)]] |
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
> **La suite pasó de cero a cubierta en la Fase 2b (2026-08-01):** `./gradlew
> testDebugUnitTest` corre **195 tests** con aserciones reales — `domain`, ViewModels,
> DAOs (Robolectric + Room in-memory), outbox, clasificador de errores, migración y
> repositorios con fakes. P-005 quedó cerrado (los ViewModels ya inyectan su
> `ExecutorService`). **420 tests** al cerrar el Fase 0b (2026-08-05).

## Cadencia de corridas — feedback rápido durante el trabajo, suite completa como gate

Módulo Gradle **único** (`app`): no hay forma barata de que Gradle sepa "qué tests toca este
cambio" — cualquier edición de Java invalida `testDebugUnitTest` entero. Eso no significa
que haya que esperar la suite completa después de cada línea. Dos momentos, dos comandos:

**Mientras se está escribiendo/iterando una pieza** — filtrar a la clase que importa, para
feedback en segundos:

```bash
./gradlew testDebugUnitTest --tests "com.example.proyectofinalrestaurante.core.ProveedorDeTokenTest"
```

**Antes de decir "esto funciona" o hacer commit** — la suite completa, una sola vez, sobre
el estado final:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Ese segundo paso **no es opcional ni negociable** — es el único momento en que se atrapa el
caso real: un cambio de firma en una clase compartida (ej. agregar un campo a `Sesion` en
P-009) que rompe silenciosamente un test en un paquete que nadie tocó a propósito. Correr
solo la clase filtrada nunca lo hubiera visto.

**Medido en esta sesión (2026-08-05, ~420 tests):** la suite completa corre en **15-30 s**;
una clase filtrada, una vez compilado, en **3-5 s**. La ganancia no es evitar la corrida
completa — es no pagarla después de *cada* edición mientras el código todavía está
cambiando, y pagarla **una vez** al final, que es el único punto donde su resultado importa.
A medida que la suite crezca, la diferencia entre "una clase" y "las 400+" solo se agranda.

> [!danger] Lo que esto NO habilita
> Nunca reportar "tests en verde" ni commitear basado en una corrida filtrada. El filtro es
> una herramienta de iteración, no un sustituto del gate. Si el mensaje final es "funciona"
> o "listo para commit", tiene que estar respaldado por la corrida completa, no por la
> filtrada.

---

## Relaciones

- [[Gate de Autoverificación]]
- [[MVVM en Android (ViewModel + LiveData)]]
- [[Asincronia en Java para Android]]
- [[Presupuestos de Rendimiento en Gama Baja]]
- [[Deuda Técnica - Pendientes]] — P-005

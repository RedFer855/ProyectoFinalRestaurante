---
title: Result Pattern
tags:
  - patron
  - manejo-errores
  - java
lifecycle: verified
aliases:
  - Result<T>
---

# Result Pattern

> [!abstract] Definición
> En lugar de dejar que una excepción de red/parseo llegue cruda hasta la UI, los métodos de `data` y `domain` retornan un `Result<T>` que es éxito o fallo. El llamador maneja ambos casos explícitamente. **Las excepciones no son control de flujo.**

---

## El tipo Result

```java
public final class Result<T> {
    private final boolean success;
    private final T value;
    private final String error;

    private Result(T value)      { this.success = true;  this.value = value; this.error = null; }
    private Result(String error) { this.success = false; this.value = null;  this.error = error; }

    public static <T> Result<T> ok(T value)     { return new Result<>(value); }
    public static <T> Result<T> fail(String e)  { return new Result<>(e); }

    public boolean isSuccess() { return success; }
    public T getValue()        { return value; }
    public String getError()   { return error; }
}
```

---

## Evolución prevista: `AppException` y `ErrorMapper`

Hoy `Result` lleva un `String` de error. Eso funciona con una pantalla, pero **no permite que la UI reaccione distinto según el tipo de fallo** (reintentar si es de red, mandar al login si es 401, marcar el campo si es de validación).

La forma completa del patrón, a adoptar cuando aparezca el segundo repositorio:

```java
public abstract class AppException extends Exception {
    public static final class NoConnection  extends AppException { }
    public static final class Timeout       extends AppException { }
    public static final class Unauthorized  extends AppException { }
    public static final class ServerError   extends AppException { public final int code; … }
    public static final class Validation    extends AppException { public final String field; … }
    public static final class Unknown       extends AppException { }
}
```

- **`safeApiCall(...)` es el único punto** donde se capturan `IOException`/`HttpException`.
- **`ErrorMapper` traduce `AppException` → `@StringRes`.** La UI **nunca** muestra `e.getMessage()` crudo: ni está traducido, ni es comprensible, y a veces filtra detalles del backend.
- Los `UseCase` devuelven `Result<T>`, **jamás** lanzan excepciones al ViewModel.

Registrado como **P-016** en [[Deuda Técnica - Pendientes]].

---

## Repositorio con Result

```java
@Override
public Result<Sesion> login(String correo, String contrasenia) {
    try {
        Response<LoginResponseDto> response =
                api.login(new LoginRequestDto(correo, contrasenia)).execute();
        if (!response.isSuccessful() || response.body() == null) {
            return Result.fail("Correo o contraseña incorrectos");
        }
        return Result.ok(mapear(response.body()));
    } catch (IOException ex) {
        return Result.fail("Sin conexión al servidor. Intentá de nuevo.");
    }
}
```

## ViewModel con Result

```java
Result<Sesion> resultado = authRepository.login(correo, contrasenia);
estado.postValue(resultado.isSuccess()
        ? EstadoLogin.exito(resultado.getValue())
        : EstadoLogin.error(resultado.getError()));
```

---

## Beneficios

| Sin Result | Con Result |
|---|---|
| La excepción llega a la Activity → crash | Error con mensaje, listo para mostrar |
| `try/catch` disperso por toda la UI | Manejo explícito en un solo lugar |
| La app se rompe en silencio | La UI muestra un estado de error controlado |
| Difícil de testear | `resultado.isSuccess()` es trivial de verificar |

---

## Anti-patrones

- `catch (Exception e) {}` vacío — traga fallos y produce bugs invisibles.
- `printStackTrace()` — no llega a crash reporting.
- Lanzar la excepción de red desde el repositorio "para que la maneje el ViewModel".
- Mostrar `e.getMessage()` al usuario.

Ver [[Lista Negra de APIs Android]].

---

## Estado en el proyecto

> [!success] Fase 1
> - ✅ `Result<T>` implementado en `domain`
> - ✅ `SupabaseAuthRepository.login(...)` lo retorna
> - ⬜ `AppException` + `ErrorMapper` — pendiente (**P-016**)
> - ⬜ `safeApiCall` compartido — pendiente hasta el segundo repositorio (**P-001**)

---

## Relaciones

- [[Base Repository con manejo de errores]]
- [[Clean Architecture]]
- [[Repository Pattern]]
- [[UiState Inmutable y Flujo Unidireccional]]
- [[Catálogo de Patrones Android]]
- [[Deuda Técnica - Pendientes]] — P-001, P-016

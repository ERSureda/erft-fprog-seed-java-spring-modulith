# FProg Standards — Manual de Excepciones y Errores (`EXCEPTIONS`)

| Propiedad | Valor Normativo |
| :--- | :--- |
| **Sistema / Preset (`preset_id`)** | `FProg STANDARDS` (Fase 2: Construcción Autónoma por Agentes) · `java-spring-modulith` (Java 21+ LTS / Spring Boot 3 / PostgreSQL 17 / Spring Modulith) |
| **Documento / Versión / Estado** | `EXCEPTIONS.md` (Jerarquía `BaseException`, Tipado `ErrorCode` y Contrato `ErrorResponse`) · `v1.0.0` · `NORMATIVO` (SSOT) |
| **Documento Padre Asociado** | `.standards/ARCHITECTURE.md` (Reglas `INP-03`, `SHR-04` y `ADR_003`) |
| **Alcance y Entradas (Fase 1 — Rails)** | Aplicable a todos los servicios, capas de dominio (`docs/modules/<m>/DOMAIN.md` Sección 5), contratos de aplicación y controladores web (`docs/modules/<m>/API.md` Sección 4), y workers asíncronos (`docs/04-INTEGRATION.md`) |
| **Repositorio Oficial (Submódulo)** | `erft-fprog-preset-java-spring-modulith` (`standards/` + `seed/`) |
| **Ubicación Catálogo / Consumidor** | `presets/java-spring-modulith/standards/EXCEPTIONS.md` ──► `.standards/EXCEPTIONS.md` (hidratado de forma inmutable vía `./scripts/sync-standards.sh` en `Gate 2`) |
| **Quality Gate Asociado** | `Gate 3.2 (Biyección ErrorCode DOMAIN.md ↔ API.md)` y `Gate 4 (./gradlew check)` |

---

> 🎯 **Reglas del Juego (FProg STANDARDS — Gestión de Errores y Excepciones):**  
> En FProg los errores no son accidentes impredecibles ni eventos caóticos; son **flujos alternativos normales del negocio** (un saldo insuficiente, un recurso no encontrado) o **fallos técnicos controlados** (caída de red, timeout de base de datos).
>
> Para garantizar que el sistema sea ultrarrápido, seguro por defecto y consistente en todos sus endpoints, en este estándar **queda terminantemente prohibido**:
> 1. **Crear subclases de excepción locales en los módulos:** Prohibido crear clases como `OrderNotFoundException` o `PaymentFailedException` dentro de `<module_id>.domain.exception.*` (`ERR-07`). Se reutiliza siempre el catálogo cerrado de 7 excepciones de `shared` parametrizado con el enum `<Subdominio>Error`.
> 2. **Lanzar excepciones genéricas o silenciarlas:** Prohibido lanzar `RuntimeException` o `Exception` planas (`ERR-01`) y prohibido silenciar errores con bloques `catch (Exception e) {}` vacíos (`ERR-05`).
> 3. **Generar trazas de pila en errores funcionales:** Los errores de negocio deben tener coste cero en la JVM (`writableStackTrace = false`, `ERR-02`).
> 4. **Filtrar detalles técnicos al cliente web:** Prohibido mostrar nombres de tablas relacionales, sentencias SQL o trazas de ejecución en la respuesta HTTP pública (`ERR-04`, `INP-03`).

---

## 1. Filosofía y Principios de Gestión de Errores

El subsistema de errores y excepciones se rige por tres pilares de ingeniería inquebrantables:

### 1.1. Excepciones de Coste Cero o Ultrabajo (*Zero / Low-Overhead*)
En la máquina virtual de Java (JVM), entre el 90% y el 95% del coste computacional al instanciar y lanzar una excepción proviene de capturar, inspeccionar y rellenar la traza de pila de ejecución (*stack trace*) mediante la llamada nativa `fillInStackTrace()`.
* Las **excepciones de negocio, conflicto y validación sintáctica** son flujos de control alternativos normales y esperados, **no anomalías técnicas del sistema**.
* Toda excepción de negocio hereda de `BaseException`, la cual traslada el flag `category.capturesDiagnostics()` al parámetro nativo `writableStackTrace` del constructor de `Throwable`.
* Al ser este valor `false` para todos los errores funcionales, la JVM **desactiva la generación de trazas de pila y la inspección del hilo de ejecución**, eliminando el consumo innecesario de memoria y CPU en rutas críticas de alto tráfico (*hot-paths*).
* **Reutilización del catálogo cerrado de `shared`:** Para evitar la proliferación de clases vacías (*boilerplate* sin lógica añadida), el catálogo ubicado en `<namespace.base>.shared.domain.exception` es cerrado y autosuficiente. Los módulos de negocio no crean subclases por herencia; la especificidad se logra instanciando directamente la excepción de `shared` parametrizada con el `ErrorCode` del módulo.

### 1.2. Tratamiento Diferenciado: Negocio vs. Técnico
* **Excepciones de Negocio / Dominio (`capturesDiagnostics() == false`):** Se registran con nivel `WARN` en los logs del servidor sin traza forense de pila, informando únicamente del código semántico y el motivo funcional.
* **Excepciones Técnicas / Infraestructura (`INTERNAL`, `capturesDiagnostics() == true`):** Representan fallos imprevistos del sistema o fallos de red (pérdida de conexión a base de datos, caídas de almacenamiento, timeouts de socket). Estas **sí capturan la traza forense completa** (`writableStackTrace = true`) y se registran con nivel `ERROR` para posibilitar el diagnóstico posterior.

### 1.3. Seguridad por Defecto (*Safe-by-Default*)
* Ningún detalle interno de implementación (nombres de tablas relacionales, fragmentos de sentencias SQL, IPs, puertos o volcados de memoria) se expone jamás al exterior en errores técnicos.
* Todo fallo técnico no controlado es interceptado en el perímetro por `GlobalExceptionHandler` y enmascarado hacia el cliente con el mensaje genérico: `"An unexpected error occurred"`.

---

## 2. Guía Rápida: ¿Qué Excepción Lanzo? (Árbol de Decisión)

Para eliminar cualquier duda al programar, consulta esta tabla canónica:

| Situación en el Código | Excepción a Lanzar (en `shared`) | Categoría / HTTP | Comportamiento Asíncrono | Ejemplo Canónico |
| :--- | :--- | :---: | :---: | :--- |
| **El registro o entidad buscada no existe en la base de datos.** | `ResourceNotFoundException` | `NOT_FOUND` (**404**) | Descarte / DLQ (No reintentable) | `throw new ResourceNotFoundException(OrderingError.ORDERING_ORDER_NOT_FOUND, "Order not found: " + id);` |
| **El recurso existe pero no admite la acción (transición FSM inválida, duplicado o conflicto de negocio).** | `ConflictException` | `CONFLICT` (**409**) | Descarte / DLQ (No reintentable) | `throw new ConflictException(OrderingError.ORDERING_ORDER_ALREADY_SHIPPED, "Cannot cancel shipped order");` |
| **El formato de entrada es inválido o no cumple las restricciones.** | `ValidationException` | `VALIDATION` (**400**) | Descarte / DLQ (No reintentable) | `throw new ValidationException(CommonError.VALIDATION_FAILED, "Invalid payload", violations);` |
| **La petición carece de credenciales o token válido.** | `UnauthenticatedException` | `UNAUTHENTICATED` (**401**) | Descarte / DLQ (No reintentable) | `throw new UnauthenticatedException("Missing security context");` |
| **El usuario está autenticado pero no tiene permisos ni rol para este inquilino.** | `ForbiddenException` | `FORBIDDEN` (**403**) | Descarte / DLQ (No reintentable) | `throw new ForbiddenException("User lacks tenant role: ADMIN");` |
| **Fallo inesperado de base de datos local o driver JDBC.** | `InfrastructureException` | `INTERNAL` (**500**) | Reintento con backoff -> DLQ | `throw new InfrastructureException("Database connection timeout", sqlException);` |
| **Fallo al comunicarse con un servicio HTTP o API externa.** | `ExternalServiceException` | `INTERNAL` (**500**) | Reintento con backoff -> DLQ | `throw new ExternalServiceException("Payment provider unavailable", restException);` |

---

## 3. Catálogo Normativo de Reglas de Error (`ERR`) y Guardianes CI

* **`ERR-01 · MUST` [A] — Herencia Obligatoria de `BaseException`**
    * **Regla:** Toda excepción lanzada en la capa de dominio o aplicación debe heredar directa o indirectamente de `BaseException`.
    * **Motivación:** Garantiza que cualquier error originado en el negocio porte un `ErrorCode` tipado y pueda ser gestionado de forma uniforme por el middleware web.
    * **Guardarraíl Técnico:** Heredar de `<namespace.base>.shared.domain.exception.BaseException`. Prohibido lanzar `RuntimeException` planas.
    * **Guardián CI:** ArchUnit y tests de plataforma (`DomainErrorsAndExceptionsTest`).
* **`ERR-02 · MUST` [A] — Supresión de Trazas de Pila en Errores Funcionales**
    * **Regla:** Las excepciones funcionales y de validación deben desactivar la inspección de trazas de pila en el runtime de la JVM.
    * **Motivación:** Generar stack traces consume el 90-95% del coste de una excepción. Al desactivarlas en errores esperados de negocio, el sistema soporta picos masivos de tráfico sin degradación de CPU.
    * **Guardarraíl Técnico:** `category.capturesDiagnostics() == false` mapeado al parámetro nativo `writableStackTrace = false` en `BaseException`.
    * **Guardián CI:** Tests unitarios de `BaseExceptionTest`.
* **`ERR-03 · MUST` [A] — Tipado Estricto de Códigos mediante `ErrorCode`**
    * **Regla:** Todo error del sistema debe asociarse obligatoriamente a un código alfanumérico inmutable y tipado mediante la implementación de `ErrorCode` en `UPPER_SNAKE_CASE`.
    * **Motivación:** Permite que las aplicaciones cliente (Frontend, Mobile, microservicios) muestren mensajes traducidos sin depender de cadenas de texto frágiles en inglés o español.
    * **Guardarraíl Técnico:**
        * Errores de módulo: `[MODULO]_[ENTIDAD]_[MOTIVO]` en biyección 1:1 entre `DOMAIN.md` y `API.md`.
        * Errores de plataforma (`CommonError`): identificadores directos sin prefijo (`VALIDATION_FAILED`, `RESOURCE_NOT_FOUND`, etc.).
        * Precondiciones defensivas (`Objects.requireNonNull`) y validaciones de campo: tokens estructurados `[ENTIDAD]_[CAMPO]_[REGLA]`.
    * **Guardián CI:** Linter documental de compuerta `Gate 3.2` y pruebas de integración.
* **`ERR-04 · MUST` [A] — Contrato Universal `ErrorResponse`**
    * **Regla:** Toda respuesta de error en la API pública debe serializarse bajo el modelo estándar `ErrorResponse`.
    * **Motivación:** Los clientes que consumen la API tienen un único contrato predecible para procesar cualquier fallo.
    * **Guardarraíl Técnico:** Omitir el campo `errors` cuando no contenga violaciones de campo mediante `@JsonInclude(JsonInclude.Include.NON_EMPTY)`.
    * **Guardián CI:** Tests de slice web `@WebMvcTest`.
* **`ERR-05 · NEVER` [A] — Prohibición de Silenciar Excepciones**
    * **Regla:** Jamás se capturarán excepciones de forma genérica para silenciarlas (`catch (Exception e) {}` vacío).
    * **Motivación:** Silenciar errores impide el rollback de las transacciones de base de datos, corrompe el estado del sistema y oculta bugs críticos en producción.
    * **Guardarraíl Técnico:** Prohibidos bloques `catch` vacíos o que no relancen ni registren la causa.
    * **Guardián CI:** Linter estático y ArchUnit.
* **`ERR-06 · MUST` [R] — Encadenamiento Forense Obligatorio de Causa**
    * **Regla:** Todo fallo técnico capturado en adaptadores secundarios (`InfrastructureException`, `ExternalServiceException`) debe encadenar obligatoriamente la causa técnica original (`cause`).
    * **Motivación:** Si ocultas la excepción raíz, los ingenieros de guardia en producción no podrán saber si la base de datos falló por timeout, por credenciales o por saturación de disco.
    * **Guardarraíl Técnico:** Pasar siempre `throwable` al constructor de la excepción de infraestructura.
    * **Guardián CI:** Revisión en PR `[R]`.
* **`ERR-07 · NEVER` [A/R] — Prohibición de Crear Subclases de Excepción en Módulos**
    * **Regla:** Queda prohibido crear subclases de excepción dentro de los submódulos funcionales (`<module_id>.domain.exception.*`).
    * **Motivación:** Evita duplicar decenas de clases vacías (`OrderNotFoundException`, `CustomerNotFoundException`) que añaden miles de líneas de código redundante sin aportar comportamiento diferencial.
    * **Guardarraíl Técnico:** Los módulos consumen directamente las excepciones provistas en `shared` parametrizadas con su propio `ErrorCode`.
    * **Guardián CI:** `DomainRulesArchTest` (ninguna clase en `..<module_id>..` hereda de `Throwable`) y revisión en PR `[R]`.

---

## 4. Jerarquía Canónica y Contratos de `shared/`

Ubicación transversal: `<namespace.base>.shared.domain.{error, exception}`.

### 4.1. Árbol de Herencia

```text
RuntimeException (JDK)
 └── BaseException (abstracta: porta ErrorCode y ErrorCategory)
      ├── ResourceNotFoundException       [404 NOT_FOUND       — Sin traza (writableStackTrace = false)]
      ├── ConflictException               [409 CONFLICT        — Sin traza (writableStackTrace = false)]
      ├── ValidationException             [400 VALIDATION      — Sin traza (writableStackTrace = false)]
      ├── ForbiddenException              [403 FORBIDDEN       — Sin traza (writableStackTrace = false)]
      ├── UnauthenticatedException        [401 UNAUTHENTICATED — Sin traza (writableStackTrace = false)]
      ├── InfrastructureException         [500 INTERNAL        — CON TRAZA FORENSE (writableStackTrace = true)]
      └── ExternalServiceException        [500 INTERNAL        — CON TRAZA FORENSE (writableStackTrace = true)]
```

### 4.2. Referencia de Firmas Canónicas en `shared.domain.{error, exception}` (*Context Pruning*)

| Paquete | Tipo | Constructores y Métodos Públicos Canónicos |
| :--- | :--- | :--- |
| `domain.error` | `interface ErrorCode` | `String code()` |
| `domain.error` | `enum CommonError implements ErrorCode` | Valores estándar de plataforma sin prefijo: `VALIDATION_FAILED`, `RESOURCE_NOT_FOUND`, `RESOURCE_CONFLICT`, `UNAUTHENTICATED`, `FORBIDDEN`, `INTERNAL_SERVER_ERROR`. |
| `domain.error` | `record FieldViolation(String field, String message)` | Representa una violación individual de validación sintáctica o de campo. |
| `domain.exception` | `ResourceNotFoundException` | `public ResourceNotFoundException(ErrorCode errorCode, String message)`<br>`public ResourceNotFoundException(String message)` *(usa `CommonError.RESOURCE_NOT_FOUND`)*<br>`public ResourceNotFoundException(Class<?> entityClass, Object id)`<br>`public ResourceNotFoundException(ErrorCode errorCode, Class<?> entityClass, Object id)` |
| `domain.exception` | `ConflictException` | `public ConflictException(ErrorCode errorCode, String message)`<br>`public ConflictException(ErrorCode errorCode)`<br>`public ConflictException(String message)` *(usa `CommonError.RESOURCE_CONFLICT`)* |
| `domain.exception` | `ValidationException` | `public ValidationException(ErrorCode errorCode, String message, List<FieldViolation> violations)`<br>`public ValidationException(ErrorCode errorCode, String message)`<br>`public ValidationException(String message, List<FieldViolation> violations)` *(usa `CommonError.VALIDATION_FAILED`)*<br>`public ValidationException(String message)` *(usa `CommonError.VALIDATION_FAILED`)*<br>`public List<FieldViolation> violations()` |
| `domain.exception` | `UnauthenticatedException` | `public UnauthenticatedException(String message)` *(usa `CommonError.UNAUTHENTICATED`)* |
| `domain.exception` | `ForbiddenException` | `public ForbiddenException(String message)` *(usa `CommonError.FORBIDDEN`)* |
| `domain.exception` | `InfrastructureException` | `public InfrastructureException(String message, Throwable cause)` *(usa `CommonError.INTERNAL_SERVER_ERROR`)*<br>`public InfrastructureException(String message)` *(usa `CommonError.INTERNAL_SERVER_ERROR`)* |
| `domain.exception` | `ExternalServiceException` | `public ExternalServiceException(ErrorCode errorCode, String message, Throwable cause)`<br>`public ExternalServiceException(ErrorCode errorCode, String message)`<br>`public ExternalServiceException(String message, Throwable cause)` *(usa `CommonError.INTERNAL_SERVER_ERROR`)* |

### 4.3. Implementación de la Jerarquía Base en Código

#### Clase Base Abstracta (`BaseException`)

```java
package com.empresa.proyecto.shared.domain.exception;

import com.empresa.proyecto.shared.domain.error.ErrorCategory;
import com.empresa.proyecto.shared.domain.error.ErrorCode;

public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;
    private final ErrorCategory category;

    protected BaseException(ErrorCode errorCode, ErrorCategory category, String message) {
        this(errorCode, category, message, null);
    }

    protected BaseException(ErrorCode errorCode, ErrorCategory category, String message, Throwable cause) {
        super(
                message,
                cause,
                true,                              // enableSuppression
                category.capturesDiagnostics()      // writableStackTrace (false = coste cero)
        );
        this.errorCode = errorCode;
        this.category = category;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public ErrorCategory category() {
        return category;
    }
}
```

#### Clasificación Semántica (`ErrorCategory`)

```java
package com.empresa.proyecto.shared.domain.error;

public enum ErrorCategory {
    VALIDATION(false),       // HTTP 400 | Async: No reintentable (DLQ) | Traza: false
    UNAUTHENTICATED(false),  // HTTP 401 | Async: No reintentable (DLQ) | Traza: false
    FORBIDDEN(false),        // HTTP 403 | Async: No reintentable (DLQ) | Traza: false
    NOT_FOUND(false),        // HTTP 404 | Async: No reintentable (DLQ) | Traza: false
    CONFLICT(false),         // HTTP 409 | Async: No reintentable (DLQ) | Traza: false
    INTERNAL(true);          // HTTP 500 | Async: REINTENTABLE          | Traza: TRUE

    private final boolean capturesDiagnostics;

    ErrorCategory(boolean capturesDiagnostics) {
        this.capturesDiagnostics = capturesDiagnostics;
    }

    public boolean capturesDiagnostics() {
        return capturesDiagnostics;
    }
}
```

---

## 5. Contratos de Identificación y Salida Web

### 5.1. Contrato `ErrorCode` e Implementación en Módulos

```java
package com.empresa.proyecto.shared.domain.error;

public interface ErrorCode {
    String code();
}
```

En cada módulo de negocio (`<namespace.base>.<module_id>.domain`), se declara un único enum tipado `<Subdominio>Error` implementando `ErrorCode`:

```java
package com.empresa.proyecto.ordering.domain;

import com.empresa.proyecto.shared.domain.error.ErrorCode;

public enum OrderingError implements ErrorCode {
    ORDERING_ORDER_INVALID_STATUS,
    ORDERING_ORDER_NOT_CONFIRMED,
    ORDERING_ORDER_ALREADY_SHIPPED,
    ORDERING_ORDER_NOT_FOUND,
    ORDERING_STOCK_INSUFFICIENT,
    ORDERING_PAYMENT_GATEWAY_TIMEOUT;

    @Override
    public String code() {
        return name();
    }
}
```

### 5.2. Contrato Universal de Salida JSON (`ErrorResponse`)

Ubicado en `<namespace.base>.shared.infrastructure.adapter.in.web.exception` (conforme a la semilla `seed/` y la topología de `ARCHITECTURE.md` Sección 2.1):

```java
package com.empresa.proyecto.shared.infrastructure.adapter.in.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.empresa.proyecto.shared.domain.error.FieldViolation;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
    int status,
    String code,
    String detail,
    List<FieldViolation> errors
) {
    public ErrorResponse(int status, String code, String detail) {
        this(status, code, detail, List.of());
    }
}
```

#### Ejemplos de Salida Serializada:

* **Error simple (HTTP 409 Conflict):** Ocurre cuando un agregado bloquea una transición inválida. El campo `errors` no se emite gracias a `@JsonInclude(NON_EMPTY)`.
  ```json
  {
    "status": 409,
    "code": "ORDERING_ORDER_ALREADY_SHIPPED",
    "detail": "Cannot cancel an order that has already shipped"
  }
  ```

* **Error de validación sintáctica (HTTP 400 Bad Request):** Emite tokens estructurados `[ENTIDAD]_[CAMPO]_[REGLA]` listados en el campo `errors`:
  ```json
  {
    "status": 400,
    "code": "VALIDATION_FAILED",
    "detail": "Validation failed for one or more fields",
    "errors": [
      {
        "field": "amount",
        "message": "ORDER_AMOUNT_REQUIRED"
      },
      {
        "field": "currency",
        "message": "ORDER_CURRENCY_INVALID_LENGTH"
      }
    ]
  }
  ```

---

## 6. Responsabilidades del Manejador Centralizado

### 6.1. Perímetro Síncrono (Web / API)

`GlobalExceptionHandler` (`@RestControllerAdvice` en `<namespace.base>.shared.infrastructure.adapter.in.web.exception`) centraliza la captura de excepciones y garantiza el cumplimiento del contrato:

```java
package com.empresa.proyecto.shared.infrastructure.adapter.in.web.exception;

import com.empresa.proyecto.shared.domain.error.CommonError;
import com.empresa.proyecto.shared.domain.error.ErrorCategory;
import com.empresa.proyecto.shared.domain.error.FieldViolation;
import com.empresa.proyecto.shared.domain.exception.BaseException;
import com.empresa.proyecto.shared.domain.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex) {
        HttpStatus status = mapCategoryToStatus(ex.category());

        if (ex.category().capturesDiagnostics()) {
            log.error("Internal technical failure [code={}]: {}", ex.errorCode().code(), ex.getMessage(), ex);
            return ResponseEntity.status(status).body(new ErrorResponse(
                status.value(),
                ex.errorCode().code(),
                "An unexpected error occurred"
            ));
        }

        log.warn("Operational business exception [code={}]: {}", ex.errorCode().code(), ex.getMessage());

        List<FieldViolation> errors = (ex instanceof ValidationException ve) ? ve.violations() : List.of();

        return ResponseEntity.status(status).body(new ErrorResponse(
            status.value(),
            ex.errorCode().code(),
            ex.getMessage(),
            errors
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> new FieldViolation(err.getField(), err.getDefaultMessage()))
            .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            CommonError.VALIDATION_FAILED.code(),
            "Validation failed for one or more fields",
            violations
        ));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ErrorResponse> handleUnhandled(Throwable ex) {
        log.error("Unhandled unexpected exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            CommonError.INTERNAL_SERVER_ERROR.code(),
            "An unexpected error occurred"
        ));
    }

    private HttpStatus mapCategoryToStatus(ErrorCategory category) {
        return switch (category) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
```

### 6.2. Perímetro Asíncrono (Workers / Consumidores de Eventos)

1. **Excepción de negocio (`capturesDiagnostics() == false`):** El worker descarta el mensaje o lo envía directamente a la Dead-Letter Queue (DLQ), confirmando el mensaje (ACK) y manteniendo el registro en `<module_id>.processed_events` para evitar bucles infinitos de reintento sobre datos inválidos.
2. **Excepción técnica transitoria (`capturesDiagnostics() == true`):**
    * **Liberación Defensiva de Idempotencia (`TRX-05`):** En el bloque `catch`, el worker invoca defensivamente `idempotencyGate.release(moduleSchema, eventId, consumerName)` para eliminar la marca en `processed_events`.
    * El relay o broker programa el reintento con retroceso exponencial (*backoff*); si supera el límite de reintentos, lo deriva a DLQ registrando el error técnico original.

---

## 7. Matriz Pedagógica de Anti-Patrones y Checklist de Pull Request

### 7.1. Matriz de Anti-Patrones Comunes

| Anti-Patrón | Por qué falla | Solución Normativa | Regla Asociada |
| :--- | :--- | :--- | :---: |
| **Proliferación de Subclases por Módulo** | Crear clases hijas vacías (`UserConflictException`, `OrderNotFoundException`) genera decenas de archivos duplicados sin valor. | Usar directamente las excepciones canónicas de `shared` parametrizándolas con el `ErrorCode` del módulo. | `ERR-01`, `ERR-07` |
| **Silenciar Excepciones (`catch {}` vacío)** | Provoca inconsistencia transaccional al impedir el rollback y oculta errores críticos en producción. | Dejar propagar las excepciones hacia el manejador central o relanzar excepciones tipadas. | `ERR-05`, `TRX-01` |
| **Manejadores Locales por Controlador** | Fragmenta el contrato de salida y rompe la estandarización del esquema `ErrorResponse`. | Centralizar toda captura y serialización de errores en `GlobalExceptionHandler`. | `ERR-04`, `INP-03` |
| **Lanzar Excepciones Genéricas (`RuntimeException`)** | Oculta la naturaleza del fallo retornando HTTP 500 para errores funcionales y degrada métricas operativas. | Lanzar exclusivamente subclases tipadas derivadas de `BaseException` (`ResourceNotFoundException`, `ConflictException`, etc.). | `ERR-01`, `ERR-03` |
| **Omitir Causa Original en Fallos Técnicos** | Destruye la cadena de error original, imposibilitando el diagnóstico forense en los logs del servidor. | Encadenar siempre el error original (`cause`) en constructores de excepciones de infraestructura. | `ERR-06` |
| **Reintentar Errores de Negocio en Colas** | Reintentar un payload con datos inválidos satura la cola, bloquea el procesamiento y desperdicia CPU. | Desviar directamente a DLQ o emitir ACK si el fallo es de categoría no técnica (`VALIDATION`, `CONFLICT`). | `ERR-02`, `TRX-05` |
| **Filtrar Datos Sensibles en Mensajes** | Los errores de negocio exponen su `detail` al cliente; emitir tokens, credenciales o secretos viola normativas de seguridad. | Restringir `detail` a explicaciones funcionales libres de datos personales o confidenciales. | `ERR-04`, `INP-03` |
| **Hardcodear Textos sin Código Semántico** | Obliga a clientes frontend y APIs consumidoras a parsear cadenas de texto frágiles en lugar de validar códigos estables. | Implementar `ErrorCode` por subdominio (`[MODULO]_[ENTIDAD]_[MOTIVO]`) y tokens estructurados para validación/precondición (`[ENTIDAD]_[CAMPO]_[REGLA]`). | `ERR-03`, `SHR-04` |

### 7.2. Checklist de Verificación para Pull Requests y Gate 4

- [ ] **Herencia de `BaseException` (`ERR-01`):** ¿Toda excepción lanzada en dominio o aplicación hereda directa o indirectamente de `BaseException`?
- [ ] **Supresión de Traza en Negocio (`ERR-02`):** ¿Las excepciones funcionales o de validación desactivan la captura de traza en el runtime de la JVM mediante `category.capturesDiagnostics() == false`?
- [ ] **Gramática de `ErrorCode` (`ERR-03`):** ¿Cada error funcional se asocia a un código alfanumérico tipado mediante la implementación de `ErrorCode` respetando la convención `[MODULO]_[ENTIDAD]_[MOTIVO]` en mayúsculas y en biyección 1:1 entre `DOMAIN.md` y `API.md`?
- [ ] **Contrato `ErrorResponse` Lean (`ERR-04`):** ¿La respuesta JSON final en la API pública cumple estrictamente la estructura de `ErrorResponse`, omitiendo `errors` cuando está vacío?
- [ ] **Cero Excepciones Silenciadas o Genéricas (`ERR-05`):** ¿El código está completamente libre de bloques `catch` vacíos y de lanzamientos de excepciones genéricas no tipadas (`RuntimeException`, `Exception`)?
- [ ] **Encadenamiento Forense (`ERR-06`):** ¿Los fallos técnicos en adaptadores de persistencia o red enlazan obligatoriamente el error original como causa (`cause`)?
- [ ] **Reutilización del Catálogo de `shared` (`ERR-07`):** ¿Se consumen directamente las excepciones canónicas de `shared` sin crear clases hijas innecesarias dentro del módulo?
- [ ] **Higiene de Detalles Internos (`ERR-04`, `INP-03`):** ¿Los mensajes de detalle funcional (`detail`) están completamente libres de secretos, contraseñas, tokens o volcados de base de datos?
---
name: generate-application-output-ports
description: >-
  Genera exclusivamente los puertos secundarios de salida (driven ports) de un Bounded Context
  en Java 21 dentro de application/port/out/: un *RepositoryPort por cada Aggregate Root
  existente en domain/model/ (save y findById) y un *QueryPort por recurso con métodos de
  proyección CQRS por cada *Query de docs/modules/<module_id>/API.md.
---

# Skill: Generación de Puertos de Salida (`generate-application-output-ports`)

## 1. ENTRADAS Y SALIDAS (I/O)

* **Lectura obligatoria**:
    1. `docs/02-TECH_STACK.md` -> Extraer `base_package` (`<base>`).
    2. Clases en `<base>.<module_id>.domain.model.*` que extienden `AggregateRoot<ID>` y contraste con `docs/modules/<module_id>/DOMAIN.md` y `docs/modules/<module_id>/schema.sql`.
    3. `docs/modules/<module_id>/API.md`: `Ruta Base REST` (o `Base Path REST`), tabla Sección 2 (`ID`, `Caso de Uso (*UseCase) y Consulta (*Query)`, `Canal de Entrada (REST | INTERNAL_PORT)`, `Contenedor de Salida (*Result | PageResult | CursorResult)`, `HTTP Status`) y bloques de detalle `3.X` / `3.4` (`Parámetros QRY-xx`, `Parámetro de Ruta`, `DTO de Salida Paginado`, `IN (Path)` y `DTO Objetivo (*Result)`).
    4. Clases existentes en `<base>.<module_id>.application`: `query/*Query`, `result/*Result` y `port/in/*UseCase`.
* **Política de Aborto Inmediato (`Fail-Fast`)**:
    * Abortar listando **todas** las carencias en una sola respuesta si:
        * Falta `package-info.java` (`generate-module-structure`), falta en `domain/model/` algún `AggregateRoot` definido en la especificación (`generate-domain-models`), falta algún `*Result` (`generate-application-results`) o falta algún enum referenciado (`generate-domain-enums`).
        * Hay consultas en alcance pero `API.md` no existe o conserva marcadores de plantilla (`[...]`, `<...>`) (nota: si una consulta tiene `Canal de Entrada: INTERNAL_PORT` sin ruta HTTP propia, no abortar; agruparla en el `<Recurso>QueryPort` principal del módulo deducido de `Ruta Base REST` o del `AggregateRoot`).
        * Un puerto ya existe, le faltan métodos, **y ya tiene una clase adaptadora implementándolo** (no modificar el puerto para no romper la compilación del adaptador; abortar e informar de los métodos faltantes).
* **Escritura exclusiva (`Blast Radius = 0`)**:
    * `src/main/java/<base>/<module_id>/application/port/out/<Agregado>RepositoryPort.java`
    * `src/main/java/<base>/<module_id>/application/port/out/<Recurso>QueryPort.java`
    * **PROHIBIDO** generar `*RepositoryPort` para entidades subordinadas (`BaseEntity`), crear adaptadores (`*PersistenceAdapter`, `*JdbcQueryAdapter`), exponer `@Entity` (`OUT-01`), devolver agregados en `*QueryPort` (`OUT-04`), o pasar el objeto `*Query`/`*Command` completo como parámetro del puerto. Omitir filas cuyo contrato declare exclusivamente un puerto `*Port` de entrada sin `*UseCase`/`*Query` (ej. `VerifyActiveUserPort`).

## 2. REGLAS INMUTABLES (`OUT-01`, `OUT-02`, `OUT-04`, `TRX-02`, `ARC-01`, `ARC-02`, `DOM-01`)

1. **Puerto de Escritura (`<Agregado>RepositoryPort`, `OUT-01`, `TRX-02`)**: Exactamente uno por cada clase que extienda `AggregateRoot<ID>` en `domain/model/`. Contiene **exclusivamente** dos métodos base (no inventar `findByEmail`, `existsBy...` ni `delete...` salvo requerimiento explícito del usuario):
    * `<Agregado> save(<Agregado> <agregadoCamelCase>);`
    * `Optional<<Agregado>> findById(<ID> id);`
2. **Puerto de Lectura CQRS (`<Recurso>QueryPort`, `OUT-02`, `OUT-04`)**: Agrupado por `Recurso` (primer segmento de `Ruta Base REST` en singular `PascalCase`, ej. `/api/v1/orders` o `/orders` -> `OrderQueryPort`, `/users` -> `UserQueryPort`; las consultas con `Canal de Entrada: INTERNAL_PORT` del módulo se integran en ese mismo `<Recurso>QueryPort`).
3. **Desglose de Parámetros en `<Recurso>QueryPort`**: Los parámetros del método se pasan **desglosados** (**nunca** el record `*Query`), respetando exactamente el mismo orden, nombres y tipos de los componentes del `<Operacion>Query` existente (o sin argumentos si la consulta no recibe parámetros).
4. **Pureza de Interfaz (`DOM-01`, `OUT-01`)**: Interfaces Java 21 públicas sin anotaciones de Spring, sin herencias de `JpaRepository` y sin métodos `default`. **Cero comentarios o bloques Javadoc.** Imports explícitos sin `*`.

## 3. TABLA DETERMINISTA DE MÉTODOS Y RETORNOS EN QUERY PORT (API.MD -> QUERY PORT)

| Salida Declarada en `API.md` (Sección 2 y Bloques `3.X` / `3.4`) | Tipo de Retorno en `<Recurso>QueryPort` | Nombre del Método en `<Recurso>QueryPort` |
| :--- | :--- | :--- |
| Objeto único (lectura puntual: `Contenedor de Salida: <Result>` / `DTO Objetivo (*Result)`) | `Optional<<Result>>` | `find<Result>By<Sufijo>` (donde `<Sufijo>` es lo que sigue a `By` en `<Operacion>`: `GetOrderById` + `OrderResult` -> `findOrderResultById`; si no contiene `By`, usar `find<Result>` o `<operacionCamelCase>`). |
| Paginación por cursor (`Contenedor de Salida` / `DTO de Salida Paginado: CursorResult<<ItemResult>>`) | `CursorResult<<ItemResult>>` | `<operacion>` en `lowerCamelCase` (`ListUsersByStatus` -> `listUsersByStatus`). |
| Paginación por offset (`Contenedor de Salida` / `DTO de Salida Paginado: PageResult<<ItemResult>>`) | `PageResult<<ItemResult>>` | `<operacion>` en `lowerCamelCase`. |

## 4. PLANTILLAS CANÓNICAS DE CÓDIGO

### 4.1. `<Agregado>RepositoryPort.java`

```java
package <base>.authentication.application.port.out;

import <base>.authentication.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(UUID id);
}
```

### 4.2. `<Recurso>QueryPort.java`

```java
package <base>.authentication.application.port.out;

import <base>.authentication.application.result.ListUsersByStatusItemResult;
import <base>.authentication.application.result.UserResult;
import <base>.authentication.domain.model.enums.UserStatus;
import <base>.shared.application.result.CursorResult;

import java.util.Optional;
import java.util.UUID;

public interface UserQueryPort {

    Optional<UserResult> findUserResultById(UUID userId);

    CursorResult<ListUsersByStatusItemResult> listUsersByStatus(UserStatus status, String cursor, int limit);
}
```

## 5. VERIFICACIÓN OBLIGATORIA (GATE)

Ejecutar en terminal antes de finalizar:
1. `./gradlew compileJava compileTestJava`
2. `./gradlew test --tests "*ModulithStructureTest*" --tests "*ApplicationRulesArchTest*"`
3. Emitir informe final conciso:
    * **Generados / Actualizados**: Puerto -> firmas de métodos añadidas.
    * **Sin puerto (`BaseEntity` subordinadas)** / **Omitidos (`*Port` de entrada)** / **Bloqueados por tener implementación activa**.
    * **Recordatorio `OUT-02`**: Queda pendiente generar `<Agregado>PersistenceAdapter` (`postgres/jpa/adapter/`) y `<Recurso>JdbcQueryAdapter` (`postgres/jdbc/adapter/`).

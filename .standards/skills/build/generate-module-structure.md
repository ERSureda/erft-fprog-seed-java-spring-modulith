---
name: generate-module-structure
description: >-
  Inicializa la estructura completa de directorios (main y test) y los 3 archivos base de
  gobernanza de un nuevo Bounded Context en Spring Modulith (package-info.java raíz con
  @ApplicationModule CLOSED, application/package-info.java con @NamedInterface("application")
  y enum <Modulo>Error implementando ErrorCode).
---

# Skill: Generación de Andamiaje Modular Hexagonal (`generate-module-structure`)

## 1. ENTRADAS Y SALIDAS (I/O)

* **Lectura obligatoria**:
    1. `docs/02-TECH_STACK.md` -> Extraer `base_package` (`<base>`).
    2. `docs/03-SUBDOMAINS.md` -> Extraer `module_id` y `DisplayName` del módulo.
    3. `docs/modules/<module_id>/DOMAIN.md` (Sección 5), `docs/modules/<module_id>/API.md` (Sección 4) o migración `V<N>__init_<module_id>_tables.sql` -> Extraer el catálogo de códigos de error `[MODULO]_[ENTIDAD]_[MOTIVO]`.
* **Política de Aborto Inmediato (`Fail-Fast`)**:
    * Abortar listando **todas** las carencias en una sola respuesta si:
        * `02-TECH_STACK.md` o `03-SUBDOMAINS.md` no existen, contienen marcadores de plantilla (`[...]`, `<...>`) o el `<module_id>` solicitado no figura en `03-SUBDOMAINS.md`.
        * No existe ninguna fuente de errores (`DOMAIN.md` / `API.md`) o un archivo de gobernanza ya existente difiere de la especificación.
* **Escritura exclusiva (`Blast Radius = 0`)**:
    * Árbol de directorios simétrico en `src/main/java/<base>/<module_id>/` y `src/test/java/<base>/<module_id>/`.
    * `src/main/java/<base>/<module_id>/package-info.java`
    * `src/main/java/<base>/<module_id>/application/package-info.java`
    * `src/main/java/<base>/<module_id>/domain/<Modulo>Error.java`
    * **PROHIBIDO** tocar otros módulos, alterar `shared/`, modificar migraciones Flyway o crear entidades, casos de uso o subclases de excepción (`ERR-07`).

## 2. REGLAS INMUTABLES (`ARC-01`, `ARC-02`, `ARC-03`, `DOM-01`, `ERR-03`, `ERR-07`, `SHR-04`, `OUT-02`)

1. **Aislamiento de Módulo (`ARC-01`)**: Declarar `@ApplicationModule(type = ApplicationModule.Type.CLOSED, displayName = "<DisplayName>")` en la raíz del módulo. Prohibido exponer paquetes internos de dominio o infraestructura hacia otros módulos. Cero comentarios o bloques Javadoc.
2. **API Pública Expuesta (`ARC-01`, `ARC-02`)**: El paquete `application` es la única interfaz pública autorizada para interactuar con otros módulos, anotada con `@NamedInterface("application")`. Cero comentarios o bloques Javadoc.
3. **Catálogo Tipado de Errores (`DOM-01`, `ERR-03`, `ERR-07`, `SHR-04`)**: Crear el enum `<Modulo>Error` implementando `ErrorCode`. **PROHIBIDO** crear subclases de excepción locales (`ERR-07`). Todos los valores siguen estrictamente la gramática `[MODULO]_[ENTIDAD]_[MOTIVO]` en `UPPER_SNAKE_CASE` en biyección 1:1 con `DOMAIN.md` / `API.md`. Cero comentarios o bloques Javadoc.
4. **Simetría Estructural de Paquetes (`OUT-02`)**: Todo paquete creado en `src/main/java` debe disponer de su contraparte simétrica en `src/test/java` para pruebas unitarias y de integración.

## 3. TOPOLOGÍA DETERMINISTA DE DIRECTORIOS (HEXAGONAL + MODULITH)

```text
src/main/java/<base>/<module_id>/
├── application/{command, query, port/in, port/out, service, result, mapper}
├── domain/{model/enums, event}
└── infrastructure/adapter/
    ├── in/{web/dto, worker/dto}
    └── out/{persistence/postgres/jpa/{adapter,entity,mapper,repository}, persistence/postgres/jdbc/{adapter,mapper,query,repository}, messaging, client}

src/test/java/<base>/<module_id>/
├── application/{command, service}
├── domain/
└── infrastructure/adapter/
    ├── in/{web/dto, worker/dto}
    └── out/persistence/postgres/{jpa/{adapter,mapper}, jdbc/{adapter,mapper,query,repository}}
```

## 4. PLANTILLAS CANÓNICAS DE CÓDIGO

### 4.1. `src/main/java/<base>/<module_id>/package-info.java`

* Declara el módulo cerrado con el `DisplayName` de `03-SUBDOMAINS.md` (`ARC-01`).

```java
@ApplicationModule(
        type = ApplicationModule.Type.CLOSED,
        displayName = "<DisplayName>"
)
package <base>.<module_id>;

import org.springframework.modulith.ApplicationModule;
```

### 4.2. `src/main/java/<base>/<module_id>/application/package-info.java`

* Única API pública expuesta a otros módulos (`@NamedInterface("application")`).

```java
@NamedInterface("application")
package <base>.<module_id>.application;

import org.springframework.modulith.NamedInterface;
```

### 4.3. `src/main/java/<base>/<module_id>/domain/<Modulo>Error.java`

* Implementa `<base>.shared.domain.error.ErrorCode` (`ERR-03`, `SHR-04`) con códigos en `UPPER_SNAKE_CASE`.

```java
package <base>.<module_id>.domain;

import <base>.shared.domain.error.ErrorCode;

public enum <Modulo>Error implements ErrorCode {

    <MODULO>_<ENTIDAD>_NOT_FOUND,
    <MODULO>_<ENTIDAD>_INVALID_STATUS,
    <MODULO>_<ENTIDAD>_ALREADY_EXISTS;

    @Override
    public String code() {
        return name();
    }
}
```

## 5. VERIFICACIÓN OBLIGATORIA (GATE)

Ejecutar en terminal antes de finalizar:
1. `./gradlew compileJava compileTestJava`
2. `./gradlew test --tests "*ModulithStructureTest*" --tests "*DomainRulesArchTest*"`
3. Emitir informe final conciso:
    * **Generados**: Árbol de directorios (`main` y `test`), `package-info.java` (raíz y `application`) y catálogo `<Modulo>Error` con el listado de constantes.
    * **Intactos (preexistentes)**.

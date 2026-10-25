# FProg Standards — Manual de Arquitectura: DDD Hexagonal Modular con Spring Modulith (`ARCHITECTURE`)

| Propiedad | Valor Normativo |
| :--- | :--- |
| **Sistema / Preset (`preset_id`)** | `FProg STANDARDS` · `java-spring-modulith` (Java 21+ LTS / Spring Boot 3 / PostgreSQL 17 / Spring Modulith) |
| **Documento / Versión / Estado** | `ARCHITECTURE.md` · `v1.0.0` · `NORMATIVO` |
| **Repositorio Oficial** | `erft-fprog-preset-java-spring-modulith` (`standards/` + `seed/`) |
| **Ubicación Catálogo / Consumidor** | `presets/java-spring-modulith/standards/ARCHITECTURE.md` ──► `.standards/ARCHITECTURE.md` (vía `./scripts/sync-standards.sh`) |
| **Alcance y Entradas (Fase 1 — Rails)** | Servicios `CORE_TRANSACTIONAL` especificados en `docs/01-OPPORTUNITY.md`, `docs/02-TECH_STACK.md`, `docs/03-SUBDOMAINS.md`, `docs/modules/<m>/{schema.sql,DOMAIN.md,API.md}` y `docs/04-INTEGRATION.md` |
| **Quality Gate Asociado** | `Gate 4 (Production Ready — ./gradlew check en verde en < 5 min)` |

---

> 🎯 **Reglas del Juego (FProg STANDARDS — Arquitectura Hexagonal y Modular):**  
> Este manual establece las convenciones inmutables de ingeniería para el Preset `java-spring-modulith`. Su propósito es permitir que múltiples desarrolladores y agentes construyan módulos en paralelo con radio de impacto cero (**Blast Radius = 0**).
>
> Para proteger la estabilidad, modularidad y rendimiento del sistema, en este estándar **queda terminantemente prohibido**:
> 1. **Acoplamiento directo inter-módulo:** Nada de inyectar repositorios de otro módulo, importar entidades JPA ajenas o ejecutar consultas SQL cruzadas entre esquemas. Toda comunicación entre módulos es síncrona vía API pública (`application`) o asíncrona vía Outbox (`EVT-xx`).
> 2. **Contaminar el Dominio con tecnología:** Nada de anotaciones de frameworks (Spring, Jackson, Hibernate, Lombok) en el paquete `domain`. El dominio es Java 21 puro.
> 3. **Modificar el Kernel Compartido (`shared/`):** El módulo `shared/` es inmutable durante la construcción de módulos funcionales. Se consume tal como viene provisto por la semilla (`seed/`).
> 4. **Improvisar librerías o dependencias:** Queda prohibido añadir dependencias al `build.gradle` fuera de las homologadas en `docs/02-TECH_STACK.md`.
> 5. **Incumplir reglas `MUST` o violar reglas `NEVER`:** Toda regla marcada como `[A]` es vigilada por guardianes automatizados de `ArchUnit` y `Spring Modulith`. Si una regla falla, el build se rompe.

---

## 1. Notación y Taxonomía de Reglas

* **Estructura de Identificador:** **`ÁMB-nn · FUERZA [TIPO]`**
    * **`MUST`**: Obligación técnica estricta cuyo incumplimiento rompe la compilación o las pruebas.
    * **`NEVER`**: Prohibición técnica absoluta. Solo admite excepción mediante un `ADR` formal aprobado.
    * **`SHOULD`**: Recomendación técnica prioritaria sujeta a verificación de diseño.
    * **`[A]`**: Verificación automatizada obligatoria en el pipeline de CI (Spring Modulith / ArchUnit / linters de análisis estático estructural).
    * **`[R]`**: Verificación manual obligatoria en Pull Request mediante checklist técnico.

| Acrónimo | Ámbito Arquitectónico | Artefacto de Fase 1 (Rails) Vinculado | Responsabilidad Normativa |
| :---: | :--- | :--- | :--- |
| **`ARC`** | Arquitectura y Fronteras Modulares | `docs/03-SUBDOMAINS.md` & `docs/04-INTEGRATION.md` | Principios macro, fronteras modulares, visibilidad pública y verificación estructural con Spring Modulith. |
| **`DOM`** | Dominio Táctico | `docs/modules/<module_id>/DOMAIN.md` | Modelo de Dominio puro, entidades, agregados, invariantes, enums FSM y Value Objects. |
| **`APP`** | Capa de Aplicación | `docs/modules/<module_id>/API.md` | Casos de uso (`*UseCase`), orquestación transaccional (`*Service`), separación CQRS y contratos. |
| **`INP`** | Adaptadores de Entrada | `docs/modules/<module_id>/API.md` & `docs/04-INTEGRATION.md` | Adaptadores primarios / *driving* (Controladores REST, Workers asíncronos, Schedulers). |
| **`OUT`** | Adaptadores de Salida y DDL | `docs/modules/<module_id>/schema.sql` | Adaptadores secundarios / *driven* (Persistencia Híbrida JPA/JDBC, Flyway, RLS, Audit Log). |
| **`TRX`** | Transacciones, Consistencia y Outbox | `docs/modules/<module_id>/schema.sql` & `docs/04-INTEGRATION.md` | Delimitación transaccional, concurrencia optimista, Transactional Outbox e Idempotencia. |
| **`SHR`** | Kernel Compartido Transversal | `docs/02-TECH_STACK.md` (`seed/`) | Contexto inmutable (`ExecutionContext`), identificadores UUIDv7, `Money` y catálogo base de errores (`ERR-xx` en `EXCEPTIONS.md`). |

---

## 2. Topología de Directorios, Fronteras Modulares y Contratos de `shared/`

### 2.1. Estructura Canónica del Proyecto

El sistema se organiza en módulos de negocio independientes (definidos como `module_id` en `docs/03-SUBDOMAINS.md`) gobernados por Spring Modulith bajo el paquete base `<namespace.base>` (`base_package` en `docs/02-TECH_STACK.md`). La infraestructura transversal técnica reside en el módulo abierto `shared/` (`@ApplicationModule(type = OPEN)`), provisto de forma inmutable por la semilla (`seed/`) en el `Gate 2`.

```text
src/main/java/<namespace.base>/
├── ApiApplication.java                     # Bootstrap Spring Boot / Spring Modulith
│
├── shared/                                 # Kernel compartido transversal (@ApplicationModule OPEN — Inmutable en Fase 2)
│   ├── package-info.java                   # Declaración @ApplicationModule(type = OPEN, displayName = "Shared")
│   ├── domain/                             # Tipos base genéricos (AggregateRoot, BaseEntity, ValueObject, DomainEvent)
│   │   ├── model/                          # AggregateRoot<ID>, BaseEntity<ID>
│   │   ├── valueobject/                    # ValueObject (interfaz marcadora), Money (VO monetario universal ISO-4217)
│   │   ├── event/                          # DomainEvent (contrato inmutable lean de eventos con aggregateId y tenantId)
│   │   ├── error/                          # CommonError, ErrorCategory, ErrorCode, FieldViolation
│   │   └── exception/                      # Jerarquía base de excepciones sin traza (BaseException, ConflictException, etc.)
│   ├── application/                        # Contexto transversal, paginación (PageResult/CursorResult) y puertos universales
│   │   ├── context/                        # ExecutionContext (record con UserType, multitenancy y roles con ámbito de tenant)
│   │   ├── result/                         # Wrappers transversales: PageResult (offset) y CursorResult (keyset)
│   │   └── port/out/                       # Puertos secundarios universales (EventPublisherPort, ExecutionContextPort,
│   │                                       # OutboxPublisherPort, IdempotencyGate, UtcClockPort, UuidGeneratorPort)
│   └── infrastructure/                     # Adaptadores transversales técnicos
│       └── adapter/
│           ├── in/web/                     # ApiHeaders, context/ExecutionContextFilter, exception/{ErrorResponse, GlobalExceptionHandler}
│           └── out/                        # UtcClockAdapter, ExecutionContextHolder, ExecutionContextAdapter,
│                                           # SpringEventPublisherAdapter, OutboxRelayService,
│                                           # JdbcOutboxPublisherAdapter, JdbcIdempotencyGate,
│                                           # TenantRlsConnectionCustomizer, UuidGeneratorAdapter
│
└── <module_id>/                            # Módulo / Bounded Context de Negocio (module_id de docs/03-SUBDOMAINS.md)
    ├── package-info.java                   # Declaración @ApplicationModule(type = CLOSED, displayName = "<DisplayName>") (cero Javadoc)
    ├── application/                        # API PÚBLICA DEL MÓDULO (@NamedInterface("application"))
    │   ├── package-info.java               # Declara la interfaz nombrada pública expuesta a otros módulos (cero Javadoc)
    │   ├── command/                        # Comandos inmutables de mutación (records WEB_EXCLUSIVE o MULTICHANNEL)
    │   ├── query/                          # Modelos de consulta inmutables (parámetros y filtros)
    │   ├── port/
    │   │   ├── in/                         # Casos de uso / interfaces primarias (*UseCase)
    │   │   └── out/                        # Puertos secundarios (*RepositoryPort para JPA y *QueryPort para JDBC)
    │   ├── service/                        # Implementaciones orquestadoras transaccionales (*Service con @RequiredArgsConstructor)
    │   ├── result/                         # DTOs inmutables de salida de la aplicación (*Result)
    │   └── mapper/                         # Mappers de aplicación (si aplican transformaciones complejas)
    ├── domain/                             # DETALLE PRIVADO INTERNO (100% puro, agnóstico a frameworks y sin Lombok)
    │   ├── model/                          # Agregados (AggregateRoot), Entidades hijas y Value Objects locales
    │   │   └── enums/                      # Enums FSM de estado de negocio (<AggregateName>Status)
    │   ├── event/                          # Eventos de dominio generados ante mutaciones (*Event records planos)
    │   └── <Subdominio>Error.java          # Catálogo tipado de errores de negocio (implements ErrorCode)
    └── infrastructure/                     # DETALLE PRIVADO INTERNO (Tecnología, transporte y persistencia)
        └── adapter/
            ├── in/                         # Adaptadores Primarios (Driving)
            │   ├── web/                    # Controladores REST (*Controller con @RequiredArgsConstructor y @ResponseStatus)
            │   │   └── dto/                # DTOs de entrada HTTP (*HttpRequest con método factoría toCommand())
            │   └── worker/                 # Consumidores de eventos (*Worker) con IdempotencyGate y release defensivo
            │       └── dto/                # Records de deserialización de eventos (*EventMessage con toCommand())
            └── out/                        # Adaptadores Secundarios (Driven)
                ├── persistence/            # Persistencia desacoplada jerárquica por motor de base de datos
                │   └── postgres/           # Implementación PostgreSQL 17 (esquema <module_id>)
                │       ├── jpa/            # Escritura / Transacciones ACID mediante JPA y Hibernate
                │       │   ├── adapter/    # *PersistenceAdapter (implements *RepositoryPort)
                │       │   ├── entity/     # *Entity (@Entity relacional con schema = "<module_id>" y Lombok)
                │       │   ├── mapper/     # *PersistenceMapper (mapeador explícito bidireccional)
                │       │   └── repository/ # *JpaRepository (extends JpaRepository<*Entity, UUID>)
                │       └── jdbc/           # Lectura de Alto Rendimiento (CQRS Ligero)
                │           ├── adapter/    # *JdbcQueryAdapter (implements *QueryPort)
                │           ├── mapper/     # *RowMapper (implements RowMapper<*Result>)
                │           ├── query/      # *JdbcQueries (Text Blocks SQL centralizados sobre <module_id>.*)
                │           └── repository/ # *JdbcRepository (NamedParameterJdbcTemplate)
                ├── messaging/              # Publicadores específicos a brokers externos (si aplica)
                └── client/                 # Clientes HTTP downstream (RestClient)
```

### 2.2. Contrato de Visibilidad Inter-Módulo

1. **API Pública del Módulo (`application`):** Único punto de contacto mediante el cual otros módulos interactúan síncronamente con este Bounded Context (contratos `SYNC-xx` en `docs/04-INTEGRATION.md`). Se declara mediante `@NamedInterface("application")` en su `package-info.java` y expone exclusivamente interfaces de casos de uso (`port/in`), Queries (o Commands si el contrato `SYNC-xx` lo requiere) y DTOs de salida (`*Result`).
2. **Confinamiento Privado (`domain` e `infrastructure`):** Queda estrictamente prohibido que un módulo acceda directamente al modelo de dominio o a las clases de persistencia/adaptadores de otro módulo. La colaboración intermodular se efectúa mediante contratos en `application` (`SYNC-xx`) o suscripciones asíncronas a eventos de dominio vía Outbox (`EVT-xx` en `docs/04-INTEGRATION.md`).
3. **Estandarización de `package-info.java` (`ARC-01`):**
    * Todo paquete raíz de subdominio (`<namespace.base>.<module_id>`) debe contar con un `package-info.java` que declare `@ApplicationModule(type = ApplicationModule.Type.CLOSED, displayName = "<DisplayName>")`, con cero comentarios o bloques Javadoc.
    * El paquete `<namespace.base>.<module_id>.application` de cada subdominio debe declarar explícitamente `@NamedInterface("application")` en su respectivo `package-info.java`, con cero comentarios o bloques Javadoc.
    * El módulo transversal técnico `shared` declara `@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "Shared")`.

### 2.3. Contratos Públicos del Kernel Compartido (`shared/`)

Dado que los desarrolladores y subagentes operan bajo **Poda Quirúrgica de Contexto (*Strict Context Pruning*)** y tienen prohibido modificar `shared/` (`Blast Radius = 0`), consumen exactamente estas firmas canónicas provistas por la semilla:

| Paquete en `<namespace.base>.shared` | Tipo / Interfaz | Firmas Públicas Canónicas Disponibles | Ejemplo Rápido de Uso |
| :--- | :--- | :--- | :--- |
| `domain.model` | `abstract class AggregateRoot<ID> extends BaseEntity<ID>` | `protected AggregateRoot(ID id, Long version)`<br>`public Long getVersion()`<br>`protected void registerEvent(DomainEvent event)`<br>`public boolean hasDomainEvents()`<br>`public List<DomainEvent> pullDomainEvents()` | `public class Order extends AggregateRoot<UUID> { ... registerEvent(new OrderConfirmedEvent(getId())); }` |
| `domain.model` | `abstract class BaseEntity<ID>` | `protected BaseEntity(ID id)`<br>`public ID getId()` *(igualdad `equals`/`hashCode` basada en `id`)* | Identidad garantizada por `id`. |
| `domain.valueobject` | `interface ValueObject` | Interfaz marcadora DDD para Value Objects implementados como `record`. | `public record Address(...) implements ValueObject {}` |
| `domain.valueobject` | `record Money(BigDecimal amount, Currency currency) implements ValueObject, Comparable<Money>` | `static Money of(BigDecimal amount, Currency currency)`<br>`static Money of(BigDecimal amount, String currencyCode)`<br>`static Money zero(Currency currency)`<br>`Money plus(Money other)`, `Money minus(Money other)`, `Money multiply(BigDecimal factor)`<br>`boolean isZero()`, `boolean isPositive()`, `boolean isGreaterThan(Money other)` | `Money total = itemPrice.multiply(quantity);` |
| `domain.event` | `interface DomainEvent` | `String aggregateId()`<br>`default Instant occurredAt() { return Instant.now(); }`<br>`default String eventType() { return this.getClass().getName(); }` | `public record OrderShippedEvent(String aggregateId) implements DomainEvent {}` |
| `application.context` | `record ExecutionContext(...)` | `ExecutionContext(UserType userType, UUID tenantId, UUID userId, Set<String> roles, String correlationId)`<br>`static ExecutionContext anonymous()`<br>`boolean isAuthenticated()`, `boolean hasTenant()`<br>`UUID requireUserId()`, `UUID requireTenantId()`<br>`boolean hasRole(String role)`, `void requireTenantRole(String role)` | `UUID tenantId = context.requireTenantId();` |
| `application.context` | `enum UserType` | `ANONYMOUS`, `CLIENT`, `TENANT_USER`, `SAAS_ADMIN` | Clasificación del emisor de la petición. |
| `application.result` | `record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages)` | `static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements)`<br>`<R> PageResult<R> map(Function<T, R> mapper)` | Retorno paginado clásico por offset. |
| `application.result` | `record CursorResult<T>(List<T> items, String nextCursor, boolean hasNext)` | `static <T> CursorResult<T> of(List<T> items, String nextCursor)` | Retorno optimizado por keyset (cursor). |
| `application.port.out` | `interface UuidGeneratorPort` | `UUID generateId()` *(genera UUIDv7 secuencial conforme a RFC 9562 con CAS lock-free)* | `UUID orderId = uuidGenerator.generateId();` |
| `application.port.out` | `interface UtcClockPort` | `Instant now()`, `LocalDate todayUtc()` | Control temporal determinista y testeable. |
| `application.port.out` | `interface ExecutionContextPort` | `ExecutionContext current()` | Acceso seguro al contexto de sesión actual. |
| `application.port.out` | `interface OutboxPublisherPort` | `void publish(DomainEvent event)`<br>`void publishAll(List<DomainEvent> events)`<br>`void publish(String moduleSchema, String aggregateType, UUID aggregateId, DomainEvent event)` | Guarda los eventos en `<moduleSchema>.outbox_events`. |
| `application.port.out` | `interface IdempotencyGate` | `boolean tryAcquire(String moduleSchema, UUID eventId, String consumerName)`<br>`void release(String moduleSchema, UUID eventId, String consumerName)` | Deduplica eventos en `<moduleSchema>.processed_events`. |
| `infrastructure.adapter.in.web` | `final class ApiHeaders` | `TENANT_ID = "X-Tenant-Id"`, `USER_ID = "X-User-Id"`, `ROLES = "X-Roles"`, `CORRELATION_ID = "X-Correlation-Id"` | Cabeceras HTTP estándar del ecosistema. |

---

## 3. Matriz de Dependencias y Purificación de Contexto

### 3.1. Regla Direccional y Visibilidad entre Capas

Las dependencias fluyen estrictamente desde el exterior hacia el centro: **`infrastructure (Adaptadores)` ──► `application (Aplicación)` ──► `domain (Dominio 100% Java 21 Puro)`**.

| Capa / Componente | Puede depender de | Prohibido depender de |
| :--- | :--- | :--- |
| **Dominio** (`domain`) | Tipos base de `shared/domain`, tipos nativos de Java (`java.*`). | Frameworks de transporte (HTTP/Web), ORMs/JPA, serializadores externos (Jackson), Aplicación, Adaptadores, Lombok (`DOM-01`). |
| **Aplicación** (`application`) | `domain` local, `shared/domain`, `shared/application`, contratos de `application` expuestos por otros módulos (`SYNC-xx`). Uso autorizado de Lombok (`@RequiredArgsConstructor`). | Adaptadores locales (`infrastructure/adapter/*`), bases de datos, tecnologías de transporte (HTTP/REST), paquetes internos de otros módulos. |
| **Adaptadores Entrada** (`adapter.in`) | `application` local, `shared`, enums de estado inmutables de `domain.model.enums.*` (`ADR_005`). Uso autorizado de Lombok (`@RequiredArgsConstructor`, `@Slf4j`). | Entidades persistentes de base de datos (`@Entity`), adaptadores de salida, cualquier paquete interno de otro módulo. Prohibido MapStruct en web (`INP-01`). |
| **Adaptadores Salida** (`adapter.out`) | Puertos de `application/port/out` locales, tipos de dominio para mapeo explícito, `shared`. Uso autorizado de Lombok (`@RequiredArgsConstructor` y `@Slf4j` en adaptadores; en entidades JPA `<EntidadSingular>Entity` exclusivamente `@Getter`, `@Setter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` y `@AllArgsConstructor`). | Adaptadores de entrada, controladores REST, cualquier paquete de otro módulo. Prohibido `@Data`, `@ToString`, `@EqualsAndHashCode` y `@GeneratedValue` en entidades JPA (`OUT-01`). |
| **Kernel Compartido** (`shared`) | Tipos nativos del JDK, utilidades transversales técnicas. | Cualquier módulo o subdominio de negocio específico (`<namespace.base>.<module_id>.*`). |

### 3.2. Purificación de Contexto y Enlace de Sesión RLS

El contexto de seguridad y transporte (`ExecutionContext`: token, cabeceras, identidad y roles) **jamás penetra en el modelo de dominio**:

1. **Adaptadores de Entrada (`adapter.in`):**
    * **En HTTP (`web`):** `ExecutionContextFilter` intercepta y extrae las cabeceras perimetrales (`X-Tenant-Id`, `X-User-Id`, `X-Roles`, `X-Correlation-Id`), reconstruye el `ExecutionContext` inmutable, lo deposita en `ExecutionContextHolder` y puebla el MDC de logging asegurando su limpieza en el bloque `finally`.
    * **En Workers asíncronos (`worker`):** Antes de invocar la compuerta de idempotencia o el caso de uso, el Worker extrae `tenantId` y `correlationId` del mensaje y ejecuta dentro de `ExecutionContextHolder.runWith(context, () -> ...)` garantizando la limpieza en `finally` (`INP-04`).
2. **Capa de Aplicación (`application`):** Valida la autorización invocando métodos defensivos sobre el puerto de contexto (`context.requireUserId()`, `context.requireTenantId()`, `context.requireTenantRole(role)`), extrae los identificadores planos requeridos (`UUID`) y resuelve la generación de nuevos IDs (`UuidGeneratorPort.generateId()`) o timestamps (`UtcClockPort.now()`).
3. **Enlace de Sesión PostgreSQL RLS (Transversal JPA + JDBC):** Al abrir una transacción (`@Transactional`) con un `tenantId` activo, la infraestructura ejecuta `SELECT set_config('app.current_tenant_id', '<tenantId>', true);`. Esto garantiza que las políticas `FORCE ROW LEVEL SECURITY` se apliquen de forma idéntica tanto en escrituras JPA como en consultas JDBC con `NamedParameterJdbcTemplate`, siendo 100% seguro con PgBouncer en *Transaction Mode*.
4. **Capa de Dominio (`domain`):** Recibe exclusivamente tipos primitivos, identificadores planos (`UUID`), `Instant` o Value Objects. El dominio desconoce por completo la existencia de tokens de transporte, cabeceras HTTP, sesiones o variables de sesión SQL.

---

## 4. Los 3 Flujos Canónicos de Operación

### 4.1. Camino de Escritura Síncrono (Command Flow — Mutación de Estado)
Obliga a hidratar el Agregado de Dominio para validar invariantes y ejecutar lógica de negocio protegida (`CMD-xx` con canal `REST` en `API.md`):
1. **`[Adapter IN (Controller)]`**: Valida sintaxis de la petición (`@Valid` en DTO `*HttpRequest` con tokens `[ENTIDAD]_[CAMPO]_[REGLA]`). Mapea payload a Command inmutable vía factoría directa en record: `request.toCommand(...)`.
2. **`[Application (Service)]`**: Abre delimitación transaccional (`@Transactional`) y enlaza `app.current_tenant_id` (RLS). Verifica contexto (`requireTenantId`), resuelve `UuidGeneratorPort` / `UtcClockPort` si aplica, e hidrata o crea el Agregado invocando puerto de salida.
3. **`[Domain (AggregateRoot)]`**: Ejecuta método de negocio semántico guiado por predicados FSM (`canConfirm()`, etc.). Valida invariantes (*fail-fast* `Objects.requireNonNull` / `BaseException`) y registra `DomainEvent`.
4. **`[Adapter OUT (Persistence)]`**: Persiste el nuevo estado del Agregado en `<module_id>.<tabla>` con versión optimista vía JPA. Extrae `pullDomainEvents()` y publica vía `OutboxPublisherPort` en `<module_id>.outbox_events` (`UUIDv7`).
5. **`[Application (Service)]`**: Cierra transacción. Retorna DTO de salida (`*Result`) o lanza excepción tipada de coste cero (`BaseException` con `<Subdominio>Error`).
6. **`[Adapter IN (Controller)]`**: Devuelve DTO directamente con `@ResponseStatus(HttpStatus.CREATED / OK)` para códigos fijos. Si hay fallo, `GlobalExceptionHandler` mapea a `ErrorResponse` estándar.

### 4.2. Camino de Lectura Síncrono (Query Flow — CQRS Ligero)
Optimiza memoria y rendimiento proyectando directamente desde la base de datos a DTOs de salida (`QRY-xx` en `API.md`). **Queda estrictamente prohibido hidratar Agregados de dominio para operaciones de consulta**:
1. **`[Adapter IN / Port IN]`**: Mapea parámetros de búsqueda y paginación a Query plano (`*Query`).
2. **`[Application (Service)]`**: Ejecuta caso de uso bajo transacción de solo lectura (`@Transactional(readOnly = true)`). Invoca puerto de consulta optimizada en `application/port/out` (`*QueryPort`).
3. **`[Adapter OUT (Proyección)]`**: `*JdbcQueryAdapter` delega en `*JdbcRepository`. Ejecuta consulta SQL (`*JdbcQueries`) respaldada por índice de `schema.sql` y mapeada vía `*RowMapper` directamente a DTOs (`*Result`, `PageResult`, `CursorResult`) sin instanciar `@Entity` ni `AggregateRoot`.
4. **`[Adapter IN / Port IN]`**: Devuelve HTTP 200 (o record `*Result` in-memory) con el DTO o contenedor paginado.

### 4.3. Camino de Consumo Asíncrono / Background (Worker & Event Flow)
Gobierna la recepción desacoplada de eventos inter-módulo (`EVT-xx` en `04-INTEGRATION.md` y `CMD-xx` con canal `ASYNC_WORKER` en `API.md`):
1. **`[Adapter IN (Worker)]`**: Recibe payload del evento (`eventId`, `tenantId`, `correlationId`, `payload` JSONB) y deserializa a record (`*EventMessage`). Inicializa `ExecutionContext` propagando `tenantId` y `correlationId` (`ExecutionContextHolder.runWith(...)`, compatibilidad RLS `INP-04`).
2. **`[Idempotency Gate]`**: Invoca `idempotencyGate.tryAcquire(moduleSchema, eventId, consumerName)`. Si YA fue procesado (existe en `<module_id>.processed_events`), cortocircuita, emite ACK y termina. Si NO fue procesado, continúa ejecución.
3. **`[Application (Service)]`**: Mapea a Command `MULTICHANNEL` (`message.toCommand()`) con validación *fail-fast* `Objects.requireNonNull`. Invoca el `UseCase` correspondiente bajo transacción local (`@Transactional`).
4. **`[Domain / Persistencia]`**: Procesa lógica de negocio y registra mutaciones en el esquema `<module_id>`.
5. **`[Adapter IN (Worker) — Manejo Diferenciado de Fallos (EXCEPTIONS.md Sección 6.2)]`**:
    * Si ocurre excepción técnica transitoria (`capturesDiagnostics() == true`): invoca `idempotencyGate.release(moduleSchema, eventId, consumerName)` y relanza para reintento con backoff.
    * Si ocurre excepción de negocio (`capturesDiagnostics() == false`): **NO** libera la marca, registra `WARN` y desvía a DLQ / ACK sin reintentar datos inválidos.

---

## 5. Catálogo Normativo de Reglas y Guardianes de CI (`Gate 4`)

> **Integración con CI:** Las suites automatizadas de `Spring Modulith` y `ArchUnit` especificadas en `.standards/TESTING.md` (`src/test/java/.../architecture/`) verifican estas reglas en cada ejecución de `./gradlew check`.

### 5.1. Arquitectura y Fronteras Modulares (`ARC`)

* **`ARC-01 · MUST` [A] — Encapsulación y Módulos con Spring Modulith**
    * **Regla:** Cada módulo declarado en `docs/03-SUBDOMAINS.md` debe estar encapsulado como un `@ApplicationModule` independiente de Spring Modulith con interfaces nombradas formalmente.
    * **Motivación:** Evita que el monolito degenere en una bola de fango (*Big Ball of Mud*) y permite compilar y testear cada subdominio de forma autónoma.
    * **Guardarraíl Técnico:** El paquete raíz (`<namespace.base>.<module_id>`) declara `@ApplicationModule(type = ApplicationModule.Type.CLOSED, displayName = "<DisplayName>")` con cero comentarios o bloques Javadoc; `application` declara `@NamedInterface("application")` con cero comentarios o bloques Javadoc; `shared` declara `@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "Shared")`.
    * **Guardián CI (`ModulithStructureTest`):** `ApplicationModules.of(ApiApplication.class).verify()` (falla ante dependencias circulares, falta de interfaces públicas o módulos mal ubicados).
* **`ARC-02 · NEVER` [A] — Confinamiento Estricto de Paquetes Privados**
    * **Regla:** Ningún módulo accederá a paquetes privados (`domain` o `infrastructure`) de otro módulo.
    * **Motivación:** Si un módulo conoce la base de datos o las entidades internas de otro, los cambios en uno romperán al otro, impidiendo la construcción en paralelo (`Blast Radius > 0`).
    * **Guardarraíl Técnico:** Comunicación intermodular exclusivamente a través de `application` (`@NamedInterface("application")`, contratos `SYNC-xx` en `docs/04-INTEGRATION.md`) o suscripción asíncrona a eventos (`EVT-xx`).
    * **Guardián CI (`ModulithStructureTest`):** `ApplicationModules.of(ApiApplication.class).verify()` (falla si un módulo importa `domain..` o `infrastructure..` de otro módulo).
* **`ARC-03 · MUST` [A] — Topología Acíclica en Grafo Dirigido (DAG)**
    * **Regla:** No existirán dependencias cíclicas directas ni indirectas entre módulos (`A → B → A`).
    * **Motivación:** Las dependencias circulares impiden el despliegue ordenado, complican el razonamiento y rompen la compilación limpia.
    * **Guardarraíl Técnico:** El flujo de dependencias forma un Grafo Dirigido Acíclico (DAG) formalmente verificado.
    * **Guardián CI (`ModulithStructureTest`):** `ApplicationModules.of(ApiApplication.class).verify()`.

### 5.2. Dominio Táctico (`DOM`)

* **`DOM-01 · NEVER` [A] — Pureza Tecnológica Absoluta del Dominio y Convenciones de Código Limpio**
    * **Regla:** El paquete `domain` no contendrá referencias, importaciones o anotaciones de Spring, Jakarta, Jackson, Hibernate/JPA, Lombok ni ninguna librería externa.
    * **Motivación:** Las reglas de negocio deben sobrevivir a cambios de frameworks, versiones de bases de datos o librerías de serialización. El corazón del negocio no debe quedar atrapado en detalles técnicos.
    * **Guardarraíl Técnico:** El dominio es 100% Java 21 puro nativo. Aplica la convención transversal de código limpio de los skills: **cero comentarios o bloques Javadoc** en las clases Java generadas (con la única excepción de los separadores de sección internos `// --- Constructors & Factories ---` y `// --- Getters ---` dentro del modelo de dominio), **imports explícitos sin comodines (`*`)**, y orden canónico de atributos, constructores privados (`super(id, version)` en `AggregateRoot<UUID>` vs. `super(id)` en `BaseEntity<UUID>`), factorías (`create`, `reconstruct`) y getters en el mismo orden de declaración.
    * **Guardián CI (`DomainRulesArchTest`):** `domain_should_not_depend_on_frameworks` (falla si clases en `..domain..` importan frameworks web, ORM, Jackson o Lombok).
* **`DOM-02 · MUST` [A] — Instanciación Controlada y Precondiciones Estructurales**
    * **Regla:** Las entidades y agregados de dominio no expondrán constructores públicos; la instanciación se realiza mediante métodos factoría semánticos (`create`) o factorías de reconstrucción (`reconstruct`).
    * **Motivación:** Evita que existan objetos en memoria en estados corruptos, incompletos o inválidos.
    * **Guardarraíl Técnico:** Constructores privados que validan precondiciones estructurales inmediatas mediante `Objects.requireNonNull` con tokens estructurados `[ENTIDAD]_[CAMPO]_[REGLA]` (ej. `ORDER_ID_CANNOT_BE_NULL`).
    * **Guardián CI (`DomainRulesArchTest`):** `domain_models_should_not_expose_public_constructors` (falla ante constructores públicos en `..domain.model..`).
* **`DOM-03 · MUST` [A] — Máquina de Estados Finita Semántica (FSM)**
    * **Regla:** Toda mutación de estado en un Agregado se realiza mediante métodos de negocio semánticos acompañados de predicados de consulta positiva (`canConfirm()`, `canShip()`, `canCancel()`).
    * **Motivación:** Hace que el código sea autodocumentado y garantiza que sea imposible ejecutar una transición inválida sin que el sistema falle inmediatamente de forma controlada.
    * **Guardarraíl Técnico:** Correspondencia 1:1 con la matriz FSM de la Sección 2.2 de `docs/modules/<m>/DOMAIN.md`.
    * **Guardián CI:** Pruebas unitarias de dominio obligatorias (`TESTING.md`).
* **`DOM-04 · NEVER` [A] — Prohibición de Modificadores Genéricos (`Setters`)**
    * **Regla:** Ningún objeto de dominio expondrá métodos mutadores genéricos (`setters` o `@Setter`).
    * **Motivación:** Los `setters` fomentan el modelo de dominio anémico, donde cualquiera puede alterar datos sin que se disparen invariantes ni eventos de dominio.
    * **Guardarraíl Técnico:** Mutaciones siempre intencionales y contextualizadas (`order.confirm()`, nunca `order.setStatus(...)`).
    * **Guardián CI (`DomainRulesArchTest`):** `domain_models_should_not_expose_public_setters` (falla si existen métodos públicos en `..domain.model..` que comiencen por `set`).
* **`DOM-05 · MUST` [A] — Emisión de Eventos de Dominio Inmutables**
    * **Regla:** Todo Agregado (`AggregateRoot`) registrará eventos de dominio inmutables mediante `registerEvent(...)` ante cualquier mutación de estado válida.
    * **Motivación:** Permite desacoplar efectos secundarios (facturación, emails, analítica) sin complicar la transacción del agregado que mutó.
    * **Guardarraíl Técnico:** Records planos que implementan `DomainEvent` con `String aggregateId()` y los datos de negocio mutados, manteniéndose limpios de metadatos de transporte perimetrales (`eventId` se genera en el adaptador Outbox).
    * **Guardián CI:** Pruebas unitarias de agregados verificando `pullDomainEvents()`.

### 5.3. Capa de Aplicación (`APP`)

* **`APP-01 · MUST` [A] — Servicios de Caso de Uso con Responsabilidad Única**
    * **Regla:** Cada servicio de aplicación residirá en `application/service`, implementará exactamente un caso de uso (`*UseCase`) de `application/port/in`, y su nombre terminará estrictamente con el sufijo `Service`.
    * **Motivación:** Evita los "servicios dios" (*God Services*) de miles de líneas que acumulan decenas de responsabilidades no relacionadas.
    * **Guardarraíl Técnico:** Clase anotada con `@Service` y `@RequiredArgsConstructor`, implementando exactamente una interfaz de `port/in`.
    * **Guardián CI (`ApplicationRulesArchTest`):** `application_services_should_implement_one_use_case`.
* **`APP-02 · NEVER` [A/R] — Cero Lógica de Cálculo en Servicios de Aplicación**
    * **Regla:** La capa de aplicación no contendrá lógica de cálculo de negocio ni evaluación de invariantes del agregado.
    * **Motivación:** El servicio solo debe orquestar (abrir transacción, buscar en repositorio, llamar al agregado, guardar y retornar). Si el cálculo se queda en el servicio, el dominio queda anémico.
    * **Guardarraíl Técnico:** Toda regla de cálculo o transición se delega al `AggregateRoot`.
    * **Guardián CI:** Revisión en PR `[R]` y pruebas unitarias de casos de uso.
* **`APP-03 · MUST` [R] — Tipado Estricto de Entradas (Comandos y Queries)**
    * **Regla:** La entrada a un caso de uso debe ser un Comando o Query inmutable (declarado en las Secciones 1, 2 y 3 de `API.md`).
    * **Motivación:** Garantiza que los parámetros de ejecución sean inmutables y seguros ante concurrencia.
    * **Guardarraíl Técnico:**
        * **Comandos Web (`WEB_EXCLUSIVE`):** Records planos sin validación interna redundante, delegando en Bean Validation (`@Valid`) del adaptador web (`ADR_006`), respetando el orden estricto de componentes (`1. Parámetros de ruta` -> `2. Cabeceras de operación` -> `3. Campos IN (Request)`) y permitiendo el uso directo de enums de `<module_id>.domain.model.enums.*` (`ADR_005`).
        * **Comandos Multicanal (`MULTICHANNEL`):** Comandos invocables desde Workers asíncronos o puertos internos; implementan comprobaciones defensivas inmediatas (*fail-fast*) de no-nulidad mediante `Objects.requireNonNull` en su constructor compacto con tokens estructurados (`[ENTIDAD]_[CAMPO]_[REGLA]`), y tienen **prohibido exponer enums del paquete privado `<module_id>.domain.model.enums.*`** si cruzan fronteras modulares (`ARC-02`, usando `String` o enums de `shared`).
        * **Queries (`*Query`):** Records planos de parámetros sin lógica interna, respetando el orden estricto de componentes (`1. Variables de ruta` -> `2. Cabeceras` -> `3. Parámetros de consulta`), el uso de tipos primitivos (`int`, `long`, `boolean`) cuando existe `default` y tipos envolventes (`Integer`, `Long`, `Boolean`) cuando el parámetro es opcional sin `default`. En operaciones `REST` locales se consumen directamente los enums de `<module_id>.domain.model.enums.*` (`ADR_005`), pero **queda prohibido exponer enums del paquete privado `<module_id>.domain.model.enums.*` en operaciones `INTERNAL_PORT` (`SYNC-xx`)** para no violar `ARC-02` en Spring Modulith (usando en su lugar `String`, `boolean` o enums de `shared`).
    * **Guardián CI:** Revisión en PR `[R]` y pruebas de casos de uso.
* **`APP-04 · NEVER` [A] — Prohibición de Fuga de Agregados o Entidades**
    * **Regla:** Un caso de uso jamás devolverá agregados de dominio ni entidades de base de datos (`@Entity`) hacia los adaptadores primarios o hacia otros módulos.
    * **Motivación:** Previene que la interfaz web o un módulo consumidor pueda modificar el estado interno del agregado sin pasar por sus métodos semánticos o disparar consultas perezosas (*LazyInitializationException*).
    * **Guardarraíl Técnico:** La salida es siempre un DTO plano inmutable (`*Result`, `PageResult`, `CursorResult`) o el lanzamiento de una excepción tipada (`BaseException`). En los records `*Result` prima el nombre explícito declarado en `API.md` (aplicando la matriz de fallback `<Recurso>Result` para objeto único de recurso, `<Operacion>Result` para resúmenes/estados de operación y `<Operacion>ItemResult` para elementos de colecciones paginadas `PageResult`/`CursorResult` o `List`). Al igual que en `*Query`, se autoriza el uso directo de enums de `<module_id>.domain.model.enums.*` en operaciones `REST` locales (`ADR_005`), pero **queda prohibido exponer enums del paquete privado `<module_id>.domain.model.enums.*` en operaciones `INTERNAL_PORT` (`SYNC-xx`)** para no violar `ARC-02` en Spring Modulith (usando `String`, `boolean` o enums de `shared`).
    * **Guardián CI (`ApplicationRulesArchTest`):** Verificación de tipos de retorno en interfaces `*UseCase`.

### 5.4. Adaptadores de Entrada (`INP`)

* **`INP-01 · MUST` [A] — Controladores Livianos y Mapeo por Factoría Directa**
    * **Regla:** Los controladores REST solo reciben la petición, la validan sintácticamente y delegan de inmediato al caso de uso.
    * **Motivación:** Evita código procedural en la capa web y reduce overhead de frameworks de mapeo complejos.
    * **Guardarraíl Técnico:** DTOs de entrada HTTP en `infrastructure/adapter/in/web/dto/` con sufijo `HttpRequest`; mapeo a Comando vía método factoría directo en el record (`request.toCommand(...)`), prohibiéndose MapStruct en controladores web. Se autoriza el uso directo de enums inmutables de estado de dominio (`domain.model.enums.*`) en DTOs web locales (`ADR_005`).
    * **Guardián CI (`WebRulesArchTest`):** `web_adapters_should_not_depend_on_domain_models_except_enums` (falla si `..adapter.in.web..` depende de `..domain.model..` excluyendo `..domain.model.enums..`).
* **`INP-02 · NEVER` [A] — Cero Transacciones o Lógica en Adaptadores**
    * **Regla:** Ningún controlador ni adaptador primario contendrá lógica de negocio ni gestionará transacciones de base de datos directamente (`@Transactional` está prohibido en controladores).
    * **Motivación:** Las transacciones web son peligrosas y alargan el tiempo de retención de conexiones JDBC.
    * **Guardarraíl Técnico:** La demarcación transaccional pertenece exclusivamente a `application/service`.
    * **Guardián CI (`WebRulesArchTest`):** `web_adapters_should_not_be_transactional` (falla si métodos o clases en `..adapter.in.web..` están anotados con `@Transactional`).
* **`INP-03 · MUST` [A] — Serialización Estándar y Enmascaramiento de Errores**
    * **Regla:** Todas las excepciones de la API deben traducirse de forma centralizada mediante `GlobalExceptionHandler` al contrato estandarizado `ErrorResponse` (`status`, `code`, `detail`, `errors`).
    * **Motivación:** Protege la seguridad del sistema ocultando detalles técnicos (tablas, IPs, trazas) a clientes externos y provee códigos semánticos unificados para frontend.
    * **Guardarraíl Técnico:** Omisión de campos vacíos mediante `@JsonInclude(NON_EMPTY)`.
    * **Guardián CI:** Tests de slice web `@WebMvcTest`.
* **`INP-04 · MUST` [R] — Inicialización de Contexto en Workers Asíncronos**
    * **Regla:** Todo worker asíncrono o scheduler debe inicializar o propagar su `ExecutionContext` (incluyendo `tenantId` y `correlationId`) antes de invocar cualquier caso de uso.
    * **Motivación:** Sin `tenantId` en contexto, PostgreSQL RLS no sabrá a qué inquilino pertenecen los datos y bloqueará el acceso a las tablas protegidas.
    * **Guardarraíl Técnico:** Ejecutar dentro de `ExecutionContextHolder.runWith(context, () -> ...)`.
    * **Guardián CI:** Pruebas de workers con Testcontainers (`TESTING.md`).
* **`INP-05 · SHOULD` [R] — Retorno Directo de DTOs con `@ResponseStatus`**
    * **Regla:** Para endpoints con código HTTP fijo (ej. `200 OK`, `201 CREATED`) y sin cabeceras dinámicas, los métodos del controlador deben retornar directamente el DTO de salida anotado con `@ResponseStatus`.
    * **Motivación:** Elimina la instanciación innecesaria de objetos `ResponseEntity<T>` en el Heap en rutas de alto tráfico.
    * **Guardarraíl Técnico:** Uso de `@ResponseStatus(HttpStatus.CREATED)` o `@ResponseStatus(HttpStatus.OK)`.
    * **Guardián CI:** Revisión en PR `[R]`.

### 5.5. Adaptadores de Salida y Persistencia (`OUT`)

* **`OUT-01 · NEVER` [A] — Confinamiento Estricto de Entidades JPA y Lombok Seguro**
    * **Regla:** Las entidades anotadas con el ORM (`@Entity`) jamás saldrán del adaptador de persistencia JPA hacia la aplicación o el dominio.
    * **Motivación:** Evita que el modelo de persistencia relacional dicte el modelo de negocio y previene fallos de serialización o sesiones JPA cerradas (*LazyInitializationException*).
    * **Guardarraíl Técnico:** Cada `AggregateRoot<ID>` de `domain/model/` dispone exactamente de un puerto secundario `<Agregado>RepositoryPort` en `application/port/out/` con los dos métodos canónicos `<Agregado> save(<Agregado> agregado)` y `Optional<<Agregado>> findById(<ID> id)`. Las clases `<EntidadSingular>Entity` residen en `..persistence.postgres.jpa.entity..` con `@Table(name = "<tabla>", schema = "<module_id>")`, anotadas exclusivamente con `@Getter`, `@Setter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` y `@AllArgsConstructor` (**prohibido** `@Data`, `@ToString`, `@EqualsAndHashCode` y `@GeneratedValue`), excluyendo tablas técnicas (`audit_log*`, `outbox_events`, `processed_events`), manteniendo el orden canónico de atributos (`1. id` -> `2. tenantId` -> `3. FKs escalares` -> `4. Campos de negocio` -> `5. version` -> `6. Timestamps`) y mapeo explícito bidireccional entre `AggregateRoot` y `@Entity` dentro de `postgres/jpa/mapper/`.
    * **Guardián CI (`PersistenceRulesArchTest`):** `entities_should_reside_in_jpa_persistence` (falla si clases `@Entity` residen fuera de `..persistence..jpa..`).
* **`OUT-02 · MUST` [A] — Persistencia Segregada Híbrida (JPA / JDBC) y Simetría de Paquetes**
    * **Regla:** La persistencia se organizará jerárquicamente por motor de base de datos (`postgres/jpa/`, `postgres/jdbc/`) con responsabilidades simétricas y segregadas, y todo paquete creado en `src/main/java` dispondrá de su contraparte simétrica en `src/test/java`.
    * **Motivación:** Permite usar la herramienta adecuada para cada tarea: JPA para transacciones ACID complejas con control de versiones optimista; JDBC directo para lecturas de alto rendimiento.
    * **Guardarraíl Técnico:**
        * **JPA (`postgres/jpa/`):** `adapter/` (`*PersistenceAdapter`), `entity/` (`*Entity`), `mapper/` (`*PersistenceMapper`), `repository/` (`*JpaRepository`).
        * **JDBC (`postgres/jdbc/`):** `adapter/` (`*JdbcQueryAdapter`), `mapper/` (`*RowMapper`), `query/` (`*JdbcQueries`), `repository/` (`*JdbcRepository`).
        * **Simetría de Test:** Réplica 1:1 de la jerarquía de paquetes en `src/test/java/<namespace.base>/<module_id>/...`.
    * **Guardián CI (`PersistenceRulesArchTest`):** `entities_should_reside_in_jpa_persistence` y `jdbc_template_should_reside_in_jdbc_persistence`.
* **`OUT-03 · NEVER` [R] — Cero Lazy Loading Fuera de Persistencia**
    * **Regla:** Queda prohibida la carga perezosa (*lazy loading*) fuera del adaptador de persistencia.
    * **Motivación:** Genera problemas de N+1 consultas o excepciones en runtime cuando la sesión ya no está abierta.
    * **Guardarraíl Técnico:** Los agregados se cargan completos y consistentes en una sola operación controlada.
    * **Guardián CI:** Revisión en PR `[R]`.
* **`OUT-04 · MUST` [R] — Proyecciones Directas CQRS en Lecturas**
    * **Regla:** Las consultas de lectura y listados (`QRY-xx` en `API.md`) **no deben hidratar Agregados de dominio**.
    * **Motivación:** Hidratar grafos completos de objetos en memoria para solo mostrar una tabla o un informe en pantalla satura la memoria y degrada la latencia drásticamente.
    * **Guardarraíl Técnico:** El puerto secundario `<Recurso>QueryPort` en `application/port/out/` recibe parámetros escalares desglosados (**nunca** el record `*Query` completo) en el mismo orden, nombres y tipos de `<Operacion>Query`, y retorna `Optional<*Result>` (método `find<Result>By<Sufijo>`), `CursorResult<*Result>` o `PageResult<*Result>` (método `<operacionCamelCase>`). Se implementa mediante mapeo directo vía JDBC (`NamedParameterJdbcTemplate`) respaldado por índices declarados en `docs/modules/<m>/schema.sql`.
    * **Guardián CI:** Revisión en PR `[R]`.
* **`OUT-05 · MUST` [A] — Transactional Outbox Desacoplado**
    * **Regla:** El Transactional Outbox se desacopla de las entidades mediante el puerto transversal `OutboxPublisherPort` en `shared/application/port/out/` y su adaptador `JdbcOutboxPublisherAdapter`.
    * **Motivación:** Garantiza consistencia eventual sin doble escritura (*dual-write*). Si la base de datos confirma, el evento existe con certeza.
    * **Guardarraíl Técnico:** Persistencia atómica de `aggregate.pullDomainEvents()` en `<module_id>.outbox_events` con identificadores UUIDv7 ordenados temporalmente.
    * **Guardián CI (`PersistenceRulesArchTest` + Testcontainers):** Falla si clases que utilizan `NamedParameterJdbcTemplate` residen fuera de `..persistence..jdbc..` o de adaptadores transversales en `..shared.infrastructure.adapter.out..`.
* **`OUT-06 · MUST` [A] — Migraciones Flyway y Cero Claves Foráneas Cruzadas**
    * **Regla:** Cada Bounded Context posee su propio esquema relacional en PostgreSQL (`CREATE SCHEMA IF NOT EXISTS <module_id>`) y gestiona sus tablas exclusivamente mediante scripts Flyway `V<N>__init_<module_id>_tables.sql`. Queda terminantemente prohibido definir claves foráneas físicas (`FOREIGN KEY`) entre esquemas de módulos distintos.
    * **Motivación:** Las claves foráneas cruzadas unen físicamente las bases de datos de dos módulos distintos, impidiendo desplegarlos o migrarlos por separado y destruyendo la autonomía modular.
    * **Guardarraíl Técnico:** Relación inter-módulo exclusivamente por identificadores escalares (`UUID`) y consistencia eventual por eventos Outbox. `hibernate.ddl-auto=validate`.
    * **Guardián CI:** Flyway + Testcontainers en `./gradlew check`.
* **`OUT-07 · MUST` [A] — Estructura Canónica DDL, Orden de Columnas y Blindaje RLS**
    * **Regla:** Los scripts DDL de migración deben seguir la estructura canónica de secciones numeradas (con sistema de comentarios en 4 niveles a 80 columnas), el orden canónico de columnas e incluir aislamiento por fila forzado.
    * **Motivación:** Garantiza mantenibilidad uniforme del esquema y asegura que ninguna consulta, ni por descuido, pueda exponer datos de un inquilino a otro.
    * **Guardarraíl Técnico:** Orden canónico de columnas (`1. id, tenant_id` -> `2. version, status VARCHAR(32) con CHECK chk_<tabla>_status_valid` -> `3. FKs y datos de negocio con dominios shared.*` -> `4. created_at, updated_at, deleted_at TIMESTAMPTZ`); parámetros `WITH (fillfactor = 90)` en tablas de negocio; constraints prefijadas (`pk_`, `fk_`, `uq_`, `chk_`); y políticas `ENABLE ROW LEVEL SECURITY` y `FORCE ROW LEVEL SECURITY` con `USING (tenant_id = (SELECT NULLIF(current_setting('app.current_tenant_id', true), '')::uuid))`.
    * **Guardián CI:** Test Mandatorio de Fuga entre Inquilinos (`TST-08` en `TESTING.md`).
* **`OUT-08 · MUST` [A] — Bitácora Forense Inmutable Particionada**
    * **Regla:** Cada módulo aprovisionará en su esquema una bitácora forense `<module_id>.audit_log` particionada mensualmente por rangos temporales (`PARTITION BY RANGE (occurred_at)`).
    * **Motivación:** Exigencia regulatoria y forense en sistemas transaccionales B2B que asegura trazabilidad inmutable sin degradar el rendimiento de la tabla principal.
    * **Guardarraíl Técnico:** Revocación de permisos destructivos tanto con `REVOKE UPDATE, DELETE, TRUNCATE ON <module_id>.audit_log FROM PUBLIC;` como mediante el bloque idempotente `DO $$ BEGIN IF EXISTS (SELECT FROM pg_roles WHERE rolname = '<app_role>') THEN REVOKE UPDATE, DELETE, TRUNCATE ON <module_id>.audit_log FROM <app_role>; END IF; END $$;` para compatibilidad con Testcontainers.
    * **Guardián CI:** Verificación Flyway en Testcontainers.

### 5.6. Transacciones, Consistencia y Outbox (`TRX`)

* **`TRX-01 · MUST` [A] — Demarcación Transaccional en Aplicación**
    * **Regla:** La demarcación transaccional de negocio residirá exclusivamente en la capa de aplicación.
    * **Motivación:** Define con precisión el límite atómico de la operación de negocio sin mantener transacciones abiertas innecesariamente en la capa web o en adaptadores.
    * **Guardarraíl Técnico:** `@Transactional` en casos de uso de escritura (`CMD-xx`) y `@Transactional(readOnly = true)` en consultas (`QRY-xx`).
    * **Guardián CI (`ApplicationRulesArchTest`):** `transactional_should_only_be_in_application` (falla ante `@Transactional` fuera de `..application..`, salvo el relay transversal en `..shared.infrastructure.adapter.out..`).
* **`TRX-02 · MUST` [A] — Control de Concurrencia Optimista**
    * **Regla:** Los agregados de dominio deben implementar control de concurrencia optimista mediante un atributo de versión opaco (`version BIGINT NOT NULL DEFAULT 0`).
    * **Motivación:** Evita la sobreescritura ciega de datos (*lost updates*) cuando dos usuarios o procesos modifican el mismo recurso simultáneamente.
    * **Guardarraíl Técnico:** Mapeo a columna `version` y control mediante JPA/Hibernate.
    * **Guardián CI:** Pruebas de integración de persistencia con concurrencia simulada.
* **`TRX-03 · MUST` [A] — Transactional Outbox con UUIDv7**
    * **Regla:** Todo evento de dominio con visibilidad `PUBLISHED_TO_OUTBOX` se persistirá en la tabla `<module_id>.outbox_events` dentro de la misma transacción local de base de datos.
    * **Motivación:** Los identificadores UUIDv7 están ordenados en el tiempo, lo que previene la fragmentación de índices B-Tree en inserciones masivas de alta velocidad.
    * **Guardarraíl Técnico:** Asignación de `id UUID` versión 7 generado en infraestructura.
    * **Guardián CI:** Test de persistencia de eventos Outbox.
* **`TRX-04 · NEVER` [A] — Prohibición de Red dentro de Transacciones ACID**
    * **Regla:** Jamás se realizarán llamadas de red síncronas hacia brokers de mensajería (Kafka, RabbitMQ) ni APIs externas dentro de la transacción de base de datos.
    * **Motivación:** Si la red se ralentiza o se congela, la conexión a la base de datos se mantiene bloqueada, agotando el pool de conexiones y tirando todo el servidor abajo.
    * **Guardarraíl Técnico:** `OutboxRelayService` despacha en background leyendo con `FOR UPDATE SKIP LOCKED`.
    * **Guardián CI:** Revisión en PR `[R]` y guardianes ArchUnit.
* **`TRX-05 · MUST` [R] — Idempotencia Mandatoria y Liberación Defensiva**
    * **Regla:** Todo consumidor asíncrono de eventos (`*Worker`) debe implementar control de idempotencia invocando `idempotencyGate.tryAcquire(moduleSchema, eventId, consumerName)`.
    * **Motivación:** En sistemas distribuidos los mensajes pueden entregarse más de una vez (*at-least-once*). Sin idempotencia, se duplican pagos, reservas o envíos.
    * **Guardarraíl Técnico:** Si ocurre un fallo técnico transitorio (`category.capturesDiagnostics() == true`), el bloque `catch` debe invocar defensivamente `idempotencyGate.release(...)` para permitir reintentos con backoff.
    * **Guardián CI:** Test obligatorio de Idempotencia (`TST-05` en `TESTING.md`).
* **`TRX-06 · MUST` [A] — Purgado Automático de Registros Entregados**
    * **Regla:** Las tablas `outbox_events` y `processed_events` deben disponer de índices parciales y de un proceso programado de purga para eliminar registros entregados con éxito (`status = 'DELIVERED'`) que superen el periodo de retención configurado.
    * **Motivación:** Evita el crecimiento descontrolado de tablas operativas que degradaría el rendimiento de PostgreSQL.
    * **Guardarraíl Técnico:** Tarea programada o particionamiento temporal de tablas de eventos.
    * **Guardián CI:** Revisión en PR `[R]`.

### 5.7. Kernel Compartido y Preocupaciones Transversales (`SHR` y Enlace `ERR`)

* **`SHR-01 · NEVER` [A] — Cero Lógica de Negocio en `shared/`**
    * **Regla:** El módulo compartido `shared` no contendrá reglas de negocio específicas de ningún subdominio.
    * **Motivación:** `shared` es infraestructura transversal pura. Si se llena de lógica de negocio, se convierte en un monolito acoplado donde cualquier cambio rompe todos los módulos.
    * **Guardarraíl Técnico:** Confinado a contratos base, utilidades de plataforma, wrappers de paginación y excepciones genéricas.
    * **Guardián CI (`ModulithStructureTest` / `ArchUnit`):** Verificación de fronteras modulares (`shared` no depende de ningún `<module_id>`).
* **`SHR-02 · MUST` [A] — Identificadores Universales UUIDv7**
    * **Regla:** Todos los identificadores únicos globales del sistema se generarán en la capa de aplicación o infraestructura con formato UUIDv7 secuencial conforme a RFC 9562 mediante `UuidGeneratorPort`.
    * **Motivación:** Proporciona la globalidad de un UUID pero con la eficiencia de inserción contigua en disco de un autoincremental, eliminando el riesgo de enumeración de recursos y fragmentación de índices.
    * **Guardarraíl Técnico:** Prohibido usar secuencias autoincrementales (`SERIAL`/`BIGSERIAL`) o `gen_random_uuid()` (UUIDv4) como PK de agregados.
    * **Guardián CI:** ArchUnit y DDL Flyway linter.
* **`SHR-03 · MUST` [A] — Propagación Inmutable del Contexto (`ExecutionContext`)**
    * **Regla:** Toda operación debe propagar el contexto inmutable de ejecución (`ExecutionContext`: `userType`, `tenantId`, `userId`, `roles`, `correlationId`).
    * **Motivación:** Permite que cualquier capa verifique permisos y trace la actividad del usuario sin recurrir a variables globales estáticas inseguras o deserializaciones repetidas.
    * **Guardarraíl Técnico:** Soportar verificación de roles con ámbito de tenant (`hasTenantRole`, `requireTenantRole`).
    * **Guardián CI:** ArchUnit y tests de seguridad.
* **`SHR-04 · MUST` [R] — Gramática Estructurada de Errores y Precondiciones (Vinculado a `ERR-01`..`ERR-07` en `EXCEPTIONS.md`)**
    * **Regla:** Los errores deben asociarse a un código alfanumérico inmutable y tipado mediante `ErrorCode` (`code()`) en `UPPER_SNAKE_CASE`, y toda excepción declarada o lanzada en `..domain..` o `..application..` debe heredar de `BaseException` (`ERR-01`).
    * **Motivación:** Facilita la traducción en clientes frontend, evita ambigüedades en logs y permite monitoreo estructurado en observabilidad (Datadog, Prometheus).
    * **Guardarraíl Técnico:**
        * Errores de módulo: `[MODULO]_[ENTIDAD]_[MOTIVO]` (ej. `ORDERING_ORDER_NOT_FOUND`).
        * Errores de plataforma (`CommonError`): identificadores directos sin prefijo (`VALIDATION_FAILED`, `RESOURCE_NOT_FOUND`).
        * Precondiciones defensivas (`Objects.requireNonNull`) y validaciones de campo: tokens estructurados `[ENTIDAD]_[CAMPO]_[REGLA]` (ej. `ORDER_AMOUNT_REQUIRED`).
    * **Guardián CI:** ArchUnit (`ERR-01`), Linter documental Gate 3.2 y pruebas de excepciones.
* **`SHR-05 · MUST` [A] — Value Objects Universales Inmutables**
    * **Regla:** Los Value Objects universales compartidos entre múltiples bounded contexts (como `Money`) residirán en `shared.domain.valueobject`.
    * **Motivación:** Evita duplicar conceptos financieros fundamentales en cada módulo y garantiza consistencia aritmética y de redondeo en todo el sistema.
    * **Guardarraíl Técnico:** Implementar igualdad monetaria independiente de escala (`BigDecimal.compareTo` y `stripTrailingZeros()`) y aritmética inmutable segura.
    * **Guardián CI:** ArchUnit y tests unitarios de `MoneyTest`.

---

## 6. Matriz Pedagógica de Anti-Patrones Comunes

| Anti-Patrón Común | Por qué falla | Solución Normativa | Regla Asociada |
| :--- | :--- | :--- | :---: |
| **Acoplamiento Directo Inter-Módulo** | Un módulo inyecta repositorios o accede a entidades de otro módulo, destruyendo la modularidad y bloqueando la construcción paralela (`Blast Radius = 0`). | Comunicar módulos exclusivamente mediante contratos en `application` (`@NamedInterface("application")`, `SYNC-xx`) o vía eventos asíncronos Outbox (`EVT-xx`). | `ARC-01`, `ARC-02` |
| **Hidratar Agregados para Lecturas** | Degrada drásticamente la latencia y satura la memoria al instanciar grafos enteros de objetos cuando solo se querían mostrar datos en pantalla. | Proyectar consultas de lectura (`QRY-xx`) directamente desde la base de datos a DTOs de salida optimizados vía JDBC (`NamedParameterJdbcTemplate`). | `OUT-04` |
| **Doble Escritura (Dual-Write) sin Outbox** | Si la base de datos confirma el guardado pero la red falla al notificar al broker, el sistema entra en inconsistencia irrecuperable. | Transactional Outbox Pattern: guardar en `<module_id>.outbox_events` en la transacción local y despachar con `FOR UPDATE SKIP LOCKED`. | `TRX-03`, `TRX-04` |
| **Consumo Asíncrono sin Idempotencia** | La red y los brokers garantizan entrega al menos una vez (*at-least-once*); procesar duplicados corrompe saldos y estados. | `IdempotencyGate` en adaptadores worker sobre `<module_id>.processed_events` y `release` defensivo en `catch` ante fallos técnicos transitorios. | `TRX-05` |
| **Agregado Anémico con Setters** | El agregado pierde el control de sus invariantes y cualquier servicio externo corrompe el estado interno. | Mutaciones mediante métodos de negocio semánticos, predicados FSM (`canConfirm`) y validación fail-fast en constructores. | `DOM-03`, `DOM-04` |
| **Entidad de Persistencia usada como Negocio** | El ORM o motor invade el dominio, forzando constructores vacíos y acoplando la lógica de negocio a tablas SQL. | Separar Agregado de Dominio de la entidad `@Entity` mediante un mapper explícito bidireccional en `postgres/jpa/mapper`. | `DOM-01`, `OUT-01` |
| **Fuga del Contexto de Seguridad al Dominio** | Pasar objetos de sesión/token al dominio contamina las invariantes puras con detalles perimetrales de transporte. | La capa de aplicación valida `ExecutionContext` y pasa al dominio únicamente identificadores planos (`UUID`). | `SHR-03`, `DOM-01` |
| **Lógica de Negocio en el Caso de Uso** | El caso de uso se vuelve un procedimiento monolítico y el modelo de dominio queda huérfano de lógica. | Mover las reglas de cálculo, transiciones e invariantes al interior de los métodos del Agregado (`DOMAIN.md`). | `APP-02` |
| **Duplicación redundante de Enums en Web** | Crear enums idénticos en la web solo para aislar el dominio multiplica el código redundante sin aportar valor funcional. | Permitir el consumo directo de enums de estado inmutables del dominio (`domain.model.enums.*`) en controladores REST locales. | `INP-01`, `ADR_005` |
| **MapStruct en Controladores Web** | Genera beans de Spring innecesarios en Metaspace y llamadas polimórficas virtuales para mapeos triviales de request a command. | Mapear DTO a Comando mediante método factoría directo `toCommand()` en el propio record `*HttpRequest`. | `INP-01` |
| **Instanciación de ResponseEntity innecesario** | Retornar `ResponseEntity.status(...).body(...)` genera objetos efímeros en el Heap en cada request fija. | Retornar directamente el DTO de salida anotado con `@ResponseStatus(HttpStatus.CREATED)` para códigos HTTP fijos. | `INP-05` |
| **Doble validación en Comandos exclusivos Web** | Revalidar en el Command lo que ya verificó Bean Validation (`@Valid`) en el controlador web duplica código innecesariamente. | Definir comandos `WEB_EXCLUSIVE` como records planos sin validación interna. | `APP-03`, `ADR_006` |
| **Comando Multicanal sin validación fail-fast** | Permite que peticiones defectuosas desde colas o schedulers alcancen el dominio o abran transacciones. | Validación defensiva inmediata con `Objects.requireNonNull` y tokens `[ENTIDAD]_[CAMPO]_[REGLA]` en comandos `MULTICHANNEL`. | `APP-03`, `ADR_006` |
| **Metadatos de Transporte en Eventos de Dominio** | Incluir `eventId` en el record del evento de dominio contamina el modelo con detalles de transporte. | `DomainEvent` lean con `aggregateId()`; el adaptador Outbox genera el `id` UUIDv7 secuencial al persistir en `outbox_events`. | `DOM-05`, `OUT-05` |
| **Value Objects Transversales confinados en Módulos** | Confinar tipos universales como `Money` en un submódulo (`ordering`) fuerza duplicación DRY o violaciones de fronteras Modulith. | Consumir Value Objects universales desde `shared.domain.valueobject` con igualdad independiente de escala. | `SHR-05` |
| **Acoplamiento Relacional Inter-Módulo (FKs Cruzadas)** | Crear claves foráneas (FK) directas entre tablas de diferentes bounded contexts o agrupar tablas de múltiples módulos en un único DDL monolítico destruye la autonomía modular. | Asignar a cada módulo su propio esquema PostgreSQL (`CREATE SCHEMA <module_id>`), su migración `V<N>__init_<module_id>_tables.sql` y relacionar módulos por `UUID` escalar y eventos. | `OUT-06` |

---

## 7. Checklist de Pull Request y Gobernanza de Decisiones (ADRs)

### 7.1. Checklist de Verificación en Pull Request y Gate 4

Antes de aprobar la integración de código a ramas principales o cerrar el `Gate 4`, el revisor o agente auditor debe certificar los 13 puntos de control:

- [ ] **Aislamiento Inter-Módulo (`ARC-02`, `ARC-03`):** ¿Las fronteras del módulo se respetan sin invadir paquetes internos (`domain` o `infrastructure`) de otros módulos ni crear ciclos?
- [ ] **Metadatos de Módulo (`ARC-01`):** ¿El paquete raíz del subdominio y el paquete `application` cuentan con su respectivo `package-info.java` normativo (`@ApplicationModule` y `@NamedInterface("application")`)?
- [ ] **Inmutabilidad del Kernel `shared/` (`SHR-01`):** ¿Se han consumido los contratos de `shared/` sin modificar clases de la semilla ni introducir lógica de negocio en `shared`?
- [ ] **Purificación de Contexto y RLS (`SHR-03`, `DOM-01`, `INP-04`):** ¿El contexto de ejecución (`ExecutionContext`) se valida en aplicación/workers sin filtrarse hacia el dominio y enlaza `app.current_tenant_id` en la sesión PostgreSQL?
- [ ] **Encapsulación y Predicados FSM (`DOM-02`, `DOM-03`, `DOM-04`):** ¿El Agregado protege sus invariantes con predicados FSM positivos (`can...()`) y constructores privados fail-fast sin exponer `setters`?
- [ ] **Orquestación Pura en Aplicación (`APP-01`, `APP-02`, `APP-03`):** ¿El servicio implementa un único `*UseCase`, orquesta el flujo con `@RequiredArgsConstructor` y distingue comandos `WEB_EXCLUSIVE` de `MULTICHANNEL`?
- [ ] **Factoría Directa y `@ResponseStatus` en Web (`INP-01`, `INP-05`):** ¿Las peticiones web residen en `dto/` con sufijo `HttpRequest`, mapean a Comando mediante `toCommand()` y los controladores retornan el DTO con `@ResponseStatus`?
- [ ] **Confinamiento de Entidades JPA (`OUT-01`, `OUT-02`):** ¿Las entidades `@Entity` están estrictamente confinadas al adaptador de persistencia JPA (`postgres/jpa/`)?
- [ ] **Proyecciones CQRS en JDBC (`OUT-04`):** ¿Las consultas de lectura (`QRY-xx`) van directas a proyecciones DTO vía JDBC (`postgres/jdbc/`) respaldadas por índices de `schema.sql` sin hidratar Agregados?
- [ ] **Transactional Outbox (`DOM-05`, `OUT-05`, `TRX-03`):** ¿Toda mutación de negocio registra su `DomainEvent` y lo persiste atómicamente en `<module_id>.outbox_events` vía `OutboxPublisherPort`?
- [ ] **Idempotencia y Liberación Defensiva (`TRX-05`):** ¿Todo consumidor de eventos (`*Worker`) verifica `<module_id>.processed_events` vía `IdempotencyGate` y libera defensivamente la marca (`release`) solo ante fallos técnicos transitorios?
- [ ] **Serialización Estándar y Gramática de Errores (`INP-03`, `SHR-04`, `ERR-01`, `ERR-03`):** ¿Los errores implementan `ErrorCode` en biyección 1:1 entre `DOMAIN.md` y `API.md` (`[MODULO]_[ENTIDAD]_[MOTIVO]`), heredan de `BaseException` y se serializan vía `GlobalExceptionHandler` a `ErrorResponse`?
- [ ] **Migraciones Flyway, RLS y Auditoría (`OUT-06`..`OUT-08`):** ¿La migración Flyway `V<N>__init_<module_id>_tables.sql` traslada fielmente `docs/modules/<module_id>/schema.sql` con schema propio, 0 FKs cruzadas, `fillfactor = 90`, constraints prefijadas (`pk_`, `fk_`, `uq_`, `chk_`), `audit_log` particionado y `FORCE ROW LEVEL SECURITY`?

### 7.2. Índice de Decisiones de Arquitectura Corporativas (ADRs)

Las siguientes decisiones arquitectónicas están desacopladas en documentos independientes dentro de [`./adrs/`](./adrs/) y forman parte vinculante de este estándar:

| Identificador | Título de la Decisión | Estado | Enlace al Documento Detallado | Reglas Vinculadas |
| :---: | :--- | :---: | :--- | :---: |
| **`ADR_001`** | Persistencia Híbrida y Desacoplamiento de Modelos | `ACEPTADO` | [Ver `ADR_001`](./adrs/ADR_001.md) | `DOM-01`, `OUT-01`..`OUT-04`, `TRX-02` |
| **`ADR_002`** | Publicación Asíncrona mediante Transactional Outbox | `ACEPTADO` | [Ver `ADR_002`](./adrs/ADR_002.md) | `OUT-05`, `TRX-03`..`TRX-06`, `SHR-02` |
| **`ADR_003`** | Estandarización de Errores y Excepciones de Coste Cero | `ACEPTADO` | [Ver `ADR_003`](./adrs/ADR_003.md) | `INP-03`, `SHR-04`, `ERR-01`..`ERR-07` |
| **`ADR_004`** | Gobernanza Modular y Verificación en Compilación | `ACEPTADO` | [Ver `ADR_004`](./adrs/ADR_004.md) | `ARC-01`, `ARC-02`, `ARC-03`, `TST-07` |
| **`ADR_005`** | Consumo Directo de Enums de Estado en Adaptadores Primarios | `ACEPTADO` | [Ver `ADR_005`](./adrs/ADR_005.md) | `DOM-01`, `DOM-03`, `APP-03`, `APP-04`, `INP-01`, `ARC-02` |
| **`ADR_006`** | Validación Híbrida en Comandos (Web vs. Multicanal) | `ACEPTADO` | [Ver `ADR_006`](./adrs/ADR_006.md) | `APP-03`, `SHR-04`, `INP-01` |
| **`ADR_007`** | Contenerización Mínima, Virtual Threads y Seguridad en Runtime | `ACEPTADO` | [Ver `ADR_007`](./adrs/ADR_007.md) | `TRX-01`, `SHR-03` |
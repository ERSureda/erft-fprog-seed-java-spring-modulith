# FProg Standards — Manual de Estrategia de Pruebas y Verificación Automatizada (`TESTING`)

| Propiedad | Valor Normativo |
| :--- | :--- |
| **Sistema / Preset (`preset_id`)** | `FProg STANDARDS` (Fase 2: Construcción Autónoma por Agentes) · `java-spring-modulith` (Java 21+ LTS / Spring Boot 3 / PostgreSQL 17 / Spring Modulith) |
| **Documento / Versión / Estado** | `TESTING.md` (Pirámide de Tests, Testcontainers, Aislamiento RLS y Guardianes de Arquitectura) · `v1.0.0` · `NORMATIVO` (SSOT) |
| **Documentos Padre Asociados** | `.standards/ARCHITECTURE.md` y `.standards/EXCEPTIONS.md` |
| **Alcance y Entradas (Fase 1 — Rails)** | Aplicable a la totalidad de suites unitarias (`docs/modules/<m>/DOMAIN.md`), casos de uso y slices de transporte (`docs/modules/<m>/API.md`), persistencia y aislamiento RLS (`docs/modules/<m>/schema.sql`), flujos de eventos e idempotencia (`docs/04-INTEGRATION.md`), conversión E2E (`docs/01-OPPORTUNITY.md`) y guardianes de arquitectura |
| **Repositorio Oficial (Submódulo)** | `erft-fprog-preset-java-spring-modulith` (`standards/` + `seed/`) |
| **Ubicación Catálogo / Consumidor** | `presets/java-spring-modulith/standards/TESTING.md` ──► `.standards/TESTING.md` (hidratado de forma inmutable vía `./scripts/sync-standards.sh` en `Gate 2`) |
| **Quality Gate Asociado** | `Gate 4 (Production Ready — ./gradlew check en verde en < 5 minutos)` |

---

> 🎯 **Reglas del Juego (FProg STANDARDS — Estrategia de Pruebas y Verificación):**  
> El objetivo de las pruebas en FProg no es inflar porcentajes cosméticos de cobertura en Sonar; es otorgar **confianza matemática absoluta de despliegue a producción** en menos de 5 minutos (**CI Time Budget**).
>
> Para garantizar que las pruebas sean deterministas, ultrarrápidas y verosímiles, en este estándar **queda terminantemente prohibido**:
> 1. **Usar dobles de prueba (*mocks*) en el Dominio:** Los Agregados, Entidades y Value Objects se prueban exclusivamente instanciándolos de verdad en memoria (`TST-01`). Mocks solo permitidos en puertos secundarios `port/out` de casos de uso (`TST-02`).
> 2. **Usar bases de datos en memoria (cero H2/SQLite):** H2 falsea dialectos SQL, extensiones nativas (UUIDv7, JSONB) y las políticas de `ROW LEVEL SECURITY` (`TST-03`). Toda persistencia se prueba contra PostgreSQL 17 real en contenedor singleton reutilizable (`Testcontainers`).
> 3. **Introducir pausas ciegas de tiempo (`Thread.sleep()`):** Prohibido ralentizar la suite con pausas ciegas (`TST-06`); las esperas asíncronas se gestionan con sondeo activo condicional (*Awaitility*).
> 4. **Omitir el Test de Fuga entre Inquilinos (*Tenant Leakage Test*):** Es obligatorio certificar que el inquilino A jamás puede ver ni alterar datos del inquilino B (`TST-08`).
> 5. **Desactivar o puentear los guardianes de arquitectura:** Queda prohibido deshabilitar los tests de Spring Modulith y ArchUnit (`TST-07`). Si la arquitectura se incumple, el build se rompe.

---

## 1. Filosofía, Pirámide y Presupuesto de Ejecución (*CI Budget*)

El sistema de pruebas garantiza confianza de despliegue a producción (`Gate 4`) sin comprometer la velocidad de entrega (*Time-to-Market*):

### 1.1. Principios Rectores
1. **Determinismo Absoluto:** Todo test debe producir el mismo resultado independientemente del entorno de ejecución, orden de llamada o concurrencia de hilos. Queda prohibida la dependencia de redes externas o servicios no controlados.
2. **Verosimilitud en Persistencia (Zero In-Memory DBs):** Las pruebas de persistencia se ejecutan obligatoriamente contra el motor de base de datos real de producción (PostgreSQL 17) mediante contenedores efímeros gestionados con Testcontainers. Queda terminantemente prohibido el uso de emuladores o bases de datos en memoria (H2, SQLite), dado que falsean dialectos SQL, extensiones nativas (JSONB, UUIDv7), operadores avanzados y políticas de aislamiento RLS.
3. **Cero Aserciones Triviales:** Queda prohibido escribir pruebas que verifiquen getters, setters o código autogenerado; cada test debe validar una invariante de dominio, una orquestación transaccional o un contrato de interfaz.

### 1.2. Proporción de la Pirámide de Pruebas

La distribución del esfuerzo de pruebas sigue una estructura piramidal estricta:

```text
       ▲
      / \     5%  Flujo Crítico E2E / Humo (Happy Path de 01-OPPORTUNITY.md y 04-INTEGRATION.md)
     /───\
    /     \   25% Integración Confinada (Slices JPA/JDBC con Testcontainers + MockMvc)
   /───────\
  /         \ 70% Unitarias Puras (Dominio puro y UseCases en memoria < 10 ms)
 ─────────────
```

* **Base (70% — Unitarias Puras):** Pruebas de Agregados, Entidades, Value Objects y Casos de Uso aislados. Ejecución instantánea en memoria sin arranque del contexto de Spring ni contenedores (milisegundos).
* **Cuerpo (25% — Integración Confinada / Slices):** Pruebas de adaptadores de persistencia JPA/JDBC contra PostgreSQL real y slices web con MockMvc.
* **Cúspide (5% — Flujo Crítico E2E / Humo):** Verificación restringida exclusivamente al Happy Path del flujo de conversión principal definido en la especificación de producto (`docs/01-OPPORTUNITY.md` y `docs/04-INTEGRATION.md`).

### 1.3. Presupuesto Máximo de Ejecución (*CI Time Budget*)
* **Tiempo total del pipeline de pruebas (`Gate 4`):** La suite completa de tests de un proyecto (`./gradlew check`) no debe superar los **3 a 5 minutos** en los ejecutores de CI (`TST-10`).
* **Umbral unitario:** Cada prueba unitaria de dominio debe ejecutarse en menos de **10 milisegundos**.
* **Umbral de integración:** Los tests que interactúan con base de datos deben reutilizar una única instancia singleton de contenedor efímero (`AbstractPostgresIntegrationTest`) para evitar penalizaciones de arranque.

---

## 2. Catálogo Normativo de Reglas de Testing (`TST`) y Guardianes CI

* **`TST-01 · NEVER` [A] — Cero Mocks en la Capa de Dominio**
    * **Regla:** Queda terminantemente prohibido utilizar dobles de prueba (*mocks*, *stubs*, *spies*) en pruebas de la capa de Dominio.
    * **Motivación:** El modelo de dominio es Java puro sin dependencias I/O. Mockear un agregado o un Value Object falsea la verificación de invariantes y crea tests frágiles que no prueban la lógica real.
    * **Guardarraíl Técnico:** Los agregados, entidades y Value Objects se prueban exclusivamente mediante instanciación real en memoria con JUnit 5 y AssertJ.
    * **Guardián CI:** `DomainRulesArchTest` y revisión de pruebas unitarias.
* **`TST-02 · MUST` [A/R] — Confinamiento Estricto de Mocks en Casos de Uso**
    * **Regla:** En pruebas de Casos de Uso (`application/service`), los dobles de prueba están confinados estrictamente a las interfaces secundarias de salida (`port/out`).
    * **Motivación:** El caso de uso solo orquesta dependencias externas (repositorios, reloj, generador de IDs). Mockear entidades internas de dominio oculta fallos de interacción.
    * **Guardarraíl Técnico:** Prohibido mockear clases de dominio, Value Objects o estructuras internas de aplicación.
    * **Guardián CI:** ArchUnit y revisión en PR `[R]`.
* **`TST-03 · MUST` [A] — Persistencia Real en PostgreSQL 17 (Zero In-Memory DBs)**
    * **Regla:** Toda prueba de adaptadores de persistencia debe ejecutarse contra una instancia de PostgreSQL 17 real mediante contenedores efímeros aislados (Testcontainers).
    * **Motivación:** H2 y SQLite no soportan las políticas nativas de `ROW LEVEL SECURITY`, los tipos `JSONB`, la generación de UUIDv7 ni los locks transaccionales de PostgreSQL. Un test en H2 en verde puede explotar en producción.
    * **Guardarraíl Técnico:** Heredar de `AbstractPostgresIntegrationTest`. Prohibido H2 en `build.gradle`.
    * **Guardián CI:** CI build y ArchUnit.
* **`TST-04 · MUST` [A] — Slices Web Aislados con MockMvc**
    * **Regla:** Las pruebas de adaptadores web deben ejecutarse como slices de transporte (`@WebMvcTest`), validando exclusivamente mapeo sintáctico (`@Valid`), cabeceras (`ApiHeaders`), códigos HTTP y serialización de `ErrorResponse`.
    * **Motivación:** Levantar el servidor completo con `@SpringBootTest` para probar un endpoint es lento e innecesario; los slices web prueban el perímetro HTTP en milisegundos.
    * **Guardarraíl Técnico:** `@WebMvcTest(controllers = *Controller.class)` con `@MockBean` en la interfaz del caso de uso (`port/in`).
    * **Guardián CI:** Pruebas de integración web.
* **`TST-05 · MUST` [A] — Test Obligatorio de Idempotencia en Workers**
    * **Regla:** Todo consumidor asíncrono o worker debe contar con una prueba de idempotencia obligatoria y manejo diferenciado de fallos (`TRX-05`).
    * **Motivación:** Los brokers de mensajería garantizan entrega al menos una vez (*at-least-once*). Si un evento llega duplicado, el worker debe procesarlo exactamente una vez sin duplicar efectos secundarios.
    * **Guardarraíl Técnico:** Certificar la deduplicación en `<module_id>.processed_events` ante la recepción consecutiva del mismo `eventId` y la liberación defensiva `release()` ante fallos técnicos transitorios.
    * **Guardián CI:** Suite de integración/unitaria de workers.
* **`TST-06 · NEVER` [A] — Prohibición de Pausas Ciegas (`Thread.sleep`)**
    * **Regla:** Jamás se utilizarán pausas ciegas de tiempo (`Thread.sleep()`) en pruebas asíncronas.
    * **Motivación:** Ralentizan exponencialmente la suite de CI y provocan falsos positivos cuando el entorno de CI sufre picos de carga.
    * **Guardarraíl Técnico:** Toda sincronización temporal debe efectuarse mediante sondeo condicional reactivo con timeout (*Awaitility*).
    * **Guardián CI:** ArchUnit y linter estático de pruebas.
* **`TST-07 · MUST` [A] — Guardianes de Arquitectura Automáticos (Fitness Functions)**
    * **Regla:** La suite de pruebas debe incorporar la ejecución obligatoria de los guardianes de arquitectura (Spring Modulith y ArchUnit).
    * **Motivación:** Ninguna revisión humana en Pull Request es infalible. Los guardianes de código rompen el build automáticamente si alguien acopla dos módulos o introduce setters en el dominio.
    * **Guardarraíl Técnico:** Ejecución de las 5 suites en `src/test/java/.../architecture/` y `ApplicationModules.of(ApiApplication.class).verify()`.
    * **Guardián CI:** `./gradlew check`.
* **`TST-08 · MUST` [A] — Test Mandatorio de Fuga entre Inquilinos (Tenant Leakage)**
    * **Regla:** Todo módulo con tablas multi-tenant protegidas por `ROW LEVEL SECURITY` (`schema.sql`) debe incluir un Test de Fuga entre Inquilinos.
    * **Motivación:** En entornos SaaS B2B, que un cliente acceda a datos de otro cliente es una brecha de seguridad inadmisible. Debe demostrarse empíricamente con un test que RLS funciona.
    * **Guardarraíl Técnico:** Certificar que los registros insertados bajo el `Tenant A` son invisibles e inmutables bajo la sesión del `Tenant B`.
    * **Guardián CI:** Testcontainers + RLS suite.
* **`TST-09 · MUST` [R] — Estructura Semántica AAA (Arrange-Act-Assert)**
    * **Regla:** Toda prueba debe estructurarse obligatoriamente bajo el patrón Arrange-Act-Assert (o Given-When-Then) y utilizar nomenclatura semántica.
    * **Motivación:** Cuando un test falla en CI, el desarrollador debe entender qué falló y por qué en 5 segundos sin tener que leer el cuerpo del test.
    * **Guardarraíl Técnico:** Convención: `should_<ExpectedBehavior>_when_<Condition>`.
    * **Guardián CI:** Revisión en PR `[R]`.
* **`TST-10 · MUST` [A] — Presupuesto de Ejecución Máximo en CI (< 5 min)**
    * **Regla:** La ejecución completa del comando `./gradlew check` debe finalizar en menos de 5 minutos.
    * **Motivación:** Pipelines de CI que tardan 20 minutos destruyen la productividad, desalientan las integraciones continuas y retrasan el despliegue.
    * **Guardarraíl Técnico:** Reutilizar el contenedor singleton `AbstractPostgresIntegrationTest` y optimizar cortes de contexto.
    * **Guardián CI:** Timeout del runner de CI.

---

## 3. Topología y Delimitación de Pruebas por Capa

```text
┌────────────────────────────────────────────────────────────────────────┐
│ 3.1 DOMINIO: Unitarias Puras (Invariantes, Value Objects, Factorías)   │ ──► Cero mocks, 100% en memoria (< 10 ms)
├────────────────────────────────────────────────────────────────────────┤
│ 3.2 APLICACIÓN: Orquestación (UseCases con mocks en port/out)          │ ──► Mocks solo en dependencias I/O
├────────────────────────────────────────────────────────────────────────┤
│ 3.3 SALIDA / PERSISTENCIA: Integración Real (Contenedor Efímero)       │ ──► Motor SQL real, mapeo, RLS, Outbox
├────────────────────────────────────────────────────────────────────────┤
│ 3.4 ENTRADA / WEB: Slices de Transporte (MockMvc / HTTP Test Client)   │ ──► Validaciones, Headers, HTTP Status
├────────────────────────────────────────────────────────────────────────┤
│ 3.5 WORKERS / ASÍNCRONO: Idempotencia y Políticas de Fallo / DLQ       │ ──► Replay de eventos, deduplicación
└────────────────────────────────────────────────────────────────────────┘
```

### 3.0. Matriz Operativa: Asignación de Tests por Artefacto

| Capa / Componente | Artefacto Rails Vinculado | Qué se Testea (Responsabilidad) | Dónde se Ubica el Test | Tipo de Test | Anotaciones Técnicas Requeridas | Dobles Permitidos |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`domain.model.AggregateRoot`** | `DOMAIN.md` (Secciones 2, 3, 4) | Invariantes de negocio, predicados FSM (`can...()`), transiciones válidas, factorías (`create`, `reconstruct`), registro de `DomainEvent` y lanzamiento de `<Subdominio>Error`. | `src/test/java/.../<module_id>/domain/` | Unitario Puro | Ninguna (JUnit 5 puro) | Cero mocks. |
| **`domain.model.ValueObject`** | `DOMAIN.md` (Secciones 1.2, 3.1) | Auto-validación en constructor/compact record, inmutabilidad y cálculo de valor. | `src/test/java/.../<module_id>/domain/` | Unitario Puro | Ninguna (JUnit 5 puro) | Cero mocks. |
| **`application.service.*Service`** | `API.md` (Secciones 1, 2, 4) | Orquestación del caso de uso, verificación de `ExecutionContext`, propagación de `BaseException`, mapeo a DTO `*Result`. | `src/test/java/.../<module_id>/application/service/` | Unitario con Mocks | `@ExtendWith(MockitoExtension.class)` | Mocks exclusivos en `port/out`. |
| **`adapter.out.persistence.jpa`** | `schema.sql` & `DOMAIN.md` | Mapeo bidireccional Entity-Agregado, constraints (`UNIQUE`, `CHECK`), concurrencia optimista (`version`), persistencia Outbox. | `src/test/java/.../<module_id>/infrastructure/adapter/out/persistence/postgres/jpa/` | Integración (Slice JPA) | `@DataJpaTest`, `@AutoConfigureTestDatabase(replace = NONE)` | Cero mocks (Postgres Real). |
| **`adapter.out.persistence.jdbc`** | `schema.sql` & `API.md` (`QRY-xx`) | Queries SQL sobre `<module_id>.*`, proyecciones directas a DTOs (`*Result`, `CursorResult`), aislamiento RLS (*Tenant Leakage Test*). | `src/test/java/.../<module_id>/infrastructure/adapter/out/persistence/postgres/jdbc/` | Integración (Slice JDBC) | `@JdbcTest`, `@AutoConfigureTestDatabase(replace = NONE)` | Cero mocks (Postgres Real). |
| **`adapter.in.web.*Controller`** | `API.md` (Secciones 3, 4) | Validación sintáctica (`@Valid` con tokens `[ENTIDAD]_[CAMPO]_[REGLA]`), cabeceras (`ApiHeaders`), HTTP Status, serialización de `ErrorResponse`. | `src/test/java/.../<module_id>/infrastructure/adapter/in/web/` | Slice de Transporte | `@WebMvcTest(controllers = *Controller.class)` | `@MockBean` en `*UseCase`. |
| **`adapter.in.worker.*Worker`** | `04-INTEGRATION.md` (`EVT-xx`) | Deserialización de eventos, compuerta de idempotencia (`<module_id>.processed_events`), liberación defensiva `release()` ante fallos técnicos. | `src/test/java/.../<module_id>/infrastructure/adapter/in/worker/` | Unitario / Integración | `@ExtendWith(MockitoExtension.class)` o `@SpringBootTest` + Awaitility | Mocks en casos de uso / brokers. |
| **`architecture` (Gobernanza)** | `.standards/ARCHITECTURE.md` | Fronteras de Spring Modulith, ausencia de acoplamiento ilegal, confinamiento de JPA/JDBC, pureza del dominio. | `src/test/java/.../architecture/` | Fitness Functions | `@AnalyzeClasses(packages = "...")` | Cero mocks. |

---

### 3.1. Pruebas de Dominio (Unitarias Puras)

* **Qué se prueba:** Invariantes de `AggregateRoot`, métodos factoría estáticos (`create`, `reconstruct`), Value Objects inmutables, predicados FSM (`can...()`), transiciones de estado legales y registro de eventos de dominio (`DomainEvent`).
* **Qué está prohibido:** Levantar contextos de Spring (`@SpringBootTest`), abrir transacciones o usar dobles de prueba (Mockito).
* **Velocidad objetivo:** < 10 milisegundos por test.

```java
package com.empresa.proyecto.ordering.domain;

import com.empresa.proyecto.ordering.domain.event.OrderCreatedEvent;
import com.empresa.proyecto.ordering.domain.model.Order;
import com.empresa.proyecto.ordering.domain.model.enums.OrderStatus;
import com.empresa.proyecto.shared.domain.exception.ValidationException;
import com.empresa.proyecto.shared.domain.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Dominio: Order Aggregate")
class OrderTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final Money totalAmount = Money.of(new BigDecimal("150.00"), Currency.getInstance("EUR"));

    @Test
    @DisplayName("Debe crear una orden en estado PENDING y emitir OrderCreatedEvent")
    void should_create_order_in_pending_status_and_register_event() {
        Order order = Order.create(UUID.randomUUID(), tenantId, customerId, totalAmount);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getVersion()).isZero();
        assertThat(order.canConfirm()).isTrue();

        assertThat(order.hasDomainEvents()).isTrue();
        assertThat(order.pullDomainEvents())
            .hasSize(1)
            .first()
            .isInstanceOf(OrderCreatedEvent.class);
    }

    @Test
    @DisplayName("Debe lanzar ValidationException si el importe total no es positivo")
    void should_throw_validation_exception_when_total_amount_is_zero_or_negative() {
        Money zeroAmount = Money.zero(Currency.getInstance("EUR"));

        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), tenantId, customerId, zeroAmount))
            .isInstanceOf(ValidationException.class);
    }
}
```

---

### 3.2. Pruebas de Casos de Uso (Aplicación)

* **Qué se prueba:** Orquestación del caso de uso, verificación de permisos sobre `ExecutionContext`, llamadas a puertos de salida (`port/out`), propagación correcta de excepciones y retorno de DTO `*Result`.
* **Herramientas:** JUnit 5, Mockito (`@ExtendWith(MockitoExtension.class)`). Mocks restringidos exclusivamente a dependencias I/O (`port/out`).

```java
package com.empresa.proyecto.ordering.application.service;

import com.empresa.proyecto.ordering.application.command.CreateOrderCommand;
import com.empresa.proyecto.ordering.application.port.out.OrderRepositoryPort;
import com.empresa.proyecto.ordering.application.result.OrderResult;
import com.empresa.proyecto.ordering.domain.model.Order;
import com.empresa.proyecto.shared.application.context.ExecutionContext;
import com.empresa.proyecto.shared.application.context.UserType;
import com.empresa.proyecto.shared.application.port.out.ExecutionContextPort;
import com.empresa.proyecto.shared.application.port.out.UuidGeneratorPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Aplicación: CreateOrderService")
class CreateOrderServiceTest {

    @Mock private OrderRepositoryPort orderRepository;
    @Mock private UuidGeneratorPort uuidGenerator;
    @Mock private ExecutionContextPort executionContextPort;

    @InjectMocks
    private CreateOrderService createOrderService;

    @Test
    @DisplayName("Debe orquestar la creación de una orden y delegar su persistencia al puerto de salida")
    void should_orchestrate_order_creation_and_persist_via_repository_port() {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ExecutionContext context = new ExecutionContext(
            UserType.TENANT_USER,
            tenantId,
            UUID.randomUUID(),
            Set.of("TENANT_USER"),
            "corr-test-001"
        );

        when(executionContextPort.current()).thenReturn(context);
        when(uuidGenerator.generateId()).thenReturn(orderId);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand command = new CreateOrderCommand(UUID.randomUUID(), new BigDecimal("100.00"), "EUR");
        OrderResult result = createOrderService.execute(command);

        assertThat(result.id()).isEqualTo(orderId);
        verify(orderRepository).save(any(Order.class));
    }
}
```

---

### 3.3. Pruebas de Persistencia Real (Slices con Testcontainers)

* **Qué se prueba:** Mapeo relacional, constraints de base de datos (`UNIQUE`, `CHECK`), serialización Flyway y aislamiento RLS real sobre PostgreSQL 17.
* **Herramientas:** `@DataJpaTest` o `@JdbcTest` heredando de `AbstractPostgresIntegrationTest`.

```java
package com.empresa.proyecto.ordering.infrastructure.adapter.out.persistence.postgres.jpa;

import com.empresa.proyecto.ordering.domain.model.Order;
import com.empresa.proyecto.ordering.domain.model.enums.OrderStatus;
import com.empresa.proyecto.shared.domain.valueobject.Money;
import com.empresa.proyecto.shared.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderPersistenceAdapter.class, OrderPersistenceMapper.class})
@DisplayName("Persistencia: OrderPersistenceAdapter (PostgreSQL 17 Real)")
class OrderPersistenceAdapterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderPersistenceAdapter persistenceAdapter;

    @Test
    @DisplayName("Debe persistir y recuperar un agregado Order preservando su versión optimista")
    void should_persist_and_retrieve_order_aggregate() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Order order = Order.create(orderId, tenantId, UUID.randomUUID(), Money.of(new BigDecimal("99.99"), Currency.getInstance("EUR")));

        persistenceAdapter.save(order);

        Optional<Order> loaded = persistenceAdapter.findById(orderId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(loaded.get().getVersion()).isZero();
    }
}
```

---

### 3.4. Pruebas de Adaptadores de Entrada (Web Slices con MockMvc)

* **Qué se prueba:** Validación Bean Validation (`@Valid` con tokens estructurados `[ENTIDAD]_[CAMPO]_[REGLA]`), cabeceras HTTP perimetrales (`ApiHeaders`), códigos HTTP (`@ResponseStatus`) y serialización estándar a `ErrorResponse`.

```java
package com.empresa.proyecto.ordering.infrastructure.adapter.in.web;

import com.empresa.proyecto.ordering.application.port.in.CreateOrderUseCase;
import com.empresa.proyecto.ordering.application.result.OrderResult;
import com.empresa.proyecto.ordering.domain.model.enums.OrderStatus;
import com.empresa.proyecto.shared.infrastructure.adapter.in.web.ApiHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CreateOrderController.class)
@DisplayName("Web Slice: CreateOrderController")
class CreateOrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CreateOrderUseCase createOrderUseCase;

    @Test
    @DisplayName("Debe retornar HTTP 201 CREATED con el DTO cuando el payload y cabeceras son válidas")
    void should_return_201_created_when_request_is_valid() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(createOrderUseCase.execute(any()))
            .thenReturn(new OrderResult(orderId, OrderStatus.PENDING, new BigDecimal("120.00"), "EUR", Instant.now()));

        mockMvc.perform(post("/api/v1/orders")
                .header(ApiHeaders.TENANT_ID, tenantId.toString())
                .header(ApiHeaders.USER_ID, UUID.randomUUID().toString())
                .header(ApiHeaders.ROLES, "TENANT_USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "customerId": "%s",
                        "amount": 120.00,
                        "currency": "EUR"
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(orderId.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("Debe retornar HTTP 400 con token estructurado cuando falta el importe")
    void should_return_400_with_structured_token_when_amount_is_missing() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .header(ApiHeaders.TENANT_ID, UUID.randomUUID().toString())
                .header(ApiHeaders.USER_ID, UUID.randomUUID().toString())
                .header(ApiHeaders.ROLES, "TENANT_USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "customerId": "%s",
                        "currency": "EUR"
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].message").value("ORDER_AMOUNT_REQUIRED"));
    }
}
```

---

## 4. Pruebas Transversales Críticas: Multi-Tenancy, Outbox e Idempotencia

### 4.1. Test Mandatorio de Fuga entre Inquilinos (*Tenant Leakage Test* — `TST-08`)

Este test es **mandatorio** en todo módulo con multi-tenancy. Demuestra de forma incontrovertible que las políticas `ROW LEVEL SECURITY` de PostgreSQL impiden que un inquilino acceda a los datos de otro:

```java
package com.empresa.proyecto.ordering.infrastructure.adapter.out.persistence.postgres;

import com.empresa.proyecto.shared.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Seguridad: Test de Fuga entre Inquilinos (RLS Mandatorio)")
class TenantLeakageIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Los registros del Tenant A deben ser completamente invisibles para el Tenant B")
    void should_prevent_tenant_b_from_reading_or_mutating_tenant_a_records() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID orderIdA = UUID.randomUUID();

        // 1. Activar sesión como Tenant A e insertar un registro
        jdbcTemplate.getJdbcTemplate().execute("SELECT set_config('app.current_tenant_id', '" + tenantA + "', false)");
        jdbcTemplate.update("""
            INSERT INTO ordering.orders (id, tenant_id, customer_id, total_amount, currency, status, version)
            VALUES (:id, :tenantId, :customerId, 100.00, 'EUR', 'PENDING', 0)
            """, Map.of("id", orderIdA, "tenantId", tenantA, "customerId", UUID.randomUUID()));

        // Confirmar que Tenant A sí ve su propio registro
        Integer countA = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ordering.orders WHERE id = :id",
            Map.of("id", orderIdA),
            Integer.class
        );
        assertThat(countA).isEqualTo(1);

        // 2. Cambiar contexto de sesión hacia Tenant B
        jdbcTemplate.getJdbcTemplate().execute("SELECT set_config('app.current_tenant_id', '" + tenantB + "', false)");

        // 3. Verificar que Tenant B recibe CERO resultados (RLS en acción)
        Integer countB = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ordering.orders WHERE id = :id",
            Map.of("id", orderIdA),
            Integer.class
        );
        assertThat(countB).isZero();

        // 4. Verificar que Tenant B no puede mutar datos del Tenant A
        int rowsUpdated = jdbcTemplate.update(
            "UPDATE ordering.orders SET status = 'CANCELLED' WHERE id = :id",
            Map.of("id", orderIdA)
        );
        assertThat(rowsUpdated).isZero();
    }
}
```

---

### 4.2. Test Mandatorio de Atomicidad del Transactional Outbox (`TRX-03`)

Garantiza que la mutación del agregado de dominio y la inserción del evento en `<module_id>.outbox_events` formen una **única transacción atómica** indivisible:

1. **Configuración:** Ejecutar un caso de uso de escritura real que persista el agregado y llame a `OutboxPublisherPort`.
2. **Escenario de Fallo:** Provocar una excepción técnica (`InfrastructureException` vía `@SpyBean` en `OutboxPublisherPort` o fallo simulado antes del cierre del commit transaccional) sin contaminar la firma de producción del servicio.
3. **Aserción:** Certificar que la transacción ejecutó un rollback atómico completo: ni el registro del agregado ni la fila en `outbox_events` deben quedar persistidos en la base de datos.

```java
package com.empresa.proyecto.ordering.application.service;

import com.empresa.proyecto.ordering.application.command.CreateOrderCommand;
import com.empresa.proyecto.shared.application.port.out.OutboxPublisherPort;
import com.empresa.proyecto.shared.domain.exception.InfrastructureException;
import com.empresa.proyecto.shared.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@DisplayName("Persistencia: Atomicidad del Transactional Outbox (TRX-03)")
class OrderTransactionalOutboxIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private CreateOrderService createOrderService;
    @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
    @SpyBean private OutboxPublisherPort outboxPublisherPort;

    @Test
    @DisplayName("Debe revertir tanto el Agregado como el evento Outbox si la transacción falla antes del commit")
    void should_rollback_aggregate_and_outbox_event_when_transaction_fails_before_commit() {
        UUID customerId = UUID.randomUUID();
        CreateOrderCommand command = new CreateOrderCommand(customerId, new BigDecimal("250.00"), "EUR");

        // Simular fallo técnico en la publicación del Outbox dentro de la transacción activa
        doThrow(new InfrastructureException("Simulated Outbox DB failure", new RuntimeException()))
            .when(outboxPublisherPort).publish(any(), any(), any(), any());

        assertThatThrownBy(() -> createOrderService.execute(command))
            .isInstanceOf(InfrastructureException.class);

        // Verificar que no existen registros huérfanos ni en orders ni en outbox_events
        Integer ordersCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ordering.orders WHERE customer_id = :customerId",
            Map.of("customerId", customerId),
            Integer.class
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ordering.outbox_events WHERE payload::text LIKE :match",
            Map.of("match", "%" + customerId + "%"),
            Integer.class
        );

        assertThat(ordersCount).isZero();
        assertThat(outboxCount).isZero();
    }
}
```

---

### 4.3. Test Mandatorio de Idempotencia y Release Defensivo en Workers (`TST-05`, `TRX-05`)

Certifica que ante la recepción duplicada del mismo mensaje (`eventId`), el consumidor no ejecuta el caso de uso dos veces, y que ante un fallo técnico transitorio (`capturesDiagnostics() == true`) libera defensivamente la marca:

```java
package com.empresa.proyecto.ordering.infrastructure.adapter.in.worker;

import com.empresa.proyecto.ordering.application.port.in.ProcessShipmentUseCase;
import com.empresa.proyecto.shared.application.port.out.IdempotencyGate;
import com.empresa.proyecto.shared.domain.exception.InfrastructureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Worker: Idempotencia y Release Defensivo en Consumo Asíncrono")
class ShipmentWorkerIdempotencyTest {

    @Mock private IdempotencyGate idempotencyGate;
    @Mock private ProcessShipmentUseCase processShipmentUseCase;

    @InjectMocks
    private ShipmentWorker shipmentWorker;

    @Test
    @DisplayName("Debe descartar el evento y emitir ACK sin invocar el caso de uso si ya fue procesado")
    void should_skip_execution_when_event_was_already_processed() {
        UUID eventId = UUID.randomUUID();
        when(idempotencyGate.tryAcquire("ordering", eventId, "ShipmentWorker")).thenReturn(false);

        shipmentWorker.consume(new ShipmentEventMessage(eventId, UUID.randomUUID(), "SHIPPED"));

        verify(processShipmentUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("Debe liberar defensivamente la marca de idempotencia si ocurre una excepción técnica transitoria")
    void should_release_idempotency_mark_when_technical_exception_occurs() {
        UUID eventId = UUID.randomUUID();
        when(idempotencyGate.tryAcquire("ordering", eventId, "ShipmentWorker")).thenReturn(true);
        doThrow(new InfrastructureException("DB Timeout", new RuntimeException()))
            .when(processShipmentUseCase).execute(any());

        assertThatThrownBy(() -> shipmentWorker.consume(new ShipmentEventMessage(eventId, UUID.randomUUID(), "SHIPPED")))
            .isInstanceOf(InfrastructureException.class);

        verify(idempotencyGate).release("ordering", eventId, "ShipmentWorker");
    }
}
```

---

## 5. Infraestructura de Test: Contenedor Singleton, Factorías y Reloj

### 5.1. Contenedor Singleton Compartido (`AbstractPostgresIntegrationTest`)

Para cumplir el presupuesto de CI de **< 5 minutos** (`TST-10`), todos los tests de persistencia heredan de `AbstractPostgresIntegrationTest` (ubicada en `<namespace.base>.shared.infrastructure`), la cual mantiene un único contenedor Docker encendido durante toda la suite:

```java
package com.empresa.proyecto.shared.infrastructure;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class AbstractPostgresIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER;

    static {
        POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("fprog_test")
                .withUsername("test_user")
                .withPassword("test_pass")
                .withReuse(true);
        POSTGRES_CONTAINER.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
        registry.add("spring.flyway.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.flyway.password", POSTGRES_CONTAINER::getPassword);
    }
}
```

### 5.2. Factorías de Datos (Patrón Object Mother / Builders)

* Se prohíbe instanciar manualmente entidades complejas o construir JSONs repetitivos dentro del cuerpo de los tests.
* Los datos de prueba se generan mediante factorías especializadas (*Object Mother*) ubicadas en `src/test/java/.../fixture/`, las cuales proveen instancias válidas preconfiguradas con sobreescritura selectiva de campos:

```java
package com.empresa.proyecto.ordering.fixture;

import com.empresa.proyecto.ordering.domain.model.Order;
import com.empresa.proyecto.shared.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

public final class OrderFixture {

    private OrderFixture() {}

    public static Order createPendingOrder(UUID tenantId, UUID customerId) {
        return Order.create(
            UUID.randomUUID(),
            tenantId,
            customerId,
            Money.of(new BigDecimal("100.00"), Currency.getInstance("EUR"))
        );
    }
}
```

### 5.3. Control Temporal Determinista (`UtcClockPort`)

* Toda lógica dependiente de fechas, expiraciones o marcas de auditoría debe consumir la interfaz `UtcClockPort`.
* En las pruebas unitarias y de integración se inyecta un reloj fijo determinista (`Clock.fixed(instant, ZoneOffset.UTC)`), garantizando que las pruebas nunca dependan del reloj del hardware o del sistema operativo ni provoquen *flakiness*.

---

## 6. Guardianes de Arquitectura (*Fitness Functions* con ArchUnit y Modulith)

Para garantizar un diseño modular limpio y mantenible, las reglas de arquitectura no se agrupan en una clase monolítica frágil, sino que se organizan en **5 suites especializadas por capa** ubicadas en `src/test/java/.../architecture/`:

| Guardián de Arquitectura | Clase de Test | Reglas Verificadas | Condición Técnica de Fallo Automatizada |
| :--- | :--- | :--- | :--- |
| **Gobernanza Modulith** | `ModulithStructureTest` | `ARC-01`, `ARC-02`, `ARC-03` | `ApplicationModules.of(ApiApplication.class).verify()` detecta ciclos entre módulos o invasión de interfaces no nombradas. |
| **Pureza de Dominio** | `DomainRulesArchTest` | `DOM-01`, `DOM-02`, `DOM-04` | Paquete `..domain..` importa librerías externas (Spring, Jakarta, Jackson, Lombok), expone constructores públicos o métodos `set*`. |
| **Capa de Aplicación** | `ApplicationRulesArchTest` | `APP-01`, `TRX-01` | Servicios en `..application.service..` no implementan exactamente un `*UseCase`, no terminan en `Service` o `@Transactional` reside fuera de `application` (salvo el relay transversal en `shared`). |
| **Persistencia Segregada** | `PersistenceRulesArchTest` | `OUT-01`, `OUT-05` | `@Entity` fuera de `postgres.jpa` o `NamedParameterJdbcTemplate` fuera de `postgres.jdbc` / adaptadores transversales de salida en `shared`. |
| **Adaptadores Web** | `WebRulesArchTest` | `INP-01`, `INP-02` | Controladores en `..adapter.in.web..` dependen de `domain.model` (salvo enums) o contienen `@Transactional`. |

A continuación se detalla la implementación canónica de las 5 suites:

### 6.1. Verificación Estructural de Spring Modulith (`ModulithStructureTest.java`)

```java
package com.empresa.proyecto.architecture;

import com.empresa.proyecto.ApiApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

@DisplayName("Spring Modulith Architectural Verification")
class ModulithStructureTest {

    @Test
    @DisplayName("Debe verificar que la estructura modular y fronteras de Modulith son válidas (ARC-01, ARC-02, ARC-03)")
    void verifyModulithStructure() {
        ApplicationModules modules = ApplicationModules.of(ApiApplication.class);
        modules.verify();
    }

    @Test
    @DisplayName("Debe generar la documentación y diagramas de arquitectura de Modulith")
    void generateArchitectureDocumentation() {
        new Documenter(ApplicationModules.of(ApiApplication.class))
                .writeDocumentation()
                .writeIndividualModulesAsPlantUml();
    }
}
```

### 6.2. Pureza y Encapsulación del Dominio (`DomainRulesArchTest.java`)

```java
package com.empresa.proyecto.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

@AnalyzeClasses(packages = "com.empresa.proyecto", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainRulesArchTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_frameworks =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "com.fasterxml.jackson..",
                            "lombok.."
                    ).as("DOM-01: El dominio no debe depender de frameworks o librerías de terceros");

    @ArchTest
    static final ArchRule domain_models_should_not_expose_public_constructors =
            constructors().that().areDeclaredInClassesThat().resideInAPackage("..domain.model..")
                    .should().notBePublic()
                    .as("DOM-02: Las entidades y agregados de dominio no deben exponer constructores públicos");

    @ArchTest
    static final ArchRule domain_models_should_not_expose_public_setters =
            methods().that().areDeclaredInClassesThat().resideInAPackage("..domain.model..")
                    .and().arePublic()
                    .should().haveNameNotStartingWith("set")
                    .as("DOM-04: Los agregados y entidades de dominio no deben exponer setters públicos");
}
```

### 6.3. Convenciones de Aplicación y Transaccionalidad (`ApplicationRulesArchTest.java`)

```java
package com.empresa.proyecto.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@AnalyzeClasses(packages = "com.empresa.proyecto", importOptions = ImportOption.DoNotIncludeTests.class)
class ApplicationRulesArchTest {

    private static final DescribedPredicate<JavaClass> IMPLEMENT_EXACTLY_ONE_USE_CASE =
            describe("implementar exactamente una interfaz UseCase",
                    javaClass -> javaClass.getRawInterfaces().size() == 1 &&
                            javaClass.getRawInterfaces().iterator().next().getSimpleName().endsWith("UseCase"));

    @ArchTest
    static final ArchRule application_services_should_implement_one_use_case =
            classes().that().resideInAPackage("..application.service..")
                    .should().haveSimpleNameEndingWith("Service")
                    .andShould(ArchCondition.from(IMPLEMENT_EXACTLY_ONE_USE_CASE))
                    .as("APP-01: Cada servicio de aplicación debe implementar exactamente una interfaz UseCase y terminar en Service");

    @ArchTest
    static final ArchRule transactional_should_only_be_in_application =
            methods().that().areAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                    .should().beDeclaredInClassesThat().resideInAnyPackage(
                            "..application..",
                            "..shared.infrastructure.adapter.out.."
                    )
                    .as("TRX-01: La demarcación transaccional debe residir exclusivamente en la capa de aplicación (salvo el relay transversal en shared)");
}
```

### 6.4. Confinamiento de Persistencia Híbrida (`PersistenceRulesArchTest.java`)

```java
package com.empresa.proyecto.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.empresa.proyecto", importOptions = ImportOption.DoNotIncludeTests.class)
class PersistenceRulesArchTest {

    @ArchTest
    static final ArchRule entities_should_reside_in_jpa_persistence =
            classes().that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().resideInAPackage("..infrastructure.adapter.out.persistence..jpa..")
                    .as("OUT-01: Las entidades JPA (@Entity) deben residir exclusivamente en el adaptador de persistencia JPA");

    @ArchTest
    static final ArchRule jdbc_template_should_reside_in_jdbc_persistence =
            noClasses().that().resideOutsideOfPackages(
                            "..infrastructure.adapter.out.persistence..jdbc..",
                            "..shared.infrastructure.adapter.out.."
                    )
                    .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")
                    .as("OUT-05: El uso de NamedParameterJdbcTemplate debe confinarse al adaptador de persistencia JDBC o a adaptadores transversales en shared");
}
```

### 6.5. Desacoplamiento de Adaptadores Web (`WebRulesArchTest.java`)

```java
package com.empresa.proyecto.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

@AnalyzeClasses(packages = "com.empresa.proyecto", importOptions = ImportOption.DoNotIncludeTests.class)
class WebRulesArchTest {

    private static final DescribedPredicate<JavaClass> RESIDE_IN_DOMAIN_MODEL_EXCEPT_ENUMS =
            describe("residir en domain.model excluyendo enums",
                    javaClass -> javaClass.getPackage().getName().contains(".domain.model") &&
                            !javaClass.getPackage().getName().contains(".domain.model.enums"));

    @ArchTest
    static final ArchRule web_adapters_should_not_depend_on_domain_models_except_enums =
            noClasses().that().resideInAPackage("..adapter.in.web..")
                    .should().dependOnClassesThat(RESIDE_IN_DOMAIN_MODEL_EXCEPT_ENUMS)
                    .as("INP-01: Los adaptadores web no deben depender del modelo de dominio salvo enums de estado");

    @ArchTest
    static final ArchRule web_adapters_should_not_be_transactional =
            noMethods().that().areDeclaredInClassesThat().resideInAPackage("..adapter.in.web..")
                    .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                    .as("INP-02: Los adaptadores web nunca deben abrir transacciones (@Transactional)");
}
```

---

## 7. Matriz Pedagógica de Anti-Patrones y Checklist de Pull Request

### 7.1. Matriz de Anti-Patrones Comunes en Testing

| Anti-Patrón | Por qué falla | Solución Normativa | Regla Asociada |
| :--- | :--- | :--- | :---: |
| **Mockear Entidades de Dominio** | Falsea las invariantes del agregado y oculta transiciones ilegales de estado. | Probar el dominio exclusivamente instanciando clases reales en memoria sin Mockito. | `TST-01` |
| **Bases de datos en memoria (H2/SQLite)** | Falsean el soporte de RLS, dialectos nativos de PostgreSQL 17 y tipos JSONB. | Ejecutar persistencia siempre contra PostgreSQL 17 real vía Testcontainers singleton. | `TST-03` |
| **Pausas ciegas (`Thread.sleep`)** | Provocan tests frágiles, lentos y fallos esporádicos en los ejecutores de CI. | Sincronización reactiva con sondeo condicional vía Awaitility. | `TST-06` |
| **Levantar Spring para Unitarias** | `@SpringBootTest` tarda segundos en arrancar, multiplicando el tiempo del build. | Unitarias puras de Dominio con JUnit 5 plano (< 10 ms por test). | `TST-01`, `TST-10` |
| **Omitir Test de Fuga RLS** | Permite que una mala configuración en Flyway exponga datos entre inquilinos. | Incluir `TenantLeakageIntegrationTest` mandatorio en cada módulo. | `TST-08` |
| **Desactivar ArchUnit por Prisa** | Rompe el blindaje arquitectónico del sistema e introduce deuda irreversible. | Corregir la violación de capas en lugar de comentar la regla de ArchUnit. | `TST-07` |

### 7.2. Checklist de Verificación para Pull Requests y Gate 4

- [ ] **Pureza en Dominio (`TST-01`):** ¿Los tests de dominio instancian objetos reales en memoria sin invocar Mockito ni Spring?
- [ ] **Mocks Restringidos a Salida (`TST-02`):** ¿Los tests de casos de uso aplican dobles de prueba exclusivamente sobre interfaces `port/out`?
- [ ] **Persistencia en Postgres Real (`TST-03`):** ¿Las pruebas de repositorio JPA/JDBC heredan de `AbstractPostgresIntegrationTest` sobre PostgreSQL 17?
- [ ] **Idempotencia Certificada (`TST-05`):** ¿Cada worker cuenta con un test que demuestra deduplicación ante mensajes idénticos y liberación defensiva `release()` ante fallos técnicos?
- [ ] **Cero Pausas Ciegas (`TST-06`):** ¿La suite está completamente limpia de llamadas a `Thread.sleep()`?
- [ ] **Guardianes ArchUnit en Verde (`TST-07`):** ¿Las 5 suites de ArchUnit y Modulith (`ModulithStructureTest`, `DomainRulesArchTest`, `ApplicationRulesArchTest`, `PersistenceRulesArchTest`, `WebRulesArchTest`) pasan sin exclusiones espurias?
- [ ] **Atomicidad Outbox Verificada (`TRX-03`):** ¿Se verifica que el rollback transaccional aborta simultáneamente la mutación del agregado y la fila de outbox?
- [ ] **Test de Fuga entre Inquilinos Aprobado (`TST-08`):** ¿Se incluye el test que demuestra que el Tenant A no puede leer ni modificar datos del Tenant B?
- [ ] **Estructura AAA Semántica (`TST-09`):** ¿Los tests siguen el formato `should_<ExpectedBehavior>_when_<Condition>`?
- [ ] **Presupuesto CI Respetado (`TST-10`):** ¿La suite completa `./gradlew check` finaliza en menos de 5 minutos?
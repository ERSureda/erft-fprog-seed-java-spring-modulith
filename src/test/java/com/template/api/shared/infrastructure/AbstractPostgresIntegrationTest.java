package com.template.api.shared.infrastructure;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class AbstractPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractPostgresIntegrationTest.class);
    protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER;
    private static final boolean DOCKER_AVAILABLE;

    static {
        boolean available = false;
        PostgreSQLContainer<?> container = null;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
            if (available) {
                container = new PostgreSQLContainer<>("postgres:17-alpine")
                        .withDatabaseName("fprog_test")
                        .withUsername("test")
                        .withPassword("test")
                        .withReuse(true);
                container.start();
                log.info("PostgreSQL 17 Testcontainers singleton started at: {}", container.getJdbcUrl());
            } else {
                log.warn("Docker environment not detected. Integration tests extending AbstractPostgresIntegrationTest require Docker.");
            }
        } catch (Exception ex) {
            log.warn("Failed to initialize PostgreSQL Testcontainer: {}.", ex.getMessage());
            available = false;
            container = null;
        }
        DOCKER_AVAILABLE = available;
        POSTGRES_CONTAINER = container;
    }

    @BeforeAll
    static void ensureDockerRunning() {
        boolean running = DOCKER_AVAILABLE && POSTGRES_CONTAINER != null && POSTGRES_CONTAINER.isRunning();
        boolean strictMode = Boolean.parseBoolean(System.getenv("CI")) || Boolean.getBoolean("requireDocker");
        if (strictMode && !running) {
            Assertions.fail("TST-03 violation: Docker environment is required for PostgreSQL 17 Testcontainers in CI/strict mode.");
        }
        Assumptions.assumeTrue(
                running,
                "Skipping test: Docker environment is not available locally or PostgreSQL container is not running."
        );
    }

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES_CONTAINER != null && POSTGRES_CONTAINER.isRunning()) {
            registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
            registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.url", POSTGRES_CONTAINER::getJdbcUrl);
            registry.add("spring.flyway.user", POSTGRES_CONTAINER::getUsername);
            registry.add("spring.flyway.password", POSTGRES_CONTAINER::getPassword);
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        }
    }
}

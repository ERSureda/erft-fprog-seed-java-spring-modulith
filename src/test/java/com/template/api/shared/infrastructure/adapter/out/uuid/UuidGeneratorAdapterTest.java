package com.template.api.shared.infrastructure.adapter.out.uuid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UuidGeneratorAdapter Unit Tests")
class UuidGeneratorAdapterTest {

    private UuidGeneratorAdapter generator;

    @BeforeEach
    void setUp() {
        generator = new UuidGeneratorAdapter();
    }

    @Test
    @DisplayName("Should generate UUIDv7 compliant with RFC 9562 (version 7, variant 2)")
    void should_generate_valid_uuid_v7_when_generate_id_invoked() {
        UUID id = generator.generateId();

        assertThat(id).isNotNull();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should generate valid string representation via default port method")
    void should_generate_valid_string_when_generate_id_string_invoked() {
        String idString = generator.generateIdString();

        assertThat(idString).isNotBlank();
        UUID parsed = UUID.fromString(idString);
        assertThat(parsed.version()).isEqualTo(7);
        assertThat(parsed.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("Sequentially generated identifiers should be monotonically increasing")
    void should_generate_monotonically_increasing_uuids_when_generated_sequentially() {
        UUID previous = generator.generateId();

        for (int i = 0; i < 1_000; i++) {
            UUID current = generator.generateId();
            assertThat(current).isGreaterThan(previous);
            previous = current;
        }
    }

    @Test
    @DisplayName("Should ensure absolute uniqueness under high multi-threaded concurrency")
    void should_ensure_uniqueness_when_generated_under_high_concurrency() throws InterruptedException {
        int threads = 16;
        int operationsPerThread = 1_000;
        int totalExpectedIds = threads * operationsPerThread;

        Set<UUID> generatedIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threads);

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < operationsPerThread; i++) {
                            generatedIds.add(generator.generateId());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            finishLatch.await();
        }

        assertThat(generatedIds).hasSize(totalExpectedIds);
    }
}

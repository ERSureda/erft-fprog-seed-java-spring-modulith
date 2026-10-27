package com.template.api.shared.application.port.out;

import java.util.UUID;

public interface IdempotencyGate {

    boolean tryAcquire(String moduleSchema, UUID eventId, String consumerName);
    void release(String moduleSchema, UUID eventId, String consumerName);
    boolean isProcessed(String moduleSchema, UUID eventId);
    default boolean tryAcquire(UUID eventId, String consumerName) {
        return tryAcquire("shared", eventId, consumerName);
    }
    default boolean release(UUID eventId) {
        release("shared", eventId, null);
        return true;
    }
    default void release(UUID eventId, String consumerName) {
        release("shared", eventId, consumerName);
    }
    default boolean isProcessed(UUID eventId) {
        return isProcessed("shared", eventId);
    }
}

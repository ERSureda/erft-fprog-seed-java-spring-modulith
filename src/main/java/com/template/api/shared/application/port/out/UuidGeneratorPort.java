package com.template.api.shared.application.port.out;

import java.util.UUID;

public interface UuidGeneratorPort {

    UUID generateId();

    default String generateIdString() {
        return generateId().toString();
    }
}

package com.template.api.shared.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BaseEntity Unit Tests")
class BaseEntityTest {

    static class TestEntity extends BaseEntity<UUID> {
        TestEntity(UUID id) {
            super(id);
        }
    }

    static class AnotherEntity extends BaseEntity<UUID> {
        AnotherEntity(UUID id) {
            super(id);
        }
    }

    @Test
    @DisplayName("Should return id via id() and getId()")
    void should_return_id_when_accessors_invoked() {
        UUID id = UUID.randomUUID();
        TestEntity entity = new TestEntity(id);

        assertThat(entity.id()).isEqualTo(id);
        assertThat(entity.getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("Should be equal when comparing the same instance")
    void should_be_equal_when_comparing_same_instance() {
        TestEntity entity = new TestEntity(UUID.randomUUID());

        assertThat(entity.equals(entity)).isTrue();
    }

    @Test
    @DisplayName("Should be equal when IDs match and classes match")
    void should_be_equal_when_same_id_and_same_class() {
        UUID id = UUID.randomUUID();
        TestEntity e1 = new TestEntity(id);
        TestEntity e2 = new TestEntity(id);

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when IDs differ")
    void should_not_be_equal_when_ids_differ() {
        TestEntity e1 = new TestEntity(UUID.randomUUID());
        TestEntity e2 = new TestEntity(UUID.randomUUID());

        assertThat(e1).isNotEqualTo(e2);
    }

    @Test
    @DisplayName("Should not be equal when comparing with null or different class")
    void should_not_be_equal_when_compared_with_null_or_different_class() {
        UUID id = UUID.randomUUID();
        TestEntity e1 = new TestEntity(id);
        AnotherEntity e2 = new AnotherEntity(id);

        assertThat(e1.equals(null)).isFalse();
        assertThat(e1.equals("string")).isFalse();
        assertThat(e1.equals(e2)).isFalse();
    }

    @Test
    @DisplayName("Should throw NullPointerException when parameterized constructor receives null id")
    void should_throw_null_pointer_exception_when_id_is_null() {
        assertThatThrownBy(() -> new TestEntity(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("ENTITY_ID_CANNOT_BE_NULL");
    }
}

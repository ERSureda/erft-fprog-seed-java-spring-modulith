package com.template.api.shared.application.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CursorResult Unit Tests")
class CursorResultTest {

    record Item(String id, String name) {}

    @Test
    @DisplayName("Should create CursorResult with nextCursor and hasNext=true when items exceed limit")
    void should_create_cursor_result_when_extra_items_present() {
        List<Item> fetched = List.of(
                new Item("c1", "First"),
                new Item("c2", "Second"),
                new Item("c3", "Third (Extra)")
        );

        CursorResult<Item> result = CursorResult.of(fetched, 2, Item::id);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(1).id()).isEqualTo("c2");
        assertThat(result.nextCursor()).isEqualTo("c2");
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("Should create CursorResult with null nextCursor and hasNext=false when items do not exceed limit")
    void should_create_cursor_result_without_next_cursor_when_limit_not_exceeded() {
        List<Item> fetched = List.of(
                new Item("c1", "First"),
                new Item("c2", "Second")
        );

        CursorResult<Item> result = CursorResult.of(fetched, 2, Item::id);

        assertThat(result.items()).hasSize(2);
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("Should create CursorResult via canonical factory of(items, nextCursor)")
    void should_create_cursor_result_when_canonical_of_factory_invoked() {
        List<String> items = List.of("A", "B");
        CursorResult<String> withCursor = CursorResult.of(items, "cursor-123");
        assertThat(withCursor.items()).containsExactly("A", "B");
        assertThat(withCursor.nextCursor()).isEqualTo("cursor-123");
        assertThat(withCursor.hasNext()).isTrue();

        CursorResult<String> withoutCursor = CursorResult.of(items, null);
        assertThat(withoutCursor.nextCursor()).isNull();
        assertThat(withoutCursor.hasNext()).isFalse();

        CursorResult<String> explicitHasNext = CursorResult.of(items, "cursor-123", false);
        assertThat(explicitHasNext.hasNext()).isFalse();
    }

    @Test
    @DisplayName("Should handle empty and edge-case inputs gracefully")
    void should_handle_gracefully_when_inputs_are_empty_or_edge_cases() {
        assertThat(CursorResult.of(null, 10, Object::toString).items()).isEmpty();
        assertThat(CursorResult.of(null, 10, Object::toString).nextCursor()).isNull();
        assertThat(CursorResult.of(null, 10, Object::toString).hasNext()).isFalse();

        assertThat(CursorResult.of(List.of(), 10, Object::toString).items()).isEmpty();
        assertThat(CursorResult.of(List.of("a"), 0, Object::toString).items()).isEmpty();
        assertThat(CursorResult.of(List.of("a"), -1, Object::toString).items()).isEmpty();
    }

    @Test
    @DisplayName("Should make defensive copy of input list in constructor")
    void should_defend_immutability_when_mutable_list_passed() {
        List<Item> mutableList = new ArrayList<>();
        mutableList.add(new Item("c1", "One"));

        CursorResult<Item> result = new CursorResult<>(mutableList, "c1", true);
        mutableList.add(new Item("c2", "Two"));

        assertThat(result.items()).hasSize(1);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("Should handle null items in constructor gracefully")
    void should_initialize_empty_items_when_constructor_receives_null() {
        CursorResult<Item> nullItemsResult = new CursorResult<>(null, null);
        assertThat(nullItemsResult.items()).isNotNull();
        assertThat(nullItemsResult.items()).isEmpty();
        assertThat(nullItemsResult.nextCursor()).isNull();
        assertThat(nullItemsResult.hasNext()).isFalse();
    }

    @Test
    @DisplayName("Should transform items and preserve nextCursor and hasNext via map")
    void should_preserve_metadata_when_mapping_items() {
        CursorResult<Item> original = new CursorResult<>(
                List.of(new Item("c1", "Alice"), new Item("c2", "Bob")),
                "c2",
                true
        );

        CursorResult<String> mapped = original.map(Item::name);

        assertThat(mapped.items()).containsExactly("Alice", "Bob");
        assertThat(mapped.nextCursor()).isEqualTo("c2");
        assertThat(mapped.hasNext()).isTrue();
    }

    @Test
    @DisplayName("Should return empty items when mapping an empty CursorResult")
    void should_return_empty_items_when_mapping_empty_result() {
        CursorResult<Item> original = new CursorResult<>(List.of(), "c0", false);

        CursorResult<String> mapped = original.map(Item::name);

        assertThat(mapped.items()).isEmpty();
        assertThat(mapped.nextCursor()).isEqualTo("c0");
        assertThat(mapped.hasNext()).isFalse();
    }

    @Test
    @DisplayName("Should throw NullPointerException when mapper is null")
    void should_throw_exception_when_mapper_is_null() {
        CursorResult<Item> original = new CursorResult<>(List.of(new Item("c1", "Alice")), "c1");

        assertThatThrownBy(() -> original.map(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("CURSOR_RESULT_MAPPER_CANNOT_BE_NULL");
    }

    @Test
    @DisplayName("Should throw NullPointerException when cursorExtractor is null in of()")
    void should_throw_exception_when_cursor_extractor_is_null() {
        List<Item> fetched = List.of(new Item("c1", "One"));

        assertThatThrownBy(() -> CursorResult.of(fetched, 1, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("CURSOR_RESULT_EXTRACTOR_CANNOT_BE_NULL");
    }
}

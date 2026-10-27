package com.template.api.shared.application.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PageResult Unit Tests")
class PageResultTest {

    @Test
    @DisplayName("Should correctly calculate totalPages using of factory method")
    void should_calculate_total_pages_correctly_when_valid_data_provided() {
        PageResult<String> page1 = PageResult.of(List.of("a", "b"), 0, 10, 25);
        PageResult<String> page2 = PageResult.of(List.of("a"), 1, 10, 20);
        PageResult<String> page3 = PageResult.of(List.of(), 0, 10, 0);
        PageResult<String> page4 = PageResult.of(List.of("a"), 0, 0, 5);

        assertThat(page1.items()).containsExactly("a", "b");
        assertThat(page1.page()).isEqualTo(0);
        assertThat(page1.size()).isEqualTo(10);
        assertThat(page1.totalElements()).isEqualTo(25L);
        assertThat(page1.totalPages()).isEqualTo(3);

        assertThat(page2.totalPages()).isEqualTo(2);
        assertThat(page3.totalPages()).isEqualTo(0);
        assertThat(page4.totalPages()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should ensure items list is unmodifiable and null items default to empty list")
    void should_defend_immutability_when_items_list_accessed() {
        List<String> mutableList = new ArrayList<>();
        mutableList.add("item1");

        PageResult<String> result = new PageResult<>(mutableList, 0, 10, 1, 1);
        mutableList.add("item2");

        assertThat(result.items()).containsExactly("item1");
        assertThatThrownBy(() -> result.items().add("item3"))
                .isInstanceOf(UnsupportedOperationException.class);

        PageResult<String> nullItemsResult = new PageResult<>(null, 0, 10, 0, 0);
        assertThat(nullItemsResult.items()).isEmpty();
    }

    @Test
    @DisplayName("Should map items preserving pagination metadata")
    void should_preserve_metadata_when_mapping_elements() {
        PageResult<Integer> numbers = PageResult.of(List.of(1, 2, 3), 1, 3, 10);

        PageResult<String> mapped = numbers.map(n -> "Value: " + n);

        assertThat(mapped.items()).containsExactly("Value: 1", "Value: 2", "Value: 3");
        assertThat(mapped.page()).isEqualTo(1);
        assertThat(mapped.size()).isEqualTo(3);
        assertThat(mapped.totalElements()).isEqualTo(10L);
        assertThat(mapped.totalPages()).isEqualTo(4);
    }

    @Test
    @DisplayName("Should return empty items when mapping an empty PageResult")
    void should_return_empty_result_when_mapping_empty_items() {
        PageResult<Integer> empty = PageResult.of(List.of(), 0, 10, 0);

        PageResult<String> mapped = empty.map(Object::toString);

        assertThat(mapped.items()).isEmpty();
        assertThat(mapped.totalPages()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should reject null mapper function")
    void should_throw_null_pointer_exception_when_mapper_is_null() {
        PageResult<Integer> numbers = PageResult.of(List.of(1), 0, 10, 1);

        assertThatThrownBy(() -> numbers.map(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("PAGE_RESULT_MAPPER_CANNOT_BE_NULL");
    }
}

package com.template.api.shared.application.result;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record PageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public PageResult {
        items = (items == null || items.isEmpty()) ? List.of() : List.copyOf(items);
    }

    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = (size > 0 && totalElements > 0) ? (int) Math.ceilDiv(totalElements, (long) size) : 0;
        return new PageResult<>(items, page, size, totalElements, totalPages);
    }

    public <U> PageResult<U> map(Function<? super T, U> mapper) {
        Objects.requireNonNull(mapper, "mapper cannot be null");
        if (items.isEmpty()) {
            return new PageResult<>(List.of(), page, size, totalElements, totalPages);
        }
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }
}

package com.template.api.shared.application.result;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record CursorResult<T>(
        List<T> items,
        String nextCursor,
        boolean hasNext
) {

    public CursorResult {
        items = (items == null || items.isEmpty()) ? List.of() : List.copyOf(items);
    }

    public CursorResult(List<T> items, String nextCursor) {
        this(items, nextCursor, nextCursor != null && !nextCursor.isBlank());
    }

    public static <T> CursorResult<T> of(List<T> items, String nextCursor) {
        return new CursorResult<>(items, nextCursor);
    }

    public static <T> CursorResult<T> of(List<T> items, String nextCursor, boolean hasNext) {
        return new CursorResult<>(items, nextCursor, hasNext);
    }

    public static <T> CursorResult<T> of(
            List<T> itemsWithExtra,
            int limit,
            Function<? super T, String> cursorExtractor
    ) {
        if (itemsWithExtra == null || itemsWithExtra.isEmpty() || limit <= 0) {
            return new CursorResult<>(List.of(), null, false);
        }
        Objects.requireNonNull(cursorExtractor, "CURSOR_RESULT_EXTRACTOR_CANNOT_BE_NULL");

        boolean hasMore = itemsWithExtra.size() > limit;
        List<T> content = hasMore ? itemsWithExtra.subList(0, limit) : itemsWithExtra;
        String nextCursor = hasMore ? cursorExtractor.apply(content.get(content.size() - 1)) : null;

        return new CursorResult<>(content, nextCursor, hasMore);
    }

    public <U> CursorResult<U> map(Function<? super T, U> mapper) {
        Objects.requireNonNull(mapper, "CURSOR_RESULT_MAPPER_CANNOT_BE_NULL");
        if (items.isEmpty()) {
            return new CursorResult<>(List.of(), nextCursor, hasNext);
        }
        return new CursorResult<>(items.stream().map(mapper).toList(), nextCursor, hasNext);
    }
}

package com.orderflow.events;

import java.util.List;
import java.util.function.Function;

/**
 * Standard paginated response shape used by every list endpoint.
 * Built explicitly rather than returning Spring's Page<T> so the API contract is stable.
 */
public record PageResponse<T>(List<T> content, int pageNumber, int pageSize,
                              long totalElements, int totalPages, boolean last) {

    public static <E, T> PageResponse<T> from(org.springframework.data.domain.Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}

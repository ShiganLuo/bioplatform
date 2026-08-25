package com.bioplatform.dto.common;

import java.util.List;

/**
 * Paginated result wrapper.
 *
 * @param <T> the type of elements in the list
 */
public record PageResult<T>(long total, int page, int size, List<T> records) {

    public static <T> PageResult<T> of(long total, int page, int size, List<T> records) {
        return new PageResult<>(total, page, size, records);
    }
}

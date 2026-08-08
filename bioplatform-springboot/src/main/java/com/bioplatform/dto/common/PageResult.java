package com.bioplatform.dto.common;

import java.util.List;

/**
 * Paginated result wrapper.
 *
 * @param <T> the type of elements in the list
 */
public record PageResult<T>(long total, int pageNum, int pageSize, List<T> list) {

    public static <T> PageResult<T> of(long total, int pageNum, int pageSize, List<T> list) {
        return new PageResult<>(total, pageNum, pageSize, list);
    }
}

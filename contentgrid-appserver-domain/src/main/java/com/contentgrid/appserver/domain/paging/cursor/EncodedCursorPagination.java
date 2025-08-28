package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorContext;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.hateoas.pagination.api.Pagination;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class EncodedCursorPagination implements Pagination {

    private final String cursor;

    private final int size;

    @NonNull
    private final SortData sort;

    public CursorContext getCursorContext() {
        return new CursorContext(cursor, size, sort);
    }

    @Override
    public Integer getLimit() {
        return size;
    }

    @Override
    public Optional<?> getReference() {
        return Optional.ofNullable(cursor);
    }

    @Override
    public boolean isFirstPage() {
        return cursor == null;
    }

    @Override
    public Map<String, Object> getParameters() {
        // The purpose is to know the uri query parameter names and values, but the names are defined in rest layer
        throw new UnsupportedOperationException("EncodedCursorPagination can not be used with default PaginationHandlerMethodArgumentResolver. Use EncodedCursorPaginationHandlerMethodArgumentResolver instead.");
    }
}

package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorContext;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.hateoas.pagination.api.Pagination;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EncodedCursorPagination implements Pagination {
    public static final int PAGE_SIZE = 20;

    @Getter
    private final String cursor;

    @Getter
    private final SortData sort;

    public CursorContext getCursorContext() {
        return new CursorContext(cursor, PAGE_SIZE, sort);
    }

    @Override
    public Integer getLimit() {
        return PAGE_SIZE;
    }

    @Override
    public Optional<?> getReference() {
        return Optional.of(cursor);
    }

    @Override
    public boolean isFirstPage() {
        return cursor.endsWith("0");
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("cursor", cursor, "size", PAGE_SIZE, "sort", sort);
    }
}

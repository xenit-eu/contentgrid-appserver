package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorContext;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.hateoas.pagination.api.Pagination;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class EncodedCursorPagination implements Pagination {
    public static final int PAGE_SIZE = 20;

    private final String cursor;

    private final int size;

    @NonNull
    private final SortData sort;

    public EncodedCursorPagination(String cursor) {
        this(cursor, PAGE_SIZE);
    }

    public EncodedCursorPagination(String cursor, int size) {
        this(cursor, size, new SortData(List.of()));
    }

    public EncodedCursorPagination(String cursor, SortData sort) {
        this(cursor, PAGE_SIZE, sort);
    }

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
        var namingStrategy = new EncodedCursorPaginationNamingStrategy();
        var result = new HashMap<String, Object>();
        result.put(namingStrategy.getSizeName(), size);

        // Empty collections remove the query parameter
        result.put(namingStrategy.getPageName(), Optional.ofNullable(cursor).stream().toList());
        result.put(namingStrategy.getSortName(), sort.toList());

        return result;
    }
}

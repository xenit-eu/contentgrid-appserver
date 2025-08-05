package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorContext;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.hateoas.pagination.api.Pagination;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.StandardException;

@Getter
@RequiredArgsConstructor
public class EncodedCursorPagination implements Pagination {
    public static final int PAGE_SIZE = 20;

    private final String cursor;

    private final int size;

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
        return Optional.of(cursor);
    }

    @Override
    public boolean isFirstPage() {
        throw new EncodedCursorException("Cursor is encoded, decode it first with CursorCodec#decodeCursor");
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("cursor", cursor, "size", size, "sort", sort);
    }

    @StandardException
    public static class EncodedCursorException extends RuntimeException {

    }
}

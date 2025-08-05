package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorContext;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.hateoas.pagination.api.Pagination;
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

    private final Integer limit;

    private final SortData sort;

    public CursorContext getCursorContext() {
        return new CursorContext(cursor, limit == null ? PAGE_SIZE : limit, sort);
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
        return Map.of("cursor", cursor, "size", limit, "sort", sort);
    }

    @StandardException
    public static class EncodedCursorException extends RuntimeException {

    }
}

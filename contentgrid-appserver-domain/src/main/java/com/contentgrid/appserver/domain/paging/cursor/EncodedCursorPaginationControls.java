package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.hateoas.pagination.api.Pagination;
import com.contentgrid.hateoas.pagination.api.PaginationControls;
import java.util.Optional;
import lombok.NonNull;
import lombok.Value;

@Value
public class EncodedCursorPaginationControls implements PaginationControls {

    @NonNull
    EncodedCursorPagination pagination;

    EncodedCursorPagination next;
    EncodedCursorPagination previous;

    @NonNull
    EncodedCursorPagination first;

    @Override
    public Pagination current() {
        return pagination;
    }

    @Override
    public Optional<Pagination> next() {
        return Optional.ofNullable(this.next);
    }

    @Override
    public Optional<Pagination> previous() {
        return Optional.ofNullable(this.previous);
    }

    @Override
    public Pagination first() {
        return first;
    }
}

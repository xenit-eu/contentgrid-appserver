package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.hateoas.pagination.api.Pagination;
import com.contentgrid.hateoas.pagination.api.PaginationControls;
import java.util.Optional;
import java.util.function.Function;
import lombok.NonNull;
import lombok.Value;

@Value
public class EncodedCursorPaginationControls implements PaginationControls {

    @NonNull
    PaginationControls delegate;

    @NonNull
    Function<Pagination, EncodedCursorPagination> encoder;

    @Override
    public Pagination current() {
        return encoder.apply(delegate.current());
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public Optional<Pagination> next() {
        return delegate.next().map(encoder);
    }

    @Override
    public boolean hasPrevious() {
        return delegate.hasPrevious();
    }

    @Override
    public Optional<Pagination> previous() {
        return delegate.previous().map(encoder);
    }

    @Override
    public Pagination first() {
        return encoder.apply(delegate.first());
    }
}

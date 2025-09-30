package com.contentgrid.appserver.domain.paging;

import com.contentgrid.hateoas.pagination.api.Pagination;
import com.contentgrid.hateoas.pagination.api.PaginationControls;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PageBasedPaginationControls implements PaginationControls {

    @Getter
    private final int size;
    @Getter
    private final int page;

    @Getter
    @Accessors(fluent = true)
    private final boolean hasNext;

    public static PageBasedPaginationControls forPagination(PageBasedPagination pagination, boolean hasNext) {
        return new PageBasedPaginationControls(pagination.getSize(), pagination.getPage(), hasNext);
    }

    @Override
    public Pagination current() {
        return new PageBasedPagination(size, page);
    }

    @Override
    public Optional<Pagination> next() {
        if (!hasNext()) {
            return Optional.empty();
        }
        return Optional.of(new PageBasedPagination(size, page + 1));
    }

    @Override
    public boolean hasPrevious() {
        return page > 0;
    }

    @Override
    public Optional<Pagination> previous() {
        if (!hasPrevious()) {
            return Optional.empty();
        }
        return Optional.of(new PageBasedPagination(size, page - 1));
    }

    @Override
    public Pagination first() {
        return new PageBasedPagination(size, 0);
    }
}

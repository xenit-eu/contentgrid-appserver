package com.contentgrid.appserver.domain.paging;

import com.contentgrid.hateoas.pagination.api.Pagination;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public class PageBasedPagination implements Pagination {
    @Getter
    private final int size;
    @Getter
    private final int page;


    @Override
    public Integer getLimit() {
        return size;
    }

    @Override
    public Optional<?> getReference() {
        return Optional.of(page);
    }

    @Override
    public boolean isFirstPage() {
        return page == 0;
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("page", page, "size", size);
    }
}

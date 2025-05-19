package com.contentgrid.appserver.query;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
// Placeholder class: to be replaced, see contentgrid-spring-data-pagination for options
public class ItemCountPageImpl<T> implements ItemCountPage<T> {

    private final List<T> results;
    private final ItemCount totalItemCount;

    @Override
    public int getPageSize() {
        return 20;
    }

    @Override
    public PageRequest getNextPage() {
        return new PageRequest(20, 1);
    }
}

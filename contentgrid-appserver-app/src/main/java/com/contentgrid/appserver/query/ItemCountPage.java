package com.contentgrid.appserver.query;

import java.util.List;

// Placeholder class: to be replaced, see contentgrid-spring-data-pagination for options
public interface ItemCountPage<T> {
    List<T> getResults();
    ItemCount getTotalItemCount();
    int getPageSize();
    PageRequest getNextPage();

    record ItemCount(long count, boolean exact) {}
}

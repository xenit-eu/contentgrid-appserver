package com.contentgrid.appserver.query;

import lombok.Value;

@Value
// Placeholder class: to be replaced, see contentgrid-spring-data-pagination for options
public class PageRequest {
    public static final int DEFAULT_PAGE_SIZE = 20;

    int pageSize;
    int page;

    public static PageRequest ofPage(int page) {
        return new PageRequest(DEFAULT_PAGE_SIZE, page);
    }
}

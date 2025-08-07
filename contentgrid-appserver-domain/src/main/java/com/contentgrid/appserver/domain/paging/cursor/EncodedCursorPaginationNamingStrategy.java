package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.domain.paging.PaginationNamingStrategy;

public class EncodedCursorPaginationNamingStrategy implements PaginationNamingStrategy {

    @Override
    public String getPageName() {
        return "_cursor";
    }

    @Override
    public String getSizeName() {
        return "_size";
    }

    @Override
    public String getSortName() {
        return "_sort";
    }
}

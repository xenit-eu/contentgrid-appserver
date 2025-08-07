package com.contentgrid.appserver.domain.paging;

import java.util.Set;

public interface PaginationNamingStrategy {

    String getPageName();
    String getSizeName();
    String getSortName();

    default Set<String> getParameters() {
        return Set.of(getPageName(), getSizeName(), getSortName());
    }

}

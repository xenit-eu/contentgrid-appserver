package com.contentgrid.appserver.application.model.searchfilters.flags;

import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;

public interface SearchFilterFlag {

    void checkSupported(SearchFilter searchFilter);
}

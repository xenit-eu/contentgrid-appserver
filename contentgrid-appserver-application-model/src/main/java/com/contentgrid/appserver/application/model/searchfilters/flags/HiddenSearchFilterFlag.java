package com.contentgrid.appserver.application.model.searchfilters.flags;

import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HiddenSearchFilterFlag implements SearchFilterFlag {

    public static final HiddenSearchFilterFlag INSTANCE = new HiddenSearchFilterFlag();

    @Override
    public void checkSupported(SearchFilter searchFilter) {

    }
}

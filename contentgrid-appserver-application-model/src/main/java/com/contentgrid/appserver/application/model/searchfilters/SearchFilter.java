package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.values.FilterName;

/**
 * Represents a search filter that can be applied to entity queries.
 * 
 * Search filters define how entities can be searched or filtered based on specific criteria.
 */
public interface SearchFilter {

    /**
     * Gets the name of the search filter.
     * 
     * @return the name of the search filter
     */
    FilterName getName();

}

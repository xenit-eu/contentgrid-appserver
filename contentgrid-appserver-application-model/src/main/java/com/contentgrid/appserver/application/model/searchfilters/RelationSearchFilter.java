package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.FilterName;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * SearchFilter across a relation. This is not part of the json representation of the application model, but rather
 * generated at load time by calling .propagateSearchFilters() on the Application.
 *
 * Wraps a SearchFilter from a target entity and makes it available on the source entity.
 */
@Value
@Builder
public class RelationSearchFilter implements SearchFilter {

    @NonNull
    FilterName name;

    @NonNull
    Relation relation;

    @NonNull
    SearchFilter wrappedFilter;
}

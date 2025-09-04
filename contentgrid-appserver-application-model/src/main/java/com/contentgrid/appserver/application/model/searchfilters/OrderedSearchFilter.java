package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import java.util.Set;
import lombok.NonNull;

public abstract sealed class OrderedSearchFilter extends AttributeSearchFilter permits
        GreaterThanSearchFilter, GreaterThanOrEqualsSearchFilter, LessThanSearchFilter, LessThanOrEqualsSearchFilter {

    private static final Set<Type> SUPPORTED_TYPES = Set.of(Type.LONG, Type.DOUBLE, Type.DATETIME);

    /**
     * Constructs an OrderedSearchFilter with the specified parameters.
     *
     * @param name the name of the search filter
     * @param attributePath the path to the attribute to apply the filter on
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    protected OrderedSearchFilter(@NonNull FilterName name,
            @NonNull PropertyPath attributePath,
            @NonNull Set<SearchFilterFlag> flags) {
        super(name, attributePath, flags);
    }

    @Override
    public boolean supports(SimpleAttribute attribute) {
        return SUPPORTED_TYPES.contains(attribute.getType());
    }
}

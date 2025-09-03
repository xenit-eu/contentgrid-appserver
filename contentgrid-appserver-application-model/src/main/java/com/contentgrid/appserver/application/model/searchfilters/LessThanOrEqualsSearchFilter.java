package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

/**
 * A search filter that performs a less than or equals operation on attribute values.
 * <p>
 * This filter can be used to find entities where an attribute is less than or equal to a specified value.
 * It supports all numeric and date-time attribute types.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class LessThanOrEqualsSearchFilter extends OrderedSearchFilter {

    /**
     * Constructs a LessThanOrEqualsSearchFilter with the specified parameters.
     *
     * @param name the name of the search filter
     * @param attributePath the path to the attribute to apply the filter on
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    @Builder
    LessThanOrEqualsSearchFilter(
            @NonNull FilterName name,
            @NonNull PropertyPath attributePath,
            @NonNull @Singular Set<SearchFilterFlag> flags
    ) throws InvalidSearchFilterException {
        super(name, attributePath, flags);
    }

    public static class LessThanOrEqualsSearchFilterBuilder {
        public LessThanOrEqualsSearchFilterBuilder attribute(@NonNull SimpleAttribute attribute) {
            this.attributePath = PropertyPath.of(attribute.getName());
            return this;
        }
    }
}

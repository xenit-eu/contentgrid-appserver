package com.contentgrid.appserver.application.model.exceptions;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.searchfilters.CompositeAttributeSearchFilter;
import lombok.NonNull;

import java.util.Collection;

public class CompositeSearchFilterAttributesWithDifferentTypesException extends InvalidSearchFilterException {


    public CompositeSearchFilterAttributesWithDifferentTypesException(@NonNull CompositeAttributeSearchFilter compositeAttributeSearchFilter,
                                                                      @NonNull Collection<SimpleAttribute.Type> attributeTypes) {
        super(("CompositeAttributeSearchFilter (%s) is defining a search over multiple attribute types, which is not supported " +
                "(provided types: (%s)).").formatted(compositeAttributeSearchFilter.getName().getValue(),
                    String.join(", ", attributeTypes.stream().map(SimpleAttribute.Type::name).toList())));
    }

}

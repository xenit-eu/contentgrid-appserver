package com.contentgrid.appserver.application.model.exceptions;

import com.contentgrid.appserver.application.model.searchfilters.CompositeAttributeSearchFilter;
import lombok.NonNull;

public class CompositeSearchFilterWithoutAttributesException extends InvalidSearchFilterException {


    public CompositeSearchFilterWithoutAttributesException(@NonNull CompositeAttributeSearchFilter compositeAttributeSearchFilter) {
        super("CompositeAttributeSearchFilter (%s) must have at least one attribute defined.".formatted(compositeAttributeSearchFilter.getName().getValue()));
    }

}

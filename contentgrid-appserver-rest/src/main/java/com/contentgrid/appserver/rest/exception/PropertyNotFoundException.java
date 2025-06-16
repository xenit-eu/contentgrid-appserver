package com.contentgrid.appserver.rest.exception;

import com.contentgrid.appserver.application.model.values.PathSegmentName;

public class PropertyNotFoundException extends RuntimeException {

    public PropertyNotFoundException(PathSegmentName name) {
        super("Property path with name '%s' not found".formatted(name));
    }

}

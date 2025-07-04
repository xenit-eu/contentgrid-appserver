package com.contentgrid.appserver.rest.exception;

import com.contentgrid.appserver.application.model.values.PathSegmentName;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PropertyNotFoundException extends ResponseStatusException {

    public PropertyNotFoundException(PathSegmentName name) {
        super(HttpStatus.NOT_FOUND, "Property path with name '%s' not found".formatted(name));
        getBody().setProperty("property", name.getValue());
    }

}

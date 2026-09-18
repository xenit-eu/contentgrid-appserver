package com.contentgrid.appserver.domain.values.exception;

import com.contentgrid.appserver.application.model.exceptions.ApplicationModelException;
import lombok.experimental.StandardException;
import com.contentgrid.appserver.domain.values.LinkRequest;

/**
 * Exception thrown when a referenced Link is not found.
 */
@StandardException
public class LinkNotFoundException extends ApplicationModelException {

    public LinkNotFoundException(LinkRequest linkRequest) {
        this("Link '%s' not found".formatted(linkRequest));
    }
}

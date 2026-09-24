package com.contentgrid.appserver.domain.values.exception;

import com.contentgrid.appserver.application.model.exceptions.ApplicationModelException;
import lombok.Getter;
import lombok.NonNull;
import com.contentgrid.appserver.domain.values.LinkRequest;

/**
 * Exception thrown when a referenced Link is not found.
 */
@Getter
public class LinkNotFoundException extends ApplicationModelException {

    @NonNull
    private final LinkRequest linkRequest;

    public LinkNotFoundException(@NonNull LinkRequest linkRequest) {
        super("Link '%s' not found".formatted(linkRequest));
        this.linkRequest = linkRequest;
    }
}

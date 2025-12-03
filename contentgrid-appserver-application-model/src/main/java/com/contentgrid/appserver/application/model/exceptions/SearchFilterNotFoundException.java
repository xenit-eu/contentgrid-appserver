package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an entity is missing a search filter.
 */
public class SearchFilterNotFoundException extends ApplicationModelException {

    /**
     * Constructs an SearchFilterNotFoundException with the specified message.
     *
     * @param message the detail message
     */
    public SearchFilterNotFoundException(String message) {
        super(message);
    }
}
package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when a referenced relation is not found.
 */
public class RelationNotFoundException extends ApplicationModelException {

    /**
     * Constructs an RelationNotFoundException with the specified message.
     *
     * @param message the detail message
     */
    public RelationNotFoundException(String message) {
        super(message);
    }
}
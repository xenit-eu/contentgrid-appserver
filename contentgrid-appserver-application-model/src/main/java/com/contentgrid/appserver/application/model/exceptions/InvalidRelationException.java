package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an invalid relation is defined.
 */
public class InvalidRelationException extends ApplicationModelException {
    
    /**
     * Constructs an InvalidRelationException with the specified message.
     *
     * @param message the detail message
     */
    public InvalidRelationException(String message) {
        super(message);
    }
}
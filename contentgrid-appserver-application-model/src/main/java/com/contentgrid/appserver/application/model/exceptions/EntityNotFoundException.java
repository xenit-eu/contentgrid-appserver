package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when a referenced entity is not found.
 */
public class EntityNotFoundException extends ApplicationModelException {
    
    /**
     * Constructs an EntityNotFoundException with the specified message.
     *
     * @param message the detail message
     */
    public EntityNotFoundException(String message) {
        super(message);
    }
}
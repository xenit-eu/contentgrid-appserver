package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when a referenced entity is not found.
 */
public class EntityNotFoundException extends ApplicationModelException {
    
    public EntityNotFoundException(String message) {
        super(message);
    }
}
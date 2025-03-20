package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an invalid relation is defined.
 */
public class InvalidRelationException extends ApplicationModelException {
    
    public InvalidRelationException(String message) {
        super(message);
    }
}
package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an invalid constraint is defined.
 */
public class InvalidConstraintException extends ApplicationModelException {
    
    public InvalidConstraintException(String message) {
        super(message);
    }
}
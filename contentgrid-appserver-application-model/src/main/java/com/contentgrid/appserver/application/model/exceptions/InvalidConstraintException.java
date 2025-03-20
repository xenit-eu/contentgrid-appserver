package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an invalid constraint is defined.
 */
public class InvalidConstraintException extends ApplicationModelException {
    
    /**
     * Constructs an InvalidConstraintException with the specified message.
     *
     * @param message the detail message
     */
    public InvalidConstraintException(String message) {
        super(message);
    }
}
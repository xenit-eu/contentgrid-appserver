package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an invalid argument is provided to a model class.
 */
public class InvalidArgumentModelException extends ApplicationModelException {
    
    /**
     * Constructs an InvalidArgumentModelException with the specified message.
     *
     * @param message the detail message
     */
    public InvalidArgumentModelException(String message) {
        super(message);
    }
}
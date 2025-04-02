package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an invalid search filter is defined.
 */
public class InvalidSearchFilterException extends ApplicationModelException {
    
    /**
     * Constructs an InvalidSearchFilterException with the specified message.
     *
     * @param message the detail message
     */
    public InvalidSearchFilterException(String message) {
        super(message);
    }
}
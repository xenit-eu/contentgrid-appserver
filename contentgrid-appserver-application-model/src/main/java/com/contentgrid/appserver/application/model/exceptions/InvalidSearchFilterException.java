package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an invalid search filter is defined.
 */
public class InvalidSearchFilterException extends ApplicationModelException {
    
    public InvalidSearchFilterException(String message) {
        super(message);
    }
}
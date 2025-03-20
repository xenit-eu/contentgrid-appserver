package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an invalid argument is provided to a model class.
 */
public class InvalidArgumentModelException extends ApplicationModelException {
    
    public InvalidArgumentModelException(String message) {
        super(message);
    }
}
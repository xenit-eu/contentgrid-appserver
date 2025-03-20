package com.contentgrid.appserver.application.model.exceptions;

/**
 * Base exception for all application model related exceptions.
 */
public class ApplicationModelException extends RuntimeException {
    
    public ApplicationModelException(String message) {
        super(message);
    }
    
    public ApplicationModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
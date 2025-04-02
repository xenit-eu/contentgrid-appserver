package com.contentgrid.appserver.application.model.exceptions;

/**
 * Base exception for all application model related exceptions.
 */
public class ApplicationModelException extends RuntimeException {
    
    /**
     * Constructs an ApplicationModelException with the specified message.
     *
     * @param message the detail message
     */
    public ApplicationModelException(String message) {
        super(message);
    }
    
    /**
     * Constructs an ApplicationModelException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public ApplicationModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
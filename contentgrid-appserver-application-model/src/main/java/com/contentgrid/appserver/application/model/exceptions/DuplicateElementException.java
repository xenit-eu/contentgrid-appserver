package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when a duplicate element is found.
 */
public class DuplicateElementException extends ApplicationModelException {
    
    /**
     * Constructs a DuplicateElementException with the specified message.
     *
     * @param message the detail message
     */
    public DuplicateElementException(String message) {
        super(message);
    }
}
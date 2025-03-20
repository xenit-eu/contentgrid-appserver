package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when a duplicate element is found.
 */
public class DuplicateElementException extends ApplicationModelException {
    
    public DuplicateElementException(String message) {
        super(message);
    }
}
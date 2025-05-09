package com.contentgrid.appserver.query.engine.api.exception;

public class ConstraintViolationException extends QueryEngineException {

    public ConstraintViolationException(String message) {
        super(message);
    }

    public ConstraintViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}

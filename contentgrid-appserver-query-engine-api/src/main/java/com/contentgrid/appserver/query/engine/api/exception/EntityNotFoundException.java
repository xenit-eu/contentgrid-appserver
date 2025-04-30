package com.contentgrid.appserver.query.engine.api.exception;

public class EntityNotFoundException extends QueryEngineException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

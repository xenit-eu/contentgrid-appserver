package com.contentgrid.appserver.query.engine.api.exception;

public class InvalidDataException extends QueryEngineException {

    public InvalidDataException(String message) {
        super(message);
    }

    public InvalidDataException(String message, Throwable cause) {
        super(message, cause);
    }
}

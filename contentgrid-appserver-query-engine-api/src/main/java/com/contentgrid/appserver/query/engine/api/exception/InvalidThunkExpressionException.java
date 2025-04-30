package com.contentgrid.appserver.query.engine.api.exception;

public class InvalidThunkExpressionException extends QueryEngineException {

    public InvalidThunkExpressionException(String message) {
        super(message);
    }

    public InvalidThunkExpressionException(String message, Throwable cause) {
        super(message, cause);
    }
}

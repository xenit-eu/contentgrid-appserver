package com.contentgrid.appserver.query.engine.api.exception;

public class InvalidSqlException extends QueryEngineException {

    public InvalidSqlException(String message) {
        super(message);
    }

    public InvalidSqlException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.contentgrid.appserver.query.engine.api.exception;

/**
 * This exception is thrown when data provided as input into the query engine is not valid.
 * <p>
 * An exception of this type being thrown <b>always</b> indicates programmer error,
 * and is never thrown when the problem is caused by user-supplied data.
 */
public class InvalidDataException extends IllegalArgumentException {

    public InvalidDataException(String message) {
        super(message);
    }

    public InvalidDataException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.contentgrid.appserver.query.engine.api.exception;

/**
 * This exception is thrown when data provided as input into the query engine is not valid.
 * <p>
 * An exception of this type being thrown <b>always</b> indicates programmer error,
 * and is never thrown when the problem is caused by user-supplied data.
 */
public class IllegalInputDataException extends IllegalArgumentException {

    public IllegalInputDataException(String message) {
        super(message);
    }

}

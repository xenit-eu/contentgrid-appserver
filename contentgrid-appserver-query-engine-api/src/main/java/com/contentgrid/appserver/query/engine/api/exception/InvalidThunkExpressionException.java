package com.contentgrid.appserver.query.engine.api.exception;

/**
 * This exception is thrown when a provided {@link com.contentgrid.thunx.predicates.model.ThunkExpression} is can not be converted to an SQL expression.
 * <p>
 * This exception usually indicates an error in the permission configuration (or in the application model).
 * As such, this exception <b>always</b> indicates programmer error.
 * This exception is never thrown when the problem is caused by user-supplied data.
 */
public class InvalidThunkExpressionException extends IllegalArgumentException {

    public InvalidThunkExpressionException(String message) {
        super(message);
    }

    public InvalidThunkExpressionException(String message, Throwable cause) {
        super(message, cause);
    }
}

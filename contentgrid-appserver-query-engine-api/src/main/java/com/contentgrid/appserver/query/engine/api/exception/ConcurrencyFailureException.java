package com.contentgrid.appserver.query.engine.api.exception;

import lombok.NonNull;

/**
 * Exception thrown when an error occurs due to multiple concurrent operations
 * <p>
 * This exception is only thrown as a last-resort, after a retry of the operation did not clear up the error condition.
 * The caller <b>must not</b> perform additional retries after receiving this exception.
 * <p>
 * @implNote Internally, this exception may be thrown immediately, to signal a higher-level component to retry the transaction.
 */
public class ConcurrencyFailureException extends QueryEngineException {
    public ConcurrencyFailureException(@NonNull Throwable cause) {
        super(cause);
    }
}

package com.contentgrid.appserver.domain.data.mapper;

import java.util.concurrent.Callable;

/**
 * Manager for interactions with external systems, where a cleanup is required in case of a failed database transaction.
 * <p>
 * A typical usage pattern is:
 * {@code
 *      ExternalInteractionManager im;
 *      try {
 *          im.performInteractions();
 *          var result = callYourService();
 *          im.commitInteractions();
 *      } finally {
 *          im.rollbackUncommittedInteractions();
 *      }
 * }
 */
public interface ExternalInteractionManager {

    /**
     * Perform the necessary interactions with an external system
     */
    void performInteractions();

    /**
     * Commit the interactions with the external system, making them no longer eligible for rollback
     */
    void commitInteractions();

    /**
     * Roll back the interactions that were not committed
     */
    void rollbackUncommittedInteractions();

    /**
     * Simplified interactions around a callable that throw only one kind of exception
     *
     * @param internalExecution The internal callable around which {@link #performInteractions()} and {@link #commitInteractions()} is called
     * @return The return value of calling {@code internalExecution}
     * @param <T> The return type of {@code internalExecution}
     * @param <E> The checked exception type of {@code internalExecution}
     */
    default <T, E extends Exception> T ensureInteractions(ThrowingCallable<T, E> internalExecution) throws E {
        try {
            performInteractions();
            var result = internalExecution.call();
            // All uploads have completed and were handled by internalExecution
            // This ensures that cancelUploads() only cancels uploads in case an exception was thrown above
            commitInteractions();
            return result;
        } finally {
            rollbackUncommittedInteractions();
        }
    }

    interface ThrowingCallable<V, E extends Exception> extends Callable<V> {
        @Override
        V call() throws E;
    }
}

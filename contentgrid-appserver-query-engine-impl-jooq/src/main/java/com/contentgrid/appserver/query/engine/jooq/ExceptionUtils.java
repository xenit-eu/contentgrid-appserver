package com.contentgrid.appserver.query.engine.jooq;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ExceptionUtils {

    /**
     * Create multiple exceptions for a list of items
     * <p>
     * The first exception will be thrown, and all additional exceptions will be added as suppressed exceptions
     * @param items Items to create exceptions for
     * @param exceptionFactory Function that creates an exception for an item. No exception will be created for a particular item if the factory returns <code>null</code>
     * @return The first exception to throw, if one is available
     * @param <T> Type of an item
     * @param <X> The exception type that will be created
     */
    public static <T, X extends Exception> Optional<X> createMultiple(@NonNull Iterable<T> items, @NonNull Function<T, X> exceptionFactory) {
        return createMultiple(items.iterator(), exceptionFactory);
    }

    /**
     * Create multiple exceptions from a stream of items
     * <p>
     * The first exception will be thrown, and all additional exceptions will be added as suppressed exceptions
     * @param items Stream of items to create exceptions for
     * @param exceptionFactory Function that creates an exception for an item. No exception will be created for a particular item if the factory returns <code>null</code>
     * @return The first exception to throw, if one is available
     * @param <T> Type of an item
     * @param <X> The exception type that will be created
     */
    public static <T, X extends Exception> Optional<X> createMultiple(@NonNull Stream<T> items, @NonNull Function<T, X> exceptionFactory) {
        return createMultiple(items.iterator(), exceptionFactory);
    }

    /**
     * Create multiple exceptions for an iterator containing items
     * <p>
     * The first exception will be thrown, and all additional exceptions will be added as suppressed exceptions
     * @param items Iterator returning items to create exceptions for
     * @param exceptionFactory Function that creates an exception for an item. No exception will be created for a particular item if the factory returns <code>null</code>
     * @return The first exception to throw, if one is available
     * @param <T> Type of an item
     * @param <X> The exception type that will be created
     */
    public static <T, X extends Exception> Optional<X> createMultiple(@NonNull Iterator<T> items, @NonNull Function<T, X> exceptionFactory) {
        X firstException = null;
        while(items.hasNext()) {
            var item = items.next();
            var ex = exceptionFactory.apply(item);
            if(firstException == null) {
                firstException = ex;
            } else if(ex != null) {
                firstException.addSuppressed(ex);
            }
        }

        return Optional.ofNullable(firstException);
    }

    /**
     * Safely runs some code to create a new exception.
     * <p>
     * In case an exception is thrown while creating the new exception, the original exception is re-thrown.
     * A suppressed exception containing the unexpected (runtime) exception thrown by <code>newExceptionFactory</code> is attached.
     * <p>
     * Additionally, if the newly created exception does not have a cause yet, the original exception is attached to the new exception as a cause.
     * @param originalException The original exception that has been caught
     * @param newExceptionFactory Factory for the new exception that we expect to be thrown. If the factory returns <code>null</code>, the original exception is thrown
     * @return Newly created exception, ready to be thrown by the caller
     * @param <O> Type of the original exception
     * @param <N> Type of the new exception
     */
    public static <O extends Throwable, N extends Exception> N handleException(@NonNull O originalException, @NonNull Supplier<N> newExceptionFactory) throws O {
        N newException = null;
        try {
            newException = newExceptionFactory.get();
        } catch(Exception unexpectedException) {
            // newException will still be null here, so we will fall through to throwing originalException
            originalException.addSuppressed(unexpectedException);
        }

        if(newException == null) {
            throw originalException;
        }

        try {
            newException.initCause(originalException);
        } catch(RuntimeException e) {
            // initCause can throw, if a cause has already been set before.
            // in that case, we ignore the exception, as setting the cause to the original exception is just a fallback behavior
        }

        return newException;

    }

}

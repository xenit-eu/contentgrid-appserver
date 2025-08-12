package com.contentgrid.appserver.domain.data.validation;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class ValidationExceptionCollector<X extends Exception> {
    private final Class<X> exceptionType;

    private X firstException;

    public <T> T use(ThrowingSupplier<T, X> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            X exception = assertExceptionType(e);
            if (firstException == null) {
                firstException = exception;
            } else {
                firstException.addSuppressed(exception);
            }
        }
        return null;
    }

    @SneakyThrows
    private X assertExceptionType(Exception exception) {
        if(!exceptionType.isInstance(exception)) {
            // rethrow the same exception, as if nothing happened
            throw exception;
        }
        return exceptionType.cast(exception);
    }

    public void use(ThrowingRunnable<X> runnable) {
        use(() -> {
            runnable.run();
            return null;
        });
    }

    public void rethrow() throws X {
        if (firstException != null) {
            throw firstException;
        }
    }


    public interface ThrowingSupplier<T, X extends Throwable> {

        T get() throws X;
    }

    public interface ThrowingRunnable<X extends Throwable> {

        void run() throws X;
    }

}

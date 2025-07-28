package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;

public class ValidationExceptionCollector {
    private InvalidPropertyDataException firstException;

    public <T> T use(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (InvalidPropertyDataException e) {
            if(firstException == null) {
                firstException = e;
            } else {
                firstException.addSuppressed(e);
            }
        }
        return null;
    }

    public void use(ThrowingRunnable runnable) {
        use(() -> {
            runnable.run();
            return null;
        });
    }

    public void rethrow() throws InvalidPropertyDataException {
        if(firstException != null) {
            throw firstException;
        }
    }




    public interface ThrowingSupplier<T> {
        T get() throws InvalidPropertyDataException;
    }

    public interface ThrowingRunnable {
        void run() throws InvalidPropertyDataException;
    }

}

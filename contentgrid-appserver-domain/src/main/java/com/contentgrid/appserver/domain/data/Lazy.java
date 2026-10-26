package com.contentgrid.appserver.domain.data;

import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.SneakyThrows;

/**
 * A "lazy" value.
 * The first time that the value is required, the lazy value is evaluated. Later fetches use the cached value
 *
 * @param <T> The type of the result
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Lazy<T> {

    /**
     * The supplier of the lazy value.
     * If it is {@code null}, the value itself is valid (even if it is {@code null} itself)
     */
    private Supplier<T> supplier;

    /**
     * The value after it has been fetched.
     */
    private T value;

    /**
     * The exception that was thrown when fetching the value
     */
    private Exception thrown;

    public static <T> Lazy<T> of(Supplier<T> supplier) {
        return new Lazy<>(supplier, null, null);
    }

    public static <T> Lazy<T> of(T value) {
        return new Lazy<>(null, value, null);
    }

    /**
     * @return The result of the lazy value
     */
    @EqualsAndHashCode.Include
    public T get() {
        if (supplier != null) {
            try {
                value = supplier.get();
            } catch (Exception e) {
                thrown = e;
            } finally {
                supplier = null; // Clear supplier indicate that a final value is present
            }
        }
        rethrow();
        return value;
    }

    @SneakyThrows
    private void rethrow() {
        if (thrown != null) {
            thrown.addSuppressed(new LazyMarkingException());
            throw thrown;
        }
    }

    /**
     * Creates a new lazy value that is the result of applying a mapping function to this lazy value.
     * <p>
     * The source lazy is still only evaluated once
     * @param mapper Mapping function that converts the value of this lazy to the target value
     * @return A new lazy object that returns the value after it has been mapped
     * @param <U> The type of the new lazy result
     */
    public <U> Lazy<U> map(@NonNull Function<? super T, ? extends U> mapper) {
        if (supplier == null && thrown == null) {
            return new Lazy<>(null, mapper.apply(value), null);
        }
        return Lazy.of(() -> mapper.apply(this.get()));
    }

    @Override
    public String toString() {
        if (supplier != null) {
            return "Lazy<uninitialized>";
        }
        if (thrown != null) {
            return "Lazy(exception=" + thrown + ")";

        }
        return "Lazy(value=" + value + ")";
    }

    private static final class LazyMarkingException extends RuntimeException {
        private LazyMarkingException() {
            super("Lazy#get() was called here");
        }
    }
}

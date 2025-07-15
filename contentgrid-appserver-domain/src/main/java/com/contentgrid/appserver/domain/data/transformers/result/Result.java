package com.contentgrid.appserver.domain.data.transformers.result;

import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.transformers.InvalidDataException;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.NonNull;

/**
 * Extension of {@link Optional} that allows to capture and pass through the checked {@link InvalidDataException} as well
 * @param <T> The type of the value
 */
public interface Result<T> {

    /**
     * Creates a result with data
     * @param data The data to create the result with
     */
    static <T> Result<T> of(@NonNull T data) {
        return new DataResult<>(data);
    }

    /**
     * Creates a result with an error
     * @param e The error to create the result with
     */
    static <T> Result<T> error(@NonNull InvalidDataException e) {
        return new ErrorResult<>(e);
    }

    /**
     * Creates an empty result: no data and no error
     */
    static <T> Result<T> empty() {
        return new EmptyResult<>();
    }

    /**
     * Creates a result from an optional
     * @param optional
     * @return A result derived from the optional. Empty optionals map to {@link Result#empty()}, Optionals with data map to {@link Result#of(Object)}
     */
    static <T> Result<T> fromOptional(@NonNull Optional<T> optional) {
        return optional.map(Result::of)
                .orElse(Result.empty());
    }

    /**
     * If a value is present, returns a Result describing the result of applying the mapping function
     * <p>
     * If the mapping function throws a {@link InvalidDataException}, the thrown exception is captured in {@link Result#error(InvalidDataException)}
     * @param mapper Mapping function that is applied to the value, if there is one present. An empty result is returned when this function returns null
     * @param <U> The type of the value returned from the mapping function
     */
    default <U> Result<U> map(@NonNull ThrowingFunction<T, U> mapper) {
        return flatMap(t -> {
            var data = mapper.apply(t);
            if(data == null) {
                return Result.empty();
            }
            return Result.of(data);
        });
    }

    /**
     * If a value is present, returns the result of applying the mapping function to the value.
     * <p>
     * If the mapping function throws a {@link InvalidDataException}, the thrown exception is captured in {@link Result#error(InvalidDataException)}
     * @param mapper Mapping function to apply to the value, if there is one present. This function may not return null.
     * @param <U> The type of the value returned from the mapping function
     */
    <U> Result<U> flatMap(@NonNull ThrowingFunction<T, Result<U>> mapper);

    /**
     * If a value is present, runs the validation function on the value.
     * If the validation function throws an {@link InvalidDataException}, the thrown exception is captured, otherwise the current Result is kept.
     * @param validator Function that is called on to validate the value, if there is one present
     */
    default Result<T> validate(@NonNull ThrowingConsumer<T> validator) {
        return map(t -> {
            validator.accept(t);
            return t;
        });
    }

    /**
     * If a value is present, return the value. Otherwise throws an exception
     * @return The non-null value contained in the result
     * @throws InvalidDataException If the result wraps an error
     * @throws java.util.NoSuchElementException If no value is present
     */
    default T orElseThrow() throws InvalidDataException {
        return asOptional().orElseThrow();
    }

    /**
     * Converts the result into an optional, or throw the error wrapped in the result
     * @return Optional containing either the value contained in the result, or an empty optional when the result is empty
     * @throws InvalidDataException If the result wraps an error
     */
    Optional<T> asOptional() throws InvalidDataException;

    /**
     * Performs the action with the value if it's present, or throw the error wrapped in the result
     * @param action The action to be performed if a value is present
     * @throws InvalidDataException If the result wraps an error
     */
    default void ifPresent(@NonNull Consumer<T> action) throws InvalidDataException {
        asOptional().ifPresent(action);
    }

    interface ThrowingFunction<T, R> {
        R apply(@NonNull T type) throws InvalidDataException;
    }

    interface ThrowingConsumer<T> {
        void accept(@NonNull T type) throws InvalidDataException;
    }
}

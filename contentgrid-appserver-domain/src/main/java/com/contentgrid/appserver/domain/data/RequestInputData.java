package com.contentgrid.appserver.domain.data;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Input data for create and update operations
 * <p>
 * The request input data are the user-submitted values that they requested to be used for create or update operations.
 * <p>
 * Conceptually, it is a key-value object, where values may be any {@link DataEntry}, a list of {@link DataEntry}s or a nested {@link RequestInputData} object.
 */
public interface RequestInputData {

    /**
     * @return A stream of all the keys that are present
     */
    Stream<String> keys();

    /**
     * Retrieves the value associated with a key
     * <p>
     * The implementation is not required to honor the type hint, it may also return the native type of the value.
     * Alternatively, if the implementation supports the requested type hint, and the current type of the value is not valid,
     * it may throw {@link InvalidDataTypeException}
     *
     * @param key The key to use to look up the value
     * @param typeHint A hint about which type of {@link DataEntry} is requested
     * @return The data entry at this key
     * @throws InvalidDataException Optionally, if the type of the value does not match the typeHint
     */
    DataEntry get(String key, Class<? extends DataEntry> typeHint) throws InvalidDataException;

    /**
     * Retrieves a list of values associated with a key
     * <p>
     * The implementation is not required to honor the type hint, it may also return the native type of the value.
     * Alternatively, if the implementation supports the requested type hint, and the current type of the value is not valid,
     * it may throw {@link InvalidDataTypeException}
     *
     * @param key The key to use to look up the value
     * @param entryTypeHint A hint about which type of {@link DataEntry} should be inside the list
     * @return A result of the list at this key
     * @throws InvalidDataException Always when the value is not a list; and optionally, if the type of the value does not match the entryTypeHint
     */
    Result<List<? extends DataEntry>> getList(String key, Class<? extends DataEntry> entryTypeHint) throws InvalidDataException;

    /**
     * Retrieves a nested request input data associated with a key
     * @param key The key to use to look up the value
     * @return A result of the request input data at this key
     * @throws InvalidDataException Always when the value is not a request input data
     */
    Result<RequestInputData> nested(String key)  throws InvalidDataException;

    /**
     * Tri-state equivalent of {@link java.util.Optional}.
     * <p>
     * A result can be
     * <ul>
     *     <li>Present with the declared value: {@link DataResult}</li>
     *     <li>Present, but null: {@link NullResult}</li>
     *     <li>Absent: {@link MissingResult}</li>
     * </ul>
     *
     * @param <T> The type of the value
     */
    sealed interface Result<T> permits DataResult, MissingResult, NullResult {

        /**
         * Retrieves the data, if any
         * <p>
         * Note that you are primarily expected to use a pattern-matching switch statement to distinguish between the different subtypes of {@linkplain Result}
         *
         * @return Retrieves the data from {@link DataResult}, or {@code null} for {@link NullResult} and {@link MissingResult}
         */
        T get();

        /**
         * If a value is present, returns the {@linkplain  Result} after applying the mapping function
         * @param mapper The mapping function to apply to the value, if present
         * @return The {@linkplain Result} describing the result of applying the mapping function to this {@linkplain  Result}
         * @param <U> The type of the value returned from the mapping function
         */
        <U> Result<U> map(Function<T, U> mapper);

        /**
         * @return A {@linkplain Result} representing an absent value
         * @param <T> The type of the absent value
         */
        static <T> Result<T> missing() {
            return new MissingResult<>();
        }

        /**
         * @return A {@linkplain Result} representing a present but {@code null} value
         * @param <T> The type of the null value
         */
        static <T> Result<T> empty() {
            return new NullResult<>();
        }

        /**
         * @param data The value to describe, must be non-null
         * @return A {@link Result} representing a non-null present value
         * @param <T> The type of the value
         */
        static <T> Result<T> of(T data) {
            return new DataResult<>(data);
        }

    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @EqualsAndHashCode
    final class DataResult<T> implements Result<T> {
        @NonNull
        private final T data;

        @Override
        public T get() {
            return data;
        }

        @Override
        public <U> Result<U> map(Function<T, U> mapper) {
            return new DataResult<>(mapper.apply(data));
        }
    }

    @Value
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    class MissingResult<T> implements Result<T> {

        @Override
        public T get() {
            return null;
        }

        @Override
        public <U> Result<U> map(Function<T, U> mapper) {
            return new MissingResult<>();
        }
    }

    @Value
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    class NullResult<T> implements Result<T> {

        @Override
        public T get() {
            return null;
        }

        @Override
        public <U> Result<U> map(Function<T, U> mapper) {
            return new NullResult<>();
        }
    }
}

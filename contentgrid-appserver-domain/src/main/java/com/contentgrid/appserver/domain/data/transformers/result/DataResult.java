package com.contentgrid.appserver.domain.data.transformers.result;

import com.contentgrid.appserver.domain.data.transformers.InvalidDataException;
import java.util.Objects;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DataResult<T> implements Result<T> {

    @NonNull
    private final T data;

    @Override
    public <U> Result<U> flatMap(@NonNull ThrowingFunction<T, Result<U>> mapper) {
        try {
            return Objects.requireNonNull(mapper.apply(data));
        } catch (InvalidDataException e) {
            return new ErrorResult<>(e);
        }
    }

    @Override
    public Optional<T> asOptional() throws InvalidDataException {
        return Optional.of(data);
    }
}

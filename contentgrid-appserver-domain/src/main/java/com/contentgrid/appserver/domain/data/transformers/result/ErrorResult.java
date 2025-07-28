package com.contentgrid.appserver.domain.data.transformers.result;

import com.contentgrid.appserver.domain.data.InvalidDataException;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class ErrorResult<T> implements Result<T> {

    private final InvalidDataException exception;

    @Override
    public <U> Result<U> flatMap(@NonNull ThrowingFunction<T, Result<U>> mapper) {
        return (Result<U>) this;
    }

    @Override
    public Optional<T> asOptional() throws InvalidDataException {
        throw exception;
    }
}

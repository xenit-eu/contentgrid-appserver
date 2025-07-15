package com.contentgrid.appserver.domain.data.transformers.result;

import com.contentgrid.appserver.domain.data.transformers.InvalidDataException;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.NonNull;

public class EmptyResult<T> implements Result<T> {

    @Override
    public <U> Result<U> flatMap(@NonNull ThrowingFunction<T, Result<U>> mapper) {
        return (Result<U>) this;
    }

    @Override
    public Optional<T> asOptional() {
        return Optional.empty();
    }
}

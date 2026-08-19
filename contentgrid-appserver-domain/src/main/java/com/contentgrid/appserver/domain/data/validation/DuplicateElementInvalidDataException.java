package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.domain.data.InvalidDataException;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class DuplicateElementInvalidDataException extends InvalidDataException {
    @NonNull
    private final String duplicateValue;

    @Override
    public String getMessage() {
        return "Value '%s' is present more than once".formatted(duplicateValue);
    }
}

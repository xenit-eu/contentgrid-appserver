package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ContentMissingInvalidDataException extends InvalidDataException {
    private final AttributeName subfield;

    @Override
    public String getMessage() {
        return "Field '%s' can not be set when there is no content".formatted(subfield.getValue());
    }
}

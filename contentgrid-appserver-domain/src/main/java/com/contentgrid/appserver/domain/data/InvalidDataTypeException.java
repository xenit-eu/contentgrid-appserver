package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.domain.data.type.DataType;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Thrown when the data type of a data entry is not the one that is required
 * <p>
 * Type errors occur when a data type is incorrect; for example when a string is required, but a boolean is given as input
 */
@RequiredArgsConstructor
@Getter
public class InvalidDataTypeException extends InvalidDataException {

    @NonNull
    private final DataType expectedType;

    @NonNull
    private final DataType actualType;

    @Override
    public String getMessage() {
        return "Expected type %s, but got type %s".formatted(expectedType.getHumanDescription(), actualType.getHumanDescription());

    }
}

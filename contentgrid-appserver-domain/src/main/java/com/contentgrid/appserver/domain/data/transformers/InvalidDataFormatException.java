package com.contentgrid.appserver.domain.data.transformers;

import com.contentgrid.appserver.domain.data.type.DataType;
import lombok.NonNull;

/**
 * Thrown when a data entry has an invalid format.
 * <p>
 * Format errors typically happen when parsing one data type into another one, for example when parsing an {@linkplain java.time.Instant} from a string.
 *
 * @see InvalidDataTypeException Which is used when the data <b>type</b> is incorrect
 */
public class InvalidDataFormatException extends InvalidDataException {

    @NonNull
    private final DataType expectedType;

    public InvalidDataFormatException(@NonNull DataType expectedType, @NonNull Throwable cause) {
        this.expectedType = expectedType;
        initCause(cause);
    }

    @Override
    public String getMessage() {
        return "Invalid format for type %s: %s".formatted(expectedType, getCause().getMessage());
    }

}

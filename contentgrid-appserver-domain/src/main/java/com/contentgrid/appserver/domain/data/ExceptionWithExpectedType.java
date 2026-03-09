package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.domain.data.type.DataType;

public interface ExceptionWithExpectedType<T extends InvalidDataException & ExceptionWithExpectedType<T>> {
    DataType getExpectedType();

    /**
     * Returns an exception with the expected type set to a more specific type
     * @param expectedType The more specific expected data type
     * @return A new instance of the same exception, with the original stacktrace intact
     */
    T withSpecializedExpectedType(DataType expectedType);
}

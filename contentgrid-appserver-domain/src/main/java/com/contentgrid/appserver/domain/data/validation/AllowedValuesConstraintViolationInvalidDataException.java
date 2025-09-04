package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.domain.data.InvalidDataException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class AllowedValuesConstraintViolationInvalidDataException extends InvalidDataException {
    @NonNull
    private final String actualValue;
    @NonNull
    private final List<String> allowedValues;

    @Override
    public String getMessage() {
        return "Value must be any of %s, but is '%s'".formatted(
                allowedValues.stream().map("'%s'"::formatted)
                        .collect(Collectors.joining(", ")),
                actualValue
        );
    }
}

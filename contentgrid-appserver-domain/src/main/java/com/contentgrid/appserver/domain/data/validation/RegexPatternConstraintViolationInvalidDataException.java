package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.domain.data.InvalidDataException;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class RegexPatternConstraintViolationInvalidDataException extends InvalidDataException {
    @NonNull
    private final String actualValue;

    @NonNull
    private final Pattern pattern;

    @Override
    public String getMessage() {
        return "Value must match pattern '%s', but '%s' doesn't match".formatted(pattern.pattern(), actualValue);
    }
}

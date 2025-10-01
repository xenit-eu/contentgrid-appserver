package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.application.model.values.PropertyName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import java.util.Arrays;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class InvalidPropertyDataException extends InvalidDataException {

    @NonNull
    private final PropertyPath path;

    InvalidPropertyDataException(@NonNull PropertyPath path, @NonNull InvalidDataException exception) {
        super();
        this.path = path;
        initCause(exception);
    }

    @Override
    public synchronized InvalidDataException getCause() {
        return (InvalidDataException) super.getCause();
    }

    public Stream<InvalidPropertyDataException> allExceptions() {
        return Stream.concat(
                Stream.of(this),
                Arrays.stream(getSuppressed())
                        .filter(InvalidPropertyDataException.class::isInstance)
                        .map(InvalidPropertyDataException.class::cast)
                        .flatMap(InvalidPropertyDataException::allExceptions)
        );
    }

    @Override
    public String getMessage() {
        return "Invalid property data at %s: %s".formatted(String.join(".", path.toList()), getCause().getMessage());
    }

    @Override
    public InvalidPropertyDataException withinProperty(PropertyName propertyName) {
        return new InvalidPropertyDataException(path.withPrefix(propertyName), getCause());
    }

}

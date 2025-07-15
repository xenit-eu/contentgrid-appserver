package com.contentgrid.appserver.domain.data.transformers;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.CompositeAttributePath;
import com.contentgrid.appserver.application.model.values.PropertyName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.RelationPath;
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

    @Override
    public String getMessage() {
        return "Invalid property data at %s: %s".formatted(String.join(".", path.toList()), getCause().getMessage());
    }

    @Override
    public InvalidPropertyDataException withinProperty(PropertyName propertyName) {
        var newPath = switch (propertyName) {
            case AttributeName attributeName -> new CompositeAttributePath(attributeName, (AttributePath) path);
            case RelationName relationName -> new RelationPath(relationName, path);
        };
        return new InvalidPropertyDataException(newPath, getCause());
    }

}

package com.contentgrid.appserver.exception;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class InvalidParameterException extends IllegalArgumentException {
    private final String entityName;
    @NonNull
    private final String attributeName;
    @NonNull
    private final Type type;
    @NonNull
    private final String value;

    private static final String fullTemplate = "Invalid argument for attribute %s in entity %s:"
            + " Could not convert value '%s' to %s";
    private static final String templateWithoutEntity = "Invalid argument for attribute %s:"
            + " Could not convert value '%s' to %s";

    public InvalidParameterException(String entityName, @NonNull String attributeName, @NonNull Type type,
            @NonNull String value, Throwable cause) {
        super(entityName == null
                ? templateWithoutEntity.formatted(attributeName, value, type)
                : fullTemplate.formatted(entityName, attributeName, value, type),
                cause
        );
        this.entityName = entityName;
        this.attributeName = attributeName;
        this.type = type;
        this.value = value;
    }

    public InvalidParameterException(@NonNull String attributeName, @NonNull Type type, @NonNull String value, Throwable cause) {
        this(null, attributeName, type, value, cause);
    }
    public InvalidParameterException(String entityName, @NonNull String attributeName, @NonNull Type type, @NonNull String value) {
        this(entityName, attributeName, type, value, null);
    }
    public InvalidParameterException(@NonNull String attributeName, @NonNull Type type, @NonNull String value) {
        this(null, attributeName, type, value, null);
    }
}

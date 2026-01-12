package com.contentgrid.appserver.exception;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class InvalidParameterException extends IllegalArgumentException {
    private final String entityName;
    @NonNull
    private final String filterName;
    @NonNull
    private final Type type;
    private final String value;

    private static final String fullTemplate = "Invalid argument for filter %s in entity %s:"
            + " Could not convert value '%s' to %s";
    private static final String templateWithoutEntity = "Invalid argument for filter %s:"
            + " Could not convert value '%s' to %s";

    public InvalidParameterException(String entityName, @NonNull String filterName, @NonNull Type type,
            String value, Throwable cause) {
        super(entityName == null
                ? templateWithoutEntity.formatted(filterName, value, type)
                : fullTemplate.formatted(filterName, entityName, value, type),
                cause
        );
        this.entityName = entityName;
        this.filterName = filterName;
        this.type = type;
        this.value = value;
    }

    public InvalidParameterException(@NonNull String filterName, @NonNull Type type, String value, Throwable cause) {
        this(null, filterName, type, value, cause);
    }
    public InvalidParameterException(String entityName, @NonNull String filterName, @NonNull Type type, String value) {
        this(entityName, filterName, type, value, null);
    }
    public InvalidParameterException(@NonNull String filterName, @NonNull Type type, String value) {
        this(null, filterName, type, value, null);
    }
}

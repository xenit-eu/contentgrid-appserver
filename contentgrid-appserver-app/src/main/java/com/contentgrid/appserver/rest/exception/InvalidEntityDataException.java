package com.contentgrid.appserver.rest.exception;

import com.contentgrid.appserver.application.model.Entity;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Exception thrown when validation fails for entity data input.
 */
public class InvalidEntityDataException extends RuntimeException {

    @Getter
    private final Map<String, String> validationErrors;

    @Getter
    private final Entity entity;

    public InvalidEntityDataException(Entity entity, Map<String, String> validationErrors) {
        super("Invalid data for entity '" + entity.getName().getValue() + "': " + validationErrors);
        this.entity = entity;
        this.validationErrors = Collections.unmodifiableMap(new HashMap<>(validationErrors));
    }

    public InvalidEntityDataException(Entity entity, String attributeName, String errorMessage) {
        this(entity, Map.of(attributeName, errorMessage));
    }

    public Collection<String> getInvalidAttributes() {
        return validationErrors.keySet();
    }

}
package com.contentgrid.appserver.application.model.exceptions;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.AttributeName;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Exception thrown when validation fails for entity instance data.
 */
public class InvalidEntityDataException extends HttpMessageNotReadableException {

    @Getter
    private final Map<AttributeName, String> validationErrors;

    @Getter
    private final Entity entity;

    public InvalidEntityDataException(Entity entity, Map<AttributeName, String> validationErrors) {
        super("Invalid data for entity '" + entity.getName().getValue() + "': " + validationErrors);
        this.entity = entity;
        this.validationErrors = Collections.unmodifiableMap(new HashMap<>(validationErrors));
    }

    public InvalidEntityDataException(Entity entity, AttributeName attributeName, String errorMessage) {
        this(entity, Map.of(attributeName, errorMessage));
    }

    public Collection<AttributeName> getInvalidAttributes() {
        return validationErrors.keySet();
    }

    /**
     * @return Map of attribute names (as strings) to error messages
     */
    public Map<String, String> getFormattedValidationErrors() {
        Map<String, String> formattedErrors = new HashMap<>();
        validationErrors.forEach((name, message) ->
                formattedErrors.put(name.getValue(), message)
        );
        return formattedErrors;
    }
}
package com.contentgrid.appserver.rest.problem.ext;

import java.util.Map;
import lombok.Getter;

@Getter
public class EntityValidationProblemProperties {
    private final String entityType;
    private final Map<String, String> errors;

    public EntityValidationProblemProperties(String entityType, Map<String, String> errors) {
        this.entityType = entityType;
        this.errors = errors;
    }
}
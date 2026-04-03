package com.contentgrid.appserver.rest.problem;

import com.contentgrid.appserver.rest.problem.ProblemTypeUrlFactory.ProblemTypeResolvable;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSourceResolvable;

public enum ProblemType implements ProblemTypeResolvable {
    INPUT_VALIDATION("input", "validation"),
    INPUT_VALIDATION_DUPLICATE_VALUE("input", "validation", "duplicate"),
    INPUT_VALIDATION_INVALID_TYPE("input", "validation", "type"),
    INPUT_VALIDATION_INVALID_TYPE_FORMAT("input", "validation", "type", "format"),
    INPUT_VALIDATION_REQUIRED_VALUE("input", "validation", "required"),
    INPUT_VALIDATION_ALLOWED_VALUES("input", "validation", "allowed-values"),
    INPUT_VALIDATION_PATTERN("input", "validation", "pattern"),
    INPUT_VALIDATION_NO_CONTENT("input", "validation", "no-content"),
    INPUT_VALIDATION_MISSING_RELATION_TARGET("input", "validation", "missing-relation-target"),

    INVALID_QUERY_PARAMETER_FILTER_FORMAT("invalid-query-parameter", "filter", "format"),
    INVALID_QUERY_PARAMETER_SORT_FORMAT("invalid-query-parameter", "sort", "format"),
    INVALID_QUERY_PARAMETER_SORT_TARGET("invalid-query-parameter", "sort", "target"),
    INVALID_QUERY_PARAMETER_PAGINATION("invalid-query-parameter", "pagination"),

    INVALID_REQUEST_BODY("invalid-request", "body"),
    INVALID_REQUEST_BODY_JSON("invalid-request", "body", "json"),
    INVALID_REQUEST_BODY_URI_LIST("invalid-request", "body", "uri-list"),
    INVALID_REQUEST_BODY_SINGLE_LINK("invalid-request", "body", "single-link"),

    INVALID_REQUEST_REQUIRED_HEADER("invalid-request", "required-header"),
    INVALID_REQUEST_FORBIDDEN_HEADER("invalid-request", "forbidden-header"),

    UNSATISFIED_VERSION("unsatisfied-version"),

    NOT_FOUND_ENDPOINT("not-found", "endpoint"),
    NOT_FOUND_ENTITY_ITEM("not-found", "entity-item"),
    NOT_FOUND_RELATION_ITEM("not-found", "relation-item"), // Specific item not linked in a relation

    INTEGRITY_RELATION_BLIND_OVERWRITE("integrity", "blind-relation-overwrite"),
    INTEGRITY_REQUIRED_RELATION("integrity", "required-relation"),
    ;

    ProblemType(String... params) {
        this.params = params;
    }

    private static final String CLASSNAME = ProblemType.class.getName();

    final String[] params;

    @Override
    public String[] getProblemHierarchy() {
        return params;
    }

    public MessageSourceResolvable forTitle() {
        return new ProblemDetailsMessageSourceResolvable(CLASSNAME+".title", this, new Object[0]);

    }

    public MessageSourceResolvable forDetails(Object... arguments) {
        return new ProblemDetailsMessageSourceResolvable(CLASSNAME+".detail", this, arguments);
    }

    @RequiredArgsConstructor
    private static class ProblemDetailsMessageSourceResolvable implements MessageSourceResolvable {
        private final String prefix;

        private final ProblemType problemType;

        private final Object[] arguments;

        @Override
        public String[] getCodes() {
            var paramsList = Arrays.asList(problemType.params);
            var codes = new String[problemType.params.length];

            for (int i = codes.length; i > 0; i--) {
                codes[codes.length - i] = prefix + "." + String.join(".", paramsList.subList(0, i));
            }
            return codes;
        }

        @Override
        public Object[] getArguments() {
            return arguments;
        }

        @Override
        public String getDefaultMessage() {
            // Provide a static default message instead of recursive call
            return "Problem: " + problemType.name();
        }
    }
}
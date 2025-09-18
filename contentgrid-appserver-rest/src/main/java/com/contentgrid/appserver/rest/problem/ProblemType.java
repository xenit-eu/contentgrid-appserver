package com.contentgrid.appserver.rest.problem;

import com.contentgrid.appserver.rest.problem.ProblemTypeUrlFactory.ProblemTypeResolvable;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSourceResolvable;

public enum ProblemType implements ProblemTypeResolvable {
    INPUT_VALIDATION("input", "validation"),
    INPUT_DUPLICATE_VALUE("input", "duplicate-value"),
    CONSTRAINT_VIOLATION("integrity", "constraint-violation"),
    INVALID_FILTER_PARAMETER("invalid-filter-parameter"),
    INVALID_FILTER_PARAMETER_FORMAT("invalid-filter-parameter", "format"),
    INVALID_SORT_PARAMETER("invalid-query-parameter", "sort"),
    INVALID_PAGINATION_PARAMETER("invalid-query-parameter", "pagination"),

    INVALID_REQUEST_BODY("invalid-request-body"),
    INVALID_REQUEST_BODY_TYPE("invalid-request-body", "type"),
    INVALID_REQUEST_BODY_JSON("invalid-request-body", "json"),
    UNSATISFIED_VERSION("unsatisfied-version"),
    INTEGRITY_RELATION_OVERWRITE("integrity", "relation-overwrite"),
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
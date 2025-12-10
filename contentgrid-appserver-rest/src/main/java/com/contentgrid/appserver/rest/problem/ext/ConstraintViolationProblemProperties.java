package com.contentgrid.appserver.rest.problem.ext;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.springframework.hateoas.mediatype.problem.Problem;

/**
 * API representation for a constraint violation problem details
 * <p>
 * This is part of the public API of the application, don't make backwards incompatible changes
 */
@Value
@Builder
public class ConstraintViolationProblemProperties {

    @Singular
    @JsonProperty("errors")
    List<Problem> errors;

    public static class ConstraintViolationProblemPropertiesBuilder {

        public ConstraintViolationProblemPropertiesBuilder global(Problem problem) {
            return error(problem);
        }

        public ConstraintViolationProblemPropertiesBuilder field(Problem problem, String fieldName) {
            return error(
                    MergedProblemProperties.extend(
                            problem,
                            new FieldViolationProblemProperties(fieldName)
                    )
            );
        }

        public ConstraintViolationProblemPropertiesBuilder field(Problem problem, String fieldName, Object invalidValue) {
            return error(MergedProblemProperties.extend(
                            problem,
                            new FieldViolationProblemProperties(fieldName),
                            new InvalidValueProblemProperties(invalidValue)
                    )
            );
        }

        public ConstraintViolationProblemPropertiesBuilder target(Problem problem, String entity, String id) {
            return error(
                    MergedProblemProperties.extend(
                            problem,
                            new RelationTargetProblemProperties(entity, id)
                    )
            );
        }
    }

    @Value
    public static class FieldViolationProblemProperties {

        String property;
    }

    @Value
    private static class InvalidValueProblemProperties {

        @JsonProperty("invalid_value")
        Object invalidValue;
    }

    @Value
    private static class RelationTargetProblemProperties {
        String entity;
        String id;
    }
}

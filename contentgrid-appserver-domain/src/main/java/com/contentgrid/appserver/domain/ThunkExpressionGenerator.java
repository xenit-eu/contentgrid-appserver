package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.exception.InvalidParameterException;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.predicates.model.Variable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ThunkExpressionGenerator {
    static ThunkExpression<Boolean> from(Entity entity, Map<String, String> params) {
        List<ThunkExpression<Boolean>> expressions = new ArrayList<>();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            var maybeAttribute = entity.getAttributeByName(AttributeName.of(entry.getKey()));
            if (maybeAttribute.isEmpty()) {
                // ignore unknown attributes
                continue;
            }
            var attribute = maybeAttribute.get();

            if (attribute instanceof SimpleAttribute simpleAttribute) {
                try {
                    Scalar<?> parsedValue = parseValueToScalar(simpleAttribute.getType(), entry.getValue());
                    ThunkExpression<Boolean> expression = createEqualityExpression(entry.getKey(), parsedValue);
                    expressions.add(expression);
                } catch (Exception e) {
                    throw new InvalidParameterException(entity.getName().getValue(), entry.getKey(),
                            simpleAttribute.getType(), entry.getValue(), e);
                }
            }
        }

        // If no valid expressions were created, return a "true" expression
        if (expressions.isEmpty()) {
            return Scalar.of(true);
        }

        // If there's only one expression, return it
        if (expressions.size() == 1) {
            return expressions.getFirst();
        }

        // Otherwise, create a conjunction (AND) of all expressions
        return LogicalOperation.conjunction(expressions.stream());
    }

    private static Scalar<?> parseValueToScalar(SimpleAttribute.Type type, String value) {
        if (value == null) {
            throw new IllegalArgumentException("null is not supported");
        }

        return switch (type) {
            case LONG -> Scalar.of(Long.parseLong(value));
            case DOUBLE -> Scalar.of(new BigDecimal(value));
            case BOOLEAN -> Scalar.of(Boolean.parseBoolean(value));
            case TEXT -> Scalar.of(value);
            case DATETIME -> Scalar.of(Instant.parse(value));
            case UUID -> Scalar.of(java.util.UUID.fromString(value));
        };
    }

    private static ThunkExpression<Boolean> createEqualityExpression(String attributeName, Scalar<?> value) {
        return Comparison.areEqual(
                SymbolicReference.of(Variable.named("entity"), SymbolicReference.path(attributeName)),
                value
        );
    }
}

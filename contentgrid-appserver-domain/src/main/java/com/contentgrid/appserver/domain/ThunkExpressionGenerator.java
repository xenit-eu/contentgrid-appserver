package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.exception.InvalidParameterException;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.SymbolicReference.PathElement;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.predicates.model.Variable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class ThunkExpressionGenerator {

    static ThunkExpression<Boolean> from(Entity entity, Map<String, String> params) {
        List<ThunkExpression<Boolean>> expressions = new ArrayList<>();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String attributePath = entry.getKey();
            String[] pathSegments = attributePath.split("\\.");

            if (pathSegments.length == 1) {
                // Simple attribute case
                var maybeAttribute = entity.getAttributeByName(AttributeName.of(pathSegments[0]));
                if (maybeAttribute.isEmpty()) {
                    // ignore unknown attributes
                    continue;
                }
                var attribute = maybeAttribute.get();

                if (attribute instanceof SimpleAttribute simpleAttribute) {
                    try {
                        Scalar<?> parsedValue = parseValueToScalar(simpleAttribute.getType(), entry.getValue());
                        ThunkExpression<Boolean> expression = createEqualityExpression(
                                pathSegments,
                                parsedValue
                        );
                        expressions.add(expression);
                    } catch (Exception e) {
                        throw new InvalidParameterException(entity.getName().getValue(), entry.getKey(),
                                simpleAttribute.getType(), entry.getValue(), e);
                    }
                }
            } else {
                // Composite attribute case
                var maybeRootAttribute = entity.getAttributeByName(AttributeName.of(pathSegments[0]));
                if (maybeRootAttribute.isEmpty()) {
                    // ignore unknown attributes
                    continue;
                }

                Attribute attribute = maybeRootAttribute.get();
                SimpleAttribute leafAttribute = findLeafAttribute(attribute, pathSegments, 1);

                if (leafAttribute != null) {
                    try {
                        Scalar<?> parsedValue = parseValueToScalar(leafAttribute.getType(), entry.getValue());
                        ThunkExpression<Boolean> expression = createEqualityExpression(
                                pathSegments,
                                parsedValue
                        );
                        expressions.add(expression);
                    } catch (Exception e) {
                        throw new InvalidParameterException(entity.getName().getValue(), entry.getKey(),
                                leafAttribute.getType(), entry.getValue(), e);
                    }
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

    // Find SimpleAttribute in a CompositeAttribute given a path
    private static SimpleAttribute findLeafAttribute(Attribute attribute, String[] pathSegments, int currentIndex) {
        if (currentIndex >= pathSegments.length) {
            return attribute instanceof SimpleAttribute ? (SimpleAttribute) attribute : null;
        }

        if (attribute instanceof CompositeAttribute compositeAttribute) {
            Optional<Attribute> childAttribute = compositeAttribute.getAttributeByName(
                    AttributeName.of(pathSegments[currentIndex])
            );

            if (childAttribute.isPresent()) {
                return findLeafAttribute(childAttribute.get(), pathSegments, currentIndex + 1);
            }
        }

        return null;
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

    private static ThunkExpression<Boolean> createEqualityExpression(String[] pathSegments, Scalar<?> value) {
        Stream<PathElement> path = Arrays.stream(pathSegments).map(SymbolicReference::path);
        SymbolicReference attr = SymbolicReference.of(Variable.named("entity"), path);

        return Comparison.areEqual(attr, value);
    }
}

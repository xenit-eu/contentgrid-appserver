package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ThunkExpressionGenerator {

    static ThunkExpression<Boolean> from(Application application, Entity entity, Map<String, String> params) {
        List<ThunkExpression<Boolean>> expressions = new ArrayList<>();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String filterName = entry.getKey();

            var maybeSearchFilter = entity.getFilterByName(FilterName.of(filterName));
            if (maybeSearchFilter.isEmpty()) {
                // ignore unknown filters
                continue;
            }

            SearchFilter searchFilter = maybeSearchFilter.get();

            // currently only handle exact search TODO support prefix, case insensitive, ...
            if (searchFilter instanceof ExactSearchFilter exactSearchFilter) {
                try {
                    Scalar<?> parsedValue = parseValueToScalar(exactSearchFilter.getAttributeType(), entry.getValue());
                    Stream<PathElement> pathElements = convertPath(application, entity, exactSearchFilter.getAttributePath());
                    ThunkExpression<Boolean> expression = createEqualityExpression(
                            pathElements,
                            parsedValue
                    );
                    expressions.add(expression);
                } catch (Exception e) {
                    throw new InvalidParameterException(entity.getName().getValue(), entry.getKey(),
                            exactSearchFilter.getAttributeType(), entry.getValue(), e);
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

    private static ThunkExpression<Boolean> createEqualityExpression(Stream<PathElement> pathElements, Scalar<?> value) {
        SymbolicReference attr = SymbolicReference.of(Variable.named("entity"), pathElements);

        return Comparison.areEqual(attr, value);
    }

    private static Stream<PathElement> convertPath(Application application, Entity entity, PropertyPath path) {
        List<PathElement> pathElements = new ArrayList<>();
        Entity currentEntity = entity;
        PropertyPath currentPath = path;

        while (currentPath != null) {
            PropertyName name = currentPath.getFirst();
            final String entityName = currentEntity.getName().getValue(); // Can only use (effectively) final vars in lambda

            switch (name) {
                case AttributeName ignored -> {
                    // If the remaining path is just (composite) attributes, validate the path via the current entity
                    // This throws if there is an invalid link
                    currentEntity.resolveAttributePath(currentPath);

                    // Convert the rest of the path using toList()
                    return Stream.concat(
                            pathElements.stream(),
                            currentPath.toList().stream().map(SymbolicReference::path)
                    );
                }
                case RelationName relationName -> {
                    var relation = application.getRelationForEntity(currentEntity, relationName)
                            .orElseThrow(() -> new IllegalArgumentException("Relation %s not found on entity %s"
                                    .formatted(relationName.getValue(), entityName)));

                    pathElements.add(SymbolicReference.path(relationName.getValue()));

                    // ThunkExpressions need an underscore variable to traverse ToMany (e.g. entity.invoices[_].date)
                    if (relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation) {
                        pathElements.add(SymbolicReference.pathVar("_"));
                    }

                    currentEntity = relation.getTargetEndPoint().getEntity();
                    currentPath = currentPath.getRest();
                }
            }
        }

        return pathElements.stream();
    }
}

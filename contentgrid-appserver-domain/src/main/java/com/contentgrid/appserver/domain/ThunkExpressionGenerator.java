package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.RelationSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import com.contentgrid.appserver.application.model.values.FilterName;
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

    static ThunkExpression<Boolean> from(Entity entity, Map<String, String> params) {
        List<ThunkExpression<Boolean>> expressions = new ArrayList<>();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String filterName = entry.getKey();

            var maybeSearchFilter = entity.getFilterByName(FilterName.of(filterName));
            if (maybeSearchFilter.isEmpty()) {
                // ignore unknown filters
                continue;
            }

            SearchFilter searchFilter = maybeSearchFilter.get();
            List<PathElement> prefix = null;

            if (searchFilter instanceof RelationSearchFilter relationSearchFilter) {
                // We always view it the relation from the source endpoint perspective, works for inverse relations too
                var rel = relationSearchFilter.getRelation();
                prefix = switch (rel) {
                    case OneToOneRelation oto -> List.of(SymbolicReference.path(rel.getSourceEndPoint().getName().getValue()));
                    case ManyToOneRelation mto -> List.of(SymbolicReference.path(rel.getSourceEndPoint().getName().getValue()));
                    case OneToManyRelation otm -> List.of(SymbolicReference.path(rel.getSourceEndPoint().getName().getValue()),
                            SymbolicReference.pathVar("_"));
                    case ManyToManyRelation mtm -> List.of(SymbolicReference.path(rel.getSourceEndPoint().getName().getValue()),
                            SymbolicReference.pathVar("_"));
                };

                searchFilter = relationSearchFilter.getWrappedFilter();
            }

            // currently only handle exact search TODO support prefix, case insensitive, ...
            if (searchFilter instanceof ExactSearchFilter exactSearchFilter) {
                try {
                    Scalar<?> parsedValue = parseValueToScalar(exactSearchFilter.getAttributeType(), entry.getValue());
                    List<String> pathSegments = exactSearchFilter.getAttributePath().toList();
                    ThunkExpression<Boolean> expression = createEqualityExpression(
                            prefix,
                            pathSegments,
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

    private static ThunkExpression<Boolean> createEqualityExpression(List<PathElement> prefix, List<String> pathSegments, Scalar<?> value) {
        Stream<PathElement> pathPrefix = prefix == null
                ? Stream.of()
                : prefix.stream();
        Stream<PathElement> path = pathSegments.stream().map(SymbolicReference::path);
        SymbolicReference attr = SymbolicReference.of(Variable.named("entity"), Stream.concat(pathPrefix, path));

        return Comparison.areEqual(attr, value);
    }
}

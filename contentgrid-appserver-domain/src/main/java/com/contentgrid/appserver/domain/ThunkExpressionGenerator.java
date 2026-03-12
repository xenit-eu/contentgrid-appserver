package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.BaseAttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.FullTextSearchAttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationPath;
import com.contentgrid.appserver.domain.data.validation.ValidationExceptionCollector;
import com.contentgrid.appserver.exception.InvalidFilterParameterException;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.SymbolicReference.PathElement;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.predicates.model.Variable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ThunkExpressionGenerator {

    static ThunkExpression<Boolean> from(Application application, Entity entity, Map<String, List<String>> params) {
        List<ThunkExpression<Boolean>> expressions = new ArrayList<>();
        var variableGenerator = new VariableGenerator();
        var collector = new ValidationExceptionCollector<>(InvalidFilterParameterException.class);

        collector.use(() -> {
            for (Map.Entry<String, List<String>> entry : params.entrySet()) {
                String filterName = entry.getKey();

                var maybeSearchFilter = entity.getFilterByName(FilterName.of(filterName));
                if (maybeSearchFilter.isEmpty()) {
                    // ignore unknown filters
                    continue;
                }

                SearchFilter searchFilter = maybeSearchFilter.get();

                // currently only handle attribute search filters
                if (searchFilter instanceof BaseAttributeSearchFilter attributeSearchFilter) {
                    var attribute = application.resolvePropertyPath(entity, attributeSearchFilter.getAttributePath());
                    List<ThunkExpression<Boolean>> subexpressions = new ArrayList<>();

                    for (String value : entry.getValue()) {
                        try {
                            Scalar<?> parsedValue = parseValueToScalar(attribute.getType(), value);
                            subexpressions.add(createExpression(
                                    attributeSearchFilter,
                                    convertPath(variableGenerator, application, entity, attributeSearchFilter.getAttributePath()),
                                    parsedValue
                            ));
                        } catch (Exception e) {
                            throw new InvalidFilterParameterException(entity.getName(), attributeSearchFilter.getName(),
                                    attribute.getType(), e);
                        }
                    }

                    if (subexpressions.size() == 1) {
                        // If there's only one subexpression, add it directly
                        expressions.add(subexpressions.getFirst());
                    } else if (!subexpressions.isEmpty()) {
                        // Otherwise, create a disjunction (OR) of all subexpressions if not empty
                        expressions.add(LogicalOperation.disjunction(subexpressions));
                    }
                }
            }
        });
        collector.rethrow();

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
            case DATE -> Scalar.of(LocalDate.parse(value));
            case DATETIME -> Scalar.of(Instant.parse(value));
            case UUID -> Scalar.of(java.util.UUID.fromString(value));
        };
    }

    private static ThunkExpression<Boolean> createExpression(BaseAttributeSearchFilter filter,
                                                             List<PathElement> pathElements,
                                                             Scalar<?> value) throws IllegalArgumentException {
        SymbolicReference attr = SymbolicReference.of(Variable.named("entity"), pathElements);

        if (filter instanceof FullTextSearchAttributeSearchFilter ftsSearchFilter) return createExpression(ftsSearchFilter, attr, value);
        if (filter instanceof AttributeSearchFilter attrSearchFilter) return createExpression(attrSearchFilter, attr, value);

        throw new IllegalArgumentException("Received unknown filter type (%s).".formatted(filter.getClass().getName()));
    }

    private static ThunkExpression<Boolean> createExpression(AttributeSearchFilter filter, SymbolicReference attr, Scalar<?> value) {
        return switch (filter.getOperation()) {
            case EXACT -> Comparison.areEqual(attr, value);
            case PREFIX -> StringComparison.contentGridPrefixSearchMatch(attr, value.assertResultType(String.class));
            case GREATER_THAN -> Comparison.greater(attr, value);
            case GREATER_THAN_OR_EQUAL -> Comparison.greaterOrEquals(attr, value);
            case LESS_THAN -> Comparison.less(attr, value);
            case LESS_THAN_OR_EQUAL -> Comparison.lessOrEquals(attr, value);
        };
    }

    private static ThunkExpression<Boolean> createExpression(FullTextSearchAttributeSearchFilter filter,
                                                             SymbolicReference attr,
                                                             Scalar<?> value) {
        return StringComparison.contentGridFullTextSearchMatch(attr, value.assertResultType(String.class), filter.getLocale());
    }

    private static List<PathElement> convertPath(VariableGenerator variableGenerator, Application application, Entity entity, PropertyPath path) {
        List<PathElement> pathElements = new ArrayList<>();
        Entity currentEntity = entity;
        PropertyPath currentPath = path;

        while (currentPath != null) {
            final String entityName = currentEntity.getName().getValue(); // Can only use (effectively) final vars in lambda

            switch (currentPath) {
                case AttributePath attributePath -> {
                    // If the remaining path is just (composite) attributes, validate the path via the current entity
                    // This throws if there is an invalid link
                    currentEntity.resolveAttributePath(attributePath);

                    // Convert the rest of the path using toList()
                    return Stream.concat(
                            pathElements.stream(),
                            currentPath.toList().stream().map(SymbolicReference::path)
                    ).toList();
                }
                case RelationPath relationPath -> {
                    var relationName = relationPath.getRelation();
                    var relation = application.getRelationForEntity(currentEntity, relationName)
                            .orElseThrow(() -> new IllegalArgumentException("Relation %s not found on entity %s"
                                    .formatted(relationName.getValue(), entityName)));

                    pathElements.add(SymbolicReference.path(relationName.getValue()));

                    // ThunkExpressions need a random variable to traverse ToMany (e.g. entity.invoices[some_var].date)
                    // Generate a new name each time.
                    if (relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation) {
                        pathElements.add(SymbolicReference.pathVar(variableGenerator.generate()));
                    }

                    currentEntity = application.getRelationTargetEntity(relation);
                    currentPath = currentPath.getRest();
                }
            }
        }

        return pathElements;
    }

    private static class VariableGenerator {
        int count = 0;

        private String generate() {
            var name = "__wildcard_" + count;
            count += 1;
            return name;
        }
    }
}

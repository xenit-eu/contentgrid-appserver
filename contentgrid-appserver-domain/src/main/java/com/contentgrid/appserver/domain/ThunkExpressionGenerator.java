package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.MultivalueAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.CrossesRelation;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.BaseAttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.FullTextSearchAttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.application.model.propertypath.PropertyPathResolver;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import com.contentgrid.appserver.domain.data.type.DataType;
import com.contentgrid.appserver.domain.data.validation.NulByteValidator;
import com.contentgrid.appserver.domain.data.validation.ValidationExceptionCollector;
import com.contentgrid.appserver.exception.InvalidFilterParameterException;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SetValue;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.SymbolicReference.PathElement;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.predicates.model.Variable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
                    var attribute = application.resolveAttribute(entity, attributeSearchFilter.getAttributePath());
                    List<Scalar<?>> parsedValues = new ArrayList<>();

                    for (String value : entry.getValue()) {
                        try {
                            parsedValues.add(parseValueToScalar(attribute, value));
                        } catch (Exception e) {
                            throw new InvalidFilterParameterException(entity.getName(), attributeSearchFilter.getName(),
                                    DataType.of(attribute), e);
                        }
                    }

                    if (!parsedValues.isEmpty()) {
                        expressions.add(createExpression(variableGenerator, application, entity, attributeSearchFilter,
                                attribute, parsedValues));
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


    private static Scalar<?> parseValueToScalar(Attribute attribute, String value) {
        return switch (attribute) {
            case SimpleAttribute simpleAttribute -> parseValueToScalar(simpleAttribute.getType(), value);
            // A multi-value filter value is a single element: it is compared against each element
            case MultivalueAttribute multivalueAttribute -> parseValueToScalar(multivalueAttribute.getItemType(), value);
            case CompositeAttribute ignored ->
                    throw new IllegalArgumentException("Search filters cannot target a composite attribute");
        };
    }

    private static boolean isMultiValued(Attribute attribute) {
        return attribute instanceof MultivalueAttribute
                || (attribute instanceof SimpleAttribute simpleAttribute
                        && simpleAttribute.getType() == SimpleAttribute.Type.TEXT_SET);
    }

    private static Scalar<?> parseValueToScalar(SimpleAttribute.Type type, String value) {
        if (value == null) {
            throw new IllegalArgumentException("null is not supported");
        }

        return switch (type) {
            case LONG -> Scalar.of(Long.parseLong(value));
            case DOUBLE -> Scalar.of(new BigDecimal(value));
            case BOOLEAN -> Scalar.of(Boolean.parseBoolean(value));
            // A TEXT_SET filter value is a single string: it is compared against each element
            case TEXT, TEXT_SET -> {
                if (value.indexOf('\u0000') >= 0) {
                    throw new IllegalArgumentException(NulByteValidator.ERROR_MESSAGE);
                }
                yield Scalar.of(value);
            }
            case DATE -> Scalar.of(LocalDate.parse(value));
            case DATETIME -> Scalar.of(Instant.parse(value));
            case UUID -> Scalar.of(java.util.UUID.fromString(value));
        };
    }

    private static ThunkExpression<Boolean> createExpression(
            VariableGenerator variableGenerator,
            Application application,
            Entity entity,
            BaseAttributeSearchFilter filter,
            Attribute attribute,
            List<Scalar<?>> values) {

        if (filter instanceof FullTextSearchAttributeSearchFilter ftsFilter) {
            // FTS keeps a disjunction; each term gets its own path so to-many wildcards stay independent
            var subexpressions = values.stream()
                    .map(v -> (ThunkExpression<Boolean>) StringComparison.contentGridFullTextSearchMatch(
                            symRef(convertPath(variableGenerator, application, entity, filter.getAttributePath())),
                            v.assertResultType(String.class),
                            ftsFilter.getLocale()
                    ))
                    .toList();
            return toSingleOrDisjunction(subexpressions);
        }

        if (filter instanceof AttributeSearchFilter attrFilter) {
            return switch (attrFilter.getOperation()) {
                case EXACT -> {
                    var attr = symRef(convertPath(variableGenerator, application, entity, filter.getAttributePath()));
                    if (isMultiValued(attribute)) {
                        // Any element matches any of the values: one overlap expression carries all values
                        yield StringComparison.contentGridArraySearchMatch(attr,
                                new SetValue(new LinkedHashSet<>(values)));
                    }
                    // EXACT can use in when there are multiple values
                    yield values.size() == 1
                            ? Comparison.areEqual(attr, values.getFirst())
                            : Comparison.in(attr, new SetValue(new LinkedHashSet<>(values)));
                }
                case PREFIX -> {
                    // PREFIX keeps a disjunction; each term gets its own path so to-many wildcards stay independent
                    var subexpressions = values.stream()
                            .map(v -> (ThunkExpression<Boolean>) StringComparison.contentGridPrefixSearchMatch(
                                    symRef(convertPath(variableGenerator, application, entity, filter.getAttributePath())),
                                    v.assertResultType(String.class)
                            ))
                            .toList();
                    yield toSingleOrDisjunction(subexpressions);
                }
                // GREATER_THAN and GREATER_THAN_OR_EQUAL always compare with the minimum value
                case GREATER_THAN -> Comparison.greater(
                        symRef(convertPath(variableGenerator, application, entity, filter.getAttributePath())),
                        minScalar(values));
                case GREATER_THAN_OR_EQUAL -> Comparison.greaterOrEquals(
                        symRef(convertPath(variableGenerator, application, entity, filter.getAttributePath())),
                        minScalar(values));
                // LESS_THAN and LESS_THAN_OR_EQUAL always compare with the maximum value
                case LESS_THAN -> Comparison.less(
                        symRef(convertPath(variableGenerator, application, entity, filter.getAttributePath())),
                        maxScalar(values));
                case LESS_THAN_OR_EQUAL -> Comparison.lessOrEquals(
                        symRef(convertPath(variableGenerator, application, entity, filter.getAttributePath())),
                        maxScalar(values));
            };
        }

        throw new IllegalArgumentException("Received unknown filter type (%s).".formatted(filter.getClass().getName()));
    }

    private static SymbolicReference symRef(List<PathElement> pathElements) {
        return SymbolicReference.of(Variable.named("entity"), pathElements);
    }

    private static ThunkExpression<Boolean> toSingleOrDisjunction(List<ThunkExpression<Boolean>> subexpressions) {
        if (subexpressions.size() == 1) {
            return subexpressions.getFirst();
        }
        return LogicalOperation.disjunction(subexpressions);
    }

    @SuppressWarnings("unchecked")
    private static Scalar<?> minScalar(List<Scalar<?>> values) {
        return values.stream()
                .min((a, b) -> ((Comparable<Object>) a.getValue()).compareTo(b.getValue()))
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Scalar<?> maxScalar(List<Scalar<?>> values) {
        return values.stream()
                .max((a, b) -> ((Comparable<Object>) a.getValue()).compareTo(b.getValue()))
                .orElseThrow();
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
                    PropertyPathResolver.resolveAttributePath(currentEntity, attributePath);

                    // Convert the rest of the path using toList()
                    return Stream.concat(
                            pathElements.stream(),
                            currentPath.toList().stream().map(SymbolicReference::path)
                    ).toList();
                }
                case CrossesRelation relationPath -> {
                    var relationName = relationPath.getFirst();
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

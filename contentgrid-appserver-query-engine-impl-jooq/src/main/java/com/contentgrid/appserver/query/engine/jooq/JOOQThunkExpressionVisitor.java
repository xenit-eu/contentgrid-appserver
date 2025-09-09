package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison.ContentGridPrefixSearch;
import com.contentgrid.thunx.predicates.model.FunctionExpression;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.SymbolicReference.PathElement;
import com.contentgrid.thunx.predicates.model.SymbolicReference.StringPathElement;
import com.contentgrid.thunx.predicates.model.SymbolicReference.VariablePathElement;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.predicates.model.ThunkExpressionVisitor;
import com.contentgrid.thunx.predicates.model.Variable;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Param;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

@RequiredArgsConstructor
public class JOOQThunkExpressionVisitor implements ThunkExpressionVisitor<Field<?>, JOOQThunkExpressionVisitor.JOOQContext> {

    @Override
    public Param<?> visit(Scalar<?> scalar, JOOQContext context) throws InvalidThunkExpressionException {
        if (scalar.getValue() == null) {
            // Special case, the value is null
            throw new InvalidThunkExpressionException("null values are not supported");
        } else if (Number.class.equals(scalar.getResultType())) {
            // Number is not supported
            return DSL.value(scalar.getValue(), scalar.getValue().getClass());
        }
        return DSL.value(scalar.getValue(), scalar.getResultType());
    }

    @Override
    public Field<?> visit(FunctionExpression<?> functionExpression, JOOQContext context) throws InvalidThunkExpressionException {
        Field<?> result = switch (functionExpression.getOperator()) {
            case EQUALS -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (List.of(left.getDataType().getType(), right.getDataType().getType()).contains(String.class)) {
                    left = normalize(left);
                    right = normalize(right);
                }
                yield ((Field<Object>) left).equal((Field<Object>) right);
            }
            case NOT_EQUAL_TO -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (List.of(left.getDataType().getType(), right.getDataType().getType()).contains(String.class)) {
                    left = normalize(left);
                    right = normalize(right);
                }
                yield ((Field<Object>) left).notEqual((Field<Object>) right);
            }
            case GREATER_THAN -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                yield ((Field<Object>) left).greaterThan((Field<Object>) right);
            }
            case GREATER_THAN_OR_EQUAL_TO -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                yield ((Field<Object>) left).greaterOrEqual((Field<Object>) right);
            }
            case LESS_THAN -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                yield ((Field<Object>) left).lessThan((Field<Object>) right);
            }
            case LESS_THEN_OR_EQUAL_TO -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                yield ((Field<Object>) left).lessOrEqual((Field<Object>) right);
            }
            case AND -> {
                yield DSL.and(functionExpression.getTerms().stream()
                        .map(expression -> expression.accept(this, context))
                        .map(field -> {
                            if (field instanceof Condition condition) {
                                return condition;
                            } else {
                                return DSL.condition((Field<Boolean>) field);
                            }
                        })
                        .toList());
            }
            case OR -> {
                yield DSL.or(functionExpression.getTerms().stream()
                        .map(expression -> expression.accept(this, context))
                        .map(field -> {
                            if (field instanceof Condition condition) {
                                return condition;
                            } else {
                                return DSL.condition((Field<Boolean>) field);
                            }
                        })
                        .toList());
            }
            case NOT -> {
                assertOneTerm(functionExpression.getTerms());
                var field = functionExpression.getTerms().getFirst().accept(this, context);
                if (field instanceof Condition condition) {
                    yield DSL.not(condition);
                } else {
                    // Try casting to Field<Boolean>
                    yield DSL.condition(DSL.not((Field<Boolean>) field));
                }
            }
            case PLUS -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                yield left.add(right);
            }
            case MULTIPLY -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (right.getDataType().isNumeric()) {
                    yield left.times((Field<? extends Number>) right);
                }
                throw new InvalidThunkExpressionException("Terms should be numeric");
            }
            case MINUS -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                yield left.minus(right);
            }
            case DIVIDE -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (right.getDataType().isNumeric()) {
                    yield left.divide((Field<? extends Number>) right);
                }
                throw new InvalidThunkExpressionException("Terms should be numeric");
            }
            case MODULUS -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (right.getDataType().isNumeric()) {
                    yield left.modulo((Field<? extends Number>) right);
                }
                throw new InvalidThunkExpressionException("Terms should be numeric");
            }
            case CUSTOM -> {
                if (functionExpression instanceof ContentGridPrefixSearch contentGridPrefixSearch) {
                    var left = contentGridPrefixSearch.getLeftTerm().accept(this, context);
                    var right = contentGridPrefixSearch.getRightTerm().accept(this, context);
                    var leftField = DSL.field(DSL.sql("extensions.contentgrid_prefix_search_normalize(?)", left), String.class);
                    var rightField = DSL.field(DSL.sql("extensions.contentgrid_prefix_search_normalize(?)", right), String.class);
                    yield leftField.startsWith(rightField);
                } else {
                    throw new InvalidThunkExpressionException(
                            "Function expression with type %s is not supported.".formatted(
                                    functionExpression.getClass().getSimpleName()));
                }
            }
        };

        if (result instanceof Condition condition) {
            return context.getJoinCollection().collect(condition);
        }
        return result;
    }

    private static void assertOneTerm(List<? extends ThunkExpression<?>> terms) throws InvalidThunkExpressionException {
        if (terms.size() != 1) {
            throw new InvalidThunkExpressionException("Operation requires 1 parameter.");
        }
    }

    private static void assertTwoTerms(List<? extends ThunkExpression<?>> terms) throws InvalidThunkExpressionException {
        if (terms.size() != 2) {
            throw new InvalidThunkExpressionException("Operation requires 2 parameters.");
        }
    }

    private static Field<String> normalize(Field<?> field) {
        return DSL.field(DSL.sql("normalize(?, NFKC)", field), SQLDataType.CLOB);
    }

    @Override
    public Field<?> visit(SymbolicReference symbolicReference, JOOQContext context) throws InvalidThunkExpressionException {
        // Assumption: some other component will translate a SearchFilter to a ThunkExpression where
        // the SymbolicReference will use AttributeName and RelationName in path elements and that
        // a SymbolicReference from OPA also uses AttributeName and RelationName in path elements.
        if (!symbolicReference.getSubject().getName().equals("entity")) {
            throw new InvalidThunkExpressionException("Symbolic reference with subject %s is not supported"
                    .formatted(symbolicReference.getSubject().getName()));
        }
        var result = handlePath(context.getEntity(), symbolicReference.getPath(), context);
        // Reset current table of join collection, so that next joins are added to root again
        context.getJoinCollection().resetCurrentTable();
        return result;
    }

    private Field<?> handlePath(@NonNull Entity entity, @NonNull List<PathElement> path, @NonNull JOOQContext context)
            throws InvalidThunkExpressionException {
        if (path.isEmpty()) {
            throw new InvalidThunkExpressionException("Empty path");
        }
        var pathElement = path.getFirst();
        var tail = path.subList(1, path.size());
        var name = getPathElementName(pathElement);

        // Check if pathElement references attribute
        Optional<Attribute> maybeAttribute = entity.getAttributeByName(AttributeName.of(name));
        if (maybeAttribute.isPresent()) {
            var attribute = maybeAttribute.get();
            return handleAttribute(context.getJoinCollection().getCurrentAlias(), attribute, tail);
        }

        // Check if pathElement references relation
        Optional<Relation> maybeRelation = context.getApplication().getRelationForEntity(entity, RelationName.of(name));
        if (maybeRelation.isPresent()) {
            var relation = maybeRelation.get();
            return handleRelation(relation, tail, context);
        }

        // pathElement seems to reference a non-existing attribute/relation on the entity
        throw new InvalidThunkExpressionException("Path element %s does not exist on entity %s".formatted(name, entity));
    }

    private Field<?> handleAttribute(@NonNull TableName currentAlias, @NonNull Attribute attribute, @NonNull List<SymbolicReference.PathElement> tail)
            throws InvalidThunkExpressionException {
        switch (attribute) {
            case SimpleAttribute simpleAttribute -> {
                if (!tail.isEmpty()) {
                    throw new InvalidThunkExpressionException("Path goes over non-existing attribute");
                }
                return JOOQUtils.resolveField(currentAlias, simpleAttribute);
            }
            case CompositeAttribute compositeAttribute -> {
                if (tail.isEmpty()) {
                    throw new InvalidThunkExpressionException("Path can not end in a composite attribute");
                }
                var pathElement = tail.getFirst();
                var newTail = tail.subList(1, tail.size());
                var name = getPathElementName(pathElement);

                // Check if pathElement references existing attribute
                Optional<Attribute> maybeAttribute = compositeAttribute.getAttributeByName(AttributeName.of(name));
                if (maybeAttribute.isPresent()) {
                    var subAttribute = maybeAttribute.get();
                    return handleAttribute(currentAlias, subAttribute, newTail);
                } else {
                    // pathElement seems to reference a non-existing attribute
                    throw new InvalidThunkExpressionException("Path element %s does not exist on attribute %s".formatted(name, compositeAttribute.getName()));
                }
            }
        }
    }

    private Field<?> handleRelation(@NonNull Relation relation, @NonNull List<SymbolicReference.PathElement> tail, @NonNull JOOQContext context) {
        if (tail.isEmpty()) {
            throw new InvalidThunkExpressionException("Path can not end in a relation");
        }
        // check variable access for *-to-many relations
        if (relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation) {
            var pathElement = tail.getFirst();
            if (pathElement instanceof VariablePathElement variable) {
                // add variable to context and check whether variable is unique
                if (!context.addVariable(variable)) {
                    throw new InvalidThunkExpressionException(
                            "Variable %s is not unique".formatted(variable.getVariable().getName()));
                }
                tail = tail.subList(1, tail.size());
            } else {
                throw new InvalidThunkExpressionException("VariablePathElement is required in traversing a *-to-many relation, got '%s' of type %s."
                        .formatted(pathElement, pathElement.getClass().getSimpleName()));
            }
        }
        context.getJoinCollection().addRelation(context.getApplication(), relation);
        return handlePath(context.getApplication().getRelationTargetEntity(relation), tail, context);
    }

    @Override
    public Condition visit(Variable variable, JOOQContext context) throws InvalidThunkExpressionException {
        throw new InvalidThunkExpressionException("Variable %s is not supported".formatted(variable.getName()));
    }

    private static String getPathElementName(@NonNull PathElement elem) throws InvalidThunkExpressionException {
        if (elem instanceof StringPathElement string) {
            return ((Scalar<String>) string.getPath()).getValue();
        }
        throw new InvalidThunkExpressionException("cannot traverse symbolic reference using path element type %s, expected a %s"
                .formatted(elem.getClass().getSimpleName(), StringPathElement.class.getSimpleName()));
    }

    @Value
    public static class JOOQContext {

        @NonNull Application application;
        @NonNull Entity entity;

        @Getter(AccessLevel.PRIVATE)
        @NonNull JoinCollection joinCollection;

        @Getter(AccessLevel.NONE)
        Set<String> variables = new HashSet<>();

        public JOOQContext(@NonNull Application application, @NonNull Entity entity) {
            this.application = application;
            this.entity = entity;
            this.joinCollection = new JoinCollection(entity.getTable());
        }

        public TableName getRootTable() {
            return joinCollection.getRootTable();
        }

        public TableName getRootAlias() {
            return joinCollection.getRootAlias();
        }

        private boolean addVariable(VariablePathElement variable) {
            if (variable.getVariable().getName().equals("_")) {
                return true;
            } else {
                return variables.add(variable.getVariable().getName());
            }
        }
    }
}

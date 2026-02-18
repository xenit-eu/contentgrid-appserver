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
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison.ContentGridPrefixSearch;
import com.contentgrid.appserver.query.engine.jooq.JOOQThunkExpressionVisitor.JOOQContext;
import com.contentgrid.thunx.predicates.model.FunctionExpression;
import com.contentgrid.thunx.predicates.model.FunctionExpression.Operator;
import com.contentgrid.thunx.predicates.model.ListValue;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SetValue;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.SymbolicReference.PathElement;
import com.contentgrid.thunx.predicates.model.SymbolicReference.StringPathElement;
import com.contentgrid.thunx.predicates.model.SymbolicReference.VariablePathElement;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.predicates.model.ThunkExpressionVisitor;
import com.contentgrid.thunx.predicates.model.Variable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Param;
import org.jooq.impl.DSL;
import org.jooq.impl.QOM.Array;

import static com.contentgrid.appserver.query.engine.jooq.JOOQUtils.generateFTSCondition;
import static java.util.Locale.ENGLISH;

@Slf4j
@RequiredArgsConstructor
public class JOOQThunkExpressionVisitor implements ThunkExpressionVisitor<Field<?>, JOOQContext> {

    private static final List<Predicate<DataType<?>>> DATATYPES = List.of(
            DataType::isString, DataType::isNumeric, DataType::isBoolean, DataType::isUUID,
            DataType::isTime, DataType::isTimeWithTimeZone, DataType::isTimestamp,
            DataType::isTimestampWithTimeZone, DataType::isDate, DataType::isInterval,
            DataType::isBinary
    );
    private static final List<Predicate<DataType<?>>> SORTABLE_DATATYPES = List.of(
            DataType::isNumeric, DataType::isUUID, DataType::isTime, DataType::isTimeWithTimeZone,
            DataType::isTimestamp, DataType::isTimestampWithTimeZone, DataType::isDate, DataType::isInterval
    );

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
    public Field<?> visit(FunctionExpression<?> functionExpression, JOOQContext context)
            throws InvalidThunkExpressionException {
        Field<?> result = switch (functionExpression.getOperator()) {
            case EQUALS -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (!sameType(left, right)) {
                    logWarning(functionExpression.getOperator(), left, right);
                    yield DSL.falseCondition();
                }
                if (left.getDataType().isString()) {
                    left = JOOQUtils.normalize(left);
                    right = JOOQUtils.normalize(right);
                }
                yield ((Field<Object>) left).equal((Field<Object>) right);
            }
            case NOT_EQUAL_TO -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (!sameType(left, right)) {
                    logWarning(functionExpression.getOperator(), left, right);
                    yield DSL.falseCondition();
                }
                if (left.getDataType().isString()) {
                    left = JOOQUtils.normalize(left);
                    right = JOOQUtils.normalize(right);
                }
                yield ((Field<Object>) left).notEqual((Field<Object>) right);
            }
            case GREATER_THAN -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (!sortableType(left, right)) {
                    logWarning(functionExpression.getOperator(), left, right);
                    yield DSL.falseCondition();
                }
                yield ((Field<Object>) left).greaterThan((Field<Object>) right);
            }
            case GREATER_THAN_OR_EQUAL_TO -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (!sortableType(left, right)) {
                    logWarning(functionExpression.getOperator(), left, right);
                    yield DSL.falseCondition();
                }
                yield ((Field<Object>) left).greaterOrEqual((Field<Object>) right);
            }
            case LESS_THAN -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (!sortableType(left, right)) {
                    logWarning(functionExpression.getOperator(), left, right);
                    yield DSL.falseCondition();
                }
                yield ((Field<Object>) left).lessThan((Field<Object>) right);
            }
            case LESS_THEN_OR_EQUAL_TO -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (!sortableType(left, right)) {
                    logWarning(functionExpression.getOperator(), left, right);
                    yield DSL.falseCondition();
                }
                yield ((Field<Object>) left).lessOrEqual((Field<Object>) right);
            }
            case IN -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (right instanceof Array<?> array) {
                    if (left.getDataType().isString()) {
                        left = JOOQUtils.normalize(left);
                        // right side is already normalized in the visit function if needed
                    }

                    var leftFinal = left; // final for lambda
                    var elements = array.$elements().stream()
                            .filter(field -> sameType(leftFinal, field))
                            .toArray();

                    yield ((Field<Object>) left).eq(DSL.any(DSL.array(elements)));
                } else {
                    // Non-array -> always false
                    logWarning(functionExpression.getOperator(), left, right);
                    yield DSL.falseCondition();
                }
            }
            case AND -> {
                yield DSL.and(functionExpression.getTerms().stream()
                        .map(expression -> expression.accept(this, context))
                        .map(field -> {
                            if (field instanceof Condition condition) {
                                return condition;
                            } else if (field.getDataType().isBoolean()) {
                                return DSL.condition((Field<Boolean>) field);
                            }
                            logWarning(functionExpression.getOperator(), field);
                            return DSL.falseCondition();
                        })
                        .toList());
            }
            case OR -> {
                yield DSL.or(functionExpression.getTerms().stream()
                        .map(expression -> expression.accept(this, context))
                        .map(field -> {
                            if (field instanceof Condition condition) {
                                return condition;
                            } else if (field.getDataType().isBoolean()) {
                                return DSL.condition((Field<Boolean>) field);
                            }
                            logWarning(functionExpression.getOperator(), field);
                            return DSL.falseCondition();
                        })
                        .toList());
            }
            case NOT -> {
                assertOneTerm(functionExpression.getTerms());
                var field = functionExpression.getTerms().getFirst().accept(this, context);
                if (field instanceof Condition condition) {
                    yield DSL.not(condition);
                } else if (field.getDataType().isBoolean()) {
                    yield DSL.condition(DSL.not((Field<Boolean>) field));
                }
                logWarning(functionExpression.getOperator(), field);
                yield DSL.falseCondition();
            }
            case PLUS -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (left.getDataType().isNumeric() && right.getDataType().isNumeric()) {
                    yield left.add(right);
                }
                throw new InvalidThunkExpressionException("Terms should be numeric");
            }
            case MULTIPLY -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (left.getDataType().isNumeric() && right.getDataType().isNumeric()) {
                    yield left.times((Field<? extends Number>) right);
                }
                throw new InvalidThunkExpressionException("Terms should be numeric");
            }
            case MINUS -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (left.getDataType().isNumeric() && right.getDataType().isNumeric()) {
                    yield left.minus(right);
                }
                throw new InvalidThunkExpressionException("Terms should be numeric");
            }
            case DIVIDE -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (left.getDataType().isNumeric() && right.getDataType().isNumeric()) {
                    yield left.divide((Field<? extends Number>) right);
                }
                throw new InvalidThunkExpressionException("Terms should be numeric");
            }
            case MODULUS -> {
                assertTwoTerms(functionExpression.getTerms());
                var left = functionExpression.getTerms().getFirst().accept(this, context);
                var right = functionExpression.getTerms().getLast().accept(this, context);
                if (left.getDataType().isNumeric() && right.getDataType().isNumeric()) {
                    yield left.modulo((Field<? extends Number>) right);
                }
                throw new InvalidThunkExpressionException("Terms should be numeric");
            }
            case CUSTOM -> {
                switch (functionExpression) {
                    case ContentGridPrefixSearch contentGridPrefixSearch -> {
                        var left = contentGridPrefixSearch.getLeftTerm().accept(this, context);
                        var right = contentGridPrefixSearch.getRightTerm().accept(this, context);
                        if (!left.getDataType().isString() || !right.getDataType().isString()) {
                            logWarning("cg_prefix_search", left, right);
                            yield DSL.falseCondition();
                        }
                        var leftField = JOOQUtils.prefixSearchNormalize(left);
                        var rightField = JOOQUtils.prefixSearchNormalize(right);
                        yield leftField.startsWith(rightField);
                    }
                    case StringComparison.ContentGridFullTextSearch contentGridFullTextSearch -> {
                        var left = contentGridFullTextSearch.getLeftTerm().accept(this, context);
                        var right = contentGridFullTextSearch.getRightTerm().accept(this, context);

                        if (!left.getDataType().isString() || !right.getDataType().isString()) {
                            logWarning("cg_fulltext_search", left, right);
                            yield DSL.falseCondition();
                        }

                        var leftField = JOOQUtils.prefixSearchNormalize(left);
                        var rightField = JOOQUtils.prefixSearchNormalize(right);

                        var locale = contentGridFullTextSearch.getLocale();
                        var language = locale.getDisplayLanguage(ENGLISH);

                        yield generateFTSCondition(leftField, rightField, language);
                    }
                    default -> throw new InvalidThunkExpressionException(
                            "Function expression with type %s is not supported.".formatted(
                                    functionExpression.getClass().getSimpleName()));
                }

            }
        };

        if (result instanceof Condition condition) {
            return context.getJoinCollection().collect(condition); // TODO: only collect last condition
        }
        return result;
    }

    private static void assertOneTerm(List<? extends ThunkExpression<?>> terms) throws InvalidThunkExpressionException {
        if (terms.size() != 1) {
            throw new InvalidThunkExpressionException("Operation requires 1 parameter.");
        }
    }

    private static void assertTwoTerms(List<? extends ThunkExpression<?>> terms)
            throws InvalidThunkExpressionException {
        if (terms.size() != 2) {
            throw new InvalidThunkExpressionException("Operation requires 2 parameters.");
        }
    }

    private static boolean sameType(Field<?> left, Field<?> right) {
        if (Objects.equals(left.getDataType(), right.getDataType())) {
            return true;
        } else {
            return DATATYPES.stream().anyMatch(predicate ->
                    predicate.test(left.getDataType()) && predicate.test(right.getDataType()));
        }
    }

    private static boolean sortableType(Field<?> left, Field<?> right) {
        return SORTABLE_DATATYPES.stream().anyMatch(predicate ->
                predicate.test(left.getDataType()) && predicate.test(right.getDataType()));
    }

    private void logWarning(Operator operator, Field<?> left, Field<?> right) {
        logWarning(operator.getKey(), left, right);
    }

    private void logWarning(String operator, Field<?> left, Field<?> right) {
        log.warn("Operator '{}' is not supported between '{}' and '{}', evaluating condition as false",
                operator, left.getDataType().getTypeName(), right.getDataType().getTypeName());
    }

    private void logWarning(Operator operator, Field<?> field) {
        logWarning(operator.getKey(), field);
    }

    private void logWarning(String operator, Field<?> field) {
        log.warn("Operator '{}' does not support type '{}', evaluating condition as false", operator, field.getDataType().getTypeName());
    }

    @Override
    public Field<?> visit(SymbolicReference symbolicReference, JOOQContext context)
            throws InvalidThunkExpressionException {
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
        throw new InvalidThunkExpressionException(
                "Path element %s does not exist on entity %s".formatted(name, entity));
    }

    private Field<?> handleAttribute(@NonNull TableName currentAlias, @NonNull Attribute attribute,
            @NonNull List<PathElement> tail)
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
                    throw new InvalidThunkExpressionException(
                            "Path element %s does not exist on attribute %s".formatted(name,
                                    compositeAttribute.getName()));
                }
            }
        }
    }

    private Field<?> handleRelation(@NonNull Relation relation, @NonNull List<PathElement> tail,
            @NonNull JOOQContext context) {
        if (tail.isEmpty()) {
            throw new InvalidThunkExpressionException("Path can not end in a relation");
        }

        // Track the relation in the path for alias reuse
        context.pushRelation(relation);

        // check variable access for *-to-many relations
        if (relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation) {
            var pathElement = tail.getFirst();
            if (pathElement instanceof VariablePathElement variable) {
                // Validate and track the variable
                context.addVariable(variable);
                tail = tail.subList(1, tail.size());
            } else {
                throw new InvalidThunkExpressionException(
                        "VariablePathElement is required in traversing a *-to-many relation, got '%s' of type %s."
                                .formatted(pathElement, pathElement.getClass().getSimpleName()));
            }
        }

        try {
            context.getJoinCollection().addRelation(context.getApplication(), relation, context.getCurrentRelationPath());
            return handlePath(context.getApplication().getRelationTargetEntity(relation), tail, context);
        } finally {
            context.popRelation();
        }
    }

    @Override
    public Condition visit(Variable variable, JOOQContext context) throws InvalidThunkExpressionException {
        throw new InvalidThunkExpressionException("Variable %s is not supported".formatted(variable.getName()));
    }

    @Override
    public Field<?> visit(SetValue setValue, JOOQContext context) {
        return getArray(context, setValue.getValue().stream());
    }

    private Field<Object[]> getArray(JOOQContext context, Stream<? extends ThunkExpression<?>> stream) {
        var values = stream.map(thunkExpression -> {
            if (Objects.requireNonNull(thunkExpression) instanceof Scalar<?> scalar) {
                Field<?> field = visit(scalar, context);
                if (field.getDataType().isString()) {
                    field = JOOQUtils.normalize(field);
                }
                return field;
            }
            throw new InvalidThunkExpressionException("Unknown thunk expression: " + thunkExpression);

        }).toArray();
        return DSL.array(values);
    }

    @Override
    public Field<?> visit(ListValue listValue, JOOQContext context) {
        return getArray(context, listValue.getValue().stream());
    }

    private static String getPathElementName(@NonNull PathElement elem) throws InvalidThunkExpressionException {
        if (elem instanceof StringPathElement string) {
            return ((Scalar<String>) string.getPath()).getValue();
        }
        throw new InvalidThunkExpressionException(
                "cannot traverse symbolic reference using path element type %s, expected a %s"
                        .formatted(elem.getClass().getSimpleName(), StringPathElement.class.getSimpleName()));
    }

    @Value
    public static class JOOQContext {

        @NonNull
        Application application;
        @NonNull
        Entity entity;

        @Getter(AccessLevel.PRIVATE)
        @NonNull
        JoinCollection joinCollection;

        @Getter(AccessLevel.NONE)
        Map<String, List<PathElement>> variableToRelationPath = new HashMap<>();

        @Getter(AccessLevel.NONE)
        List<PathElement> relationPath = new ArrayList<>();

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

        private void addVariable(VariablePathElement variable) {
            String variableName = variable.getVariable().getName();

            // Unnamed variable is always distinct from any other variable
            if (variableName.equals("_")) {
                relationPath.add(SymbolicReference.pathVar(UUID.randomUUID().toString()));
                return;
            }

            // Check if this variable has been used before
            if (variableToRelationPath.containsKey(variableName)) {
                // Variable already exists - verify it's used with the same relation path
                var existingPath = variableToRelationPath.get(variableName);
                if (!existingPath.equals(relationPath)) {
                    throw new InvalidThunkExpressionException(
                            "Variable %s cannot be reused across different relation paths. First used at path %s, now used at path %s"
                            .formatted(variableName, existingPath, relationPath));
                }
                // Same path - OK to reuse
            } else {
                // New variable - record its relation path
                variableToRelationPath.put(variableName, new ArrayList<>(relationPath));
            }

            relationPath.add(variable);
        }

        public void pushRelation(Relation relation) {
            relationPath.add(SymbolicReference.path(relation.getSourceEndPoint().getName().getValue()));
        }

        public void popRelation() {
            var element = relationPath.removeLast();
            if (element instanceof VariablePathElement) {
                relationPath.removeLast();
            }
        }

        public List<PathElement> getCurrentRelationPath() {
            return new ArrayList<>(relationPath);
        }
    }
}

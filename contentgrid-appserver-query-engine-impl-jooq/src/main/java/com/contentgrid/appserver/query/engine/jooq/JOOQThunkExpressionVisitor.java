package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
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
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.predicates.model.ThunkExpressionVisitor;
import com.contentgrid.thunx.predicates.model.Variable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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

    /**
     * Create a {@link Condition} from the given {@link ThunkExpression}.
     * This method should be used instead of any {@code visit} methods.
     *
     * @param expression The expression to convert.
     * @param context Context for this visitor.
     * @return The converted {@link Condition} that can be used in the where clause of queries.
     */
    public Condition createCondition(ThunkExpression<Boolean> expression, JOOQContext context) {
        var condition = DSL.condition((Field<Boolean>) expression.accept(this, context));

        // In case the expression did not contain an OR expression, we still need to add the joins
        return context.addJoins(condition);
    }

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
        return switch (functionExpression.getOperator()) {
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
                // AND is expressed in OPA as different expressions within the same rule body.
                // Variables are local to the rule body, which means that they can be reused in different terms.
                // For to-many relations: this means both `ANY(X) AND ANY(Y)` and `ANY(X AND Y)` can be expressed,
                // where the latter interpretation is obtained by reusing variables.
                var conditions = new ArrayList<Condition>();
                for (var expression : functionExpression.getTerms()) {
                    var field = expression.accept(this, context);

                    if (!field.getDataType().isBoolean()) {
                        logWarning(functionExpression.getOperator(), field);
                        yield DSL.falseCondition();
                    }

                    conditions.add(DSL.condition((Field<Boolean>) field));
                }
                yield DSL.and(conditions);
            }
            case OR -> {
                // OR is expressed in OPA as different rules, variables are local to the rule body.
                // As such, it is not possible to reuse a variable across different terms.
                // Collect the joins for each term separately, and prevent usage of variables across terms.
                // For to-many relations: this means only `ANY(X) OR ANY(Y)` is valid,
                // and `ANY(X OR Y)` can not be expressed (but it is mathematically equivalent to the former).
                var conditions = new ArrayList<Condition>();
                for (var expression : functionExpression.getTerms()) {
                    var newContext = context.newContext();
                    var field = expression.accept(this, newContext);

                    if (!field.getDataType().isBoolean()) {
                        // Evaluate as false
                        logWarning(functionExpression.getOperator(), field);
                        continue;
                    }

                    var condition = DSL.condition((Field<Boolean>) field);
                    conditions.add(newContext.addJoins(condition));
                    context.merge(newContext);
                }
                yield DSL.or(conditions);
            }
            case NOT -> {
                // NOT in OPA can only occur in simple expressions after partial evaluation
                // (see https://www.openpolicyagent.org/docs/filtering/fragment#not-expressions).
                // For to-many relations: this means only `ANY(NOT(X))` is valid,
                // and `NOT(ANY(X))` can not be expressed.
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
        return context.resolvePath(symbolicReference.getPath());
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

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JOOQContext {

        @NonNull
        private final JOOQSymbolicReferenceResolver symbolicReferenceResolver;

        public JOOQContext(@NonNull Application application, @NonNull Entity entity) {
            this(new JOOQSymbolicReferenceResolver(application, entity.getName()));
        }

        public JOOQContext newContext() {
            return new JOOQContext(symbolicReferenceResolver.newResolver());
        }

        public void merge(JOOQContext other) {
            symbolicReferenceResolver.merge(other.symbolicReferenceResolver);
        }

        public TableName getRootTable() {
            return symbolicReferenceResolver.getRootEntity().getTable();
        }

        public TableName getRootAlias() {
            return symbolicReferenceResolver.getRootAlias();
        }

        public Field<?> resolvePath(List<PathElement> path) {
            return symbolicReferenceResolver.resolvePath(path);
        }

        public Condition addJoins(Condition condition) {
            return symbolicReferenceResolver.collect(condition);
        }
    }
}

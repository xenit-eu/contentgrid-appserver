package com.contentgrid.appserver.query.engine.jooq.thunk;

import static com.contentgrid.appserver.query.engine.jooq.JOOQUtils.generateFTSCondition;
import static java.util.Locale.ENGLISH;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison.ContentGridPrefixSearch;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import com.contentgrid.appserver.query.engine.jooq.thunk.JOOQSymbolicReferenceResolver.ScopedConditions;
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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.QOM.Array;

public class JOOQThunkExpressionResolver {

    private static final JOOQThunkExpressionResolverVisitor VISITOR = new JOOQThunkExpressionResolverVisitor();

    /**
     * Create a {@link Condition} from the given {@link ThunkExpression}.
     *
     * @param expression The expression to resolve.
     * @param context Context for this visitor.
     * @return The resolved {@link Condition} that can be used in the where clause of queries.
     */
    public Condition resolveExpression(ThunkExpression<Boolean> expression, JOOQContext context) {
        return VISITOR.resolveWithJoins(expression, context);
    }


    /**
     * A field and its dependency on joined tables in the current scope. Keep the dependency even when
     * an operator reduces the field to a constant: resolving a relation still requires a related row.
     * Mapping and combining fields propagate this information without mutating the resolution context.
     */
    private record ResolvedExpression(Field<?> field, boolean requiresJoins) {

        private static ResolvedExpression rootScoped(Field<?> field) {
            return new ResolvedExpression(field, false);
        }

        private ResolvedExpression map(Function<Field<?>, Field<?>> operator) {
            return new ResolvedExpression(operator.apply(field), requiresJoins);
        }

        private ResolvedExpression combine(ResolvedExpression other, BiFunction<Field<?>, Field<?>, Field<?>> operator) {
            return new ResolvedExpression(operator.apply(field, other.field), requiresJoins || other.requiresJoins);
        }
    }

    @Slf4j
    private static class JOOQThunkExpressionResolverVisitor implements
            ThunkExpressionVisitor<ResolvedExpression, JOOQContext> {

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

        private Condition resolveWithJoins(ThunkExpression<?> expression, JOOQContext context) {
            return context.wrapConjuncts(joinContext -> {
                var outerConditions = new ArrayList<Condition>();
                var innerConditions = new ArrayList<Condition>();
                for (var conjunct : conjuncts(expression).toList()) {
                    // Conjuncts share the join cache and variable scope, but carry their own dependencies.
                    var resolved = conjunct.accept(this, joinContext);
                    var field = resolved.field();
                    if (!field.getDataType().isBoolean()) {
                        logWarning(Operator.AND, field);
                        return new ScopedConditions(List.of(), List.of(DSL.falseCondition()));
                    }
                    var condition = DSL.condition((Field<Boolean>) field);
                    if (resolved.requiresJoins()) {
                        innerConditions.add(condition);
                    } else {
                        outerConditions.add(condition);
                    }
                }
                return new ScopedConditions(outerConditions, innerConditions);
            });
        }

        private static Stream<ThunkExpression<?>> conjuncts(ThunkExpression<?> expression) {
            if (expression instanceof FunctionExpression<?> function && function.getOperator() == Operator.AND) {
                return function.getTerms().stream().flatMap(JOOQThunkExpressionResolverVisitor::conjuncts);
            }
            // Do not distribute AND through OR or NOT: those have different scoping semantics.
            return Stream.of(expression);
        }

        @Override
        public ResolvedExpression visit(Scalar<?> scalar, JOOQContext context) throws InvalidThunkExpressionException {
            if (scalar.getValue() == null) {
                // Special case, the value is null
                throw new InvalidThunkExpressionException("null values are not supported");
            } else if (Number.class.equals(scalar.getResultType())) {
                // Number is not supported
                return ResolvedExpression.rootScoped(DSL.value(scalar.getValue(), scalar.getValue().getClass()));
            }
            return ResolvedExpression.rootScoped(DSL.value(scalar.getValue(), scalar.getResultType()));
        }

        @Override
        public ResolvedExpression visit(FunctionExpression<?> functionExpression, JOOQContext context)
                throws InvalidThunkExpressionException {
            return switch (functionExpression.getOperator()) {
                case EQUALS -> resolveBinary(functionExpression, context, (left, right) -> {
                    if (!sameType(left, right)) {
                        logWarning(functionExpression.getOperator(), left, right);
                        return DSL.falseCondition();
                    }
                    if (left.getDataType().isString()) {
                        left = JOOQUtils.normalize(left);
                        right = JOOQUtils.normalize(right);
                    }
                    return ((Field<Object>) left).equal((Field<Object>) right);
                });
                case NOT_EQUAL_TO -> resolveBinary(functionExpression, context, (left, right) -> {
                    if (!sameType(left, right)) {
                        logWarning(functionExpression.getOperator(), left, right);
                        return DSL.falseCondition();
                    }
                    if (left.getDataType().isString()) {
                        left = JOOQUtils.normalize(left);
                        right = JOOQUtils.normalize(right);
                    }
                    return ((Field<Object>) left).notEqual((Field<Object>) right);
                });
                case GREATER_THAN -> resolveBinary(functionExpression, context, (left, right) -> {
                    if (!sortableType(left, right)) {
                        logWarning(functionExpression.getOperator(), left, right);
                        return DSL.falseCondition();
                    }
                    return ((Field<Object>) left).greaterThan((Field<Object>) right);
                });
                case GREATER_THAN_OR_EQUAL_TO -> resolveBinary(functionExpression, context, (left, right) -> {
                    if (!sortableType(left, right)) {
                        logWarning(functionExpression.getOperator(), left, right);
                        return DSL.falseCondition();
                    }
                    return ((Field<Object>) left).greaterOrEqual((Field<Object>) right);
                });
                case LESS_THAN -> resolveBinary(functionExpression, context, (left, right) -> {
                    if (!sortableType(left, right)) {
                        logWarning(functionExpression.getOperator(), left, right);
                        return DSL.falseCondition();
                    }
                    return ((Field<Object>) left).lessThan((Field<Object>) right);
                });
                case LESS_THEN_OR_EQUAL_TO -> resolveBinary(functionExpression, context, (left, right) -> {
                    if (!sortableType(left, right)) {
                        logWarning(functionExpression.getOperator(), left, right);
                        return DSL.falseCondition();
                    }
                    return ((Field<Object>) left).lessOrEqual((Field<Object>) right);
                });
                case IN -> resolveBinary(functionExpression, context, (left, right) -> {
                    if (right instanceof Array<?> array) {
                        if (left.getDataType().isString()) {
                            left = JOOQUtils.normalize(left);
                            // right side is already normalized in the visit function if needed
                        }

                        var leftFinal = left; // final for lambda
                        var elements = array.$elements().stream()
                                .filter(field -> sameType(leftFinal, field))
                                .toArray();

                        return ((Field<Object>) left).eq(DSL.any(DSL.array(elements)));
                    } else {
                        // Non-array -> always false
                        logWarning(functionExpression.getOperator(), left, right);
                        return DSL.falseCondition();
                    }
                });
                case AND -> {
                    // AND is expressed in OPA as different expressions within the same rule body.
                    // Variables are local to the rule body, which means that they can be reused in different terms.
                    // For to-many relations: this means both `ANY(X) AND ANY(Y)` and `ANY(X AND Y)` can be expressed,
                    // where the latter interpretation is obtained by reusing variables.
                    var conditions = new ArrayList<Condition>();
                    var requiresJoins = false;
                    for (var expression : functionExpression.getTerms()) {
                        var resolved = expression.accept(this, context);
                        var field = resolved.field();
                        requiresJoins |= resolved.requiresJoins();

                        if (!field.getDataType().isBoolean()) {
                            logWarning(functionExpression.getOperator(), field);
                            yield new ResolvedExpression(DSL.falseCondition(), requiresJoins);
                        }

                        conditions.add(DSL.condition((Field<Boolean>) field));
                    }
                    yield new ResolvedExpression(DSL.and(conditions), requiresJoins);
                }
                case OR -> {
                    // OR is expressed in OPA as different rules, variables are local to the rule body.
                    // As such, it is not possible to reuse a variable across different terms.
                    // Collect the joins for each term separately, and prevent usage of variables across terms.
                    // For to-many relations: this means only `ANY(X) OR ANY(Y)` is valid,
                    // and `ANY(X OR Y)` can not be expressed (but it is mathematically equivalent to the former).
                    var conditions = new ArrayList<Condition>();
                    for (var expression : functionExpression.getTerms()) {
                        conditions.add(resolveWithJoins(expression, context));
                    }
                    // Each branch has already wrapped its own joins, so none escape into this scope.
                    yield ResolvedExpression.rootScoped(DSL.or(conditions));
                }
                case NOT -> {
                    // NOT in OPA can only occur in simple expressions after partial evaluation
                    // (see https://www.openpolicyagent.org/docs/filtering/fragment#not-expressions).
                    // For to-many relations: this means only `ANY(NOT(X))` is valid,
                    // and `NOT(ANY(X))` can not be expressed.
                    assertOneTerm(functionExpression.getTerms());
                    var resolved = functionExpression.getTerms().getFirst().accept(this, context);
                    yield resolved.map(field -> {
                        if (field instanceof Condition condition) {
                            return DSL.not(condition);
                        } else if (field.getDataType().isBoolean()) {
                            return DSL.condition(DSL.not((Field<Boolean>) field));
                        }
                        logWarning(functionExpression.getOperator(), field);
                        return DSL.falseCondition();
                    });
                }
                case PLUS -> resolveBinary(functionExpression, context, (left, right) -> {
                    if (left.getDataType().isNumeric() && right.getDataType().isNumeric()) {
                        return left.add(right);
                    }
                    throw new InvalidThunkExpressionException("Terms should be numeric");
                });
                case MULTIPLY -> resolveBinary(functionExpression, context, (left, right) -> {
                    if (left.getDataType().isNumeric() && right.getDataType().isNumeric()) {
                        return left.times((Field<? extends Number>) right);
                    }
                    throw new InvalidThunkExpressionException("Terms should be numeric");
                });
                case MINUS -> resolveBinary(functionExpression, context, (left, right) -> {
                    if (left.getDataType().isNumeric() && right.getDataType().isNumeric()) {
                        return left.minus(right);
                    }
                    throw new InvalidThunkExpressionException("Terms should be numeric");
                });
                case DIVIDE -> resolveBinary(functionExpression, context, (left, right) -> {
                    if (left.getDataType().isNumeric() && right.getDataType().isNumeric()) {
                        return left.divide((Field<? extends Number>) right);
                    }
                    throw new InvalidThunkExpressionException("Terms should be numeric");
                });
                case MODULUS -> resolveBinary(functionExpression, context, (left, right) -> {
                    if (left.getDataType().isNumeric() && right.getDataType().isNumeric()) {
                        return left.modulo((Field<? extends Number>) right);
                    }
                    throw new InvalidThunkExpressionException("Terms should be numeric");
                });
                case CUSTOM -> switch (functionExpression) {
                    case ContentGridPrefixSearch contentGridPrefixSearch ->
                            resolveBinary(contentGridPrefixSearch, context, (left, right) -> {
                                if (!left.getDataType().isString() || !right.getDataType().isString()) {
                                    logWarning("cg_prefix_search", left, right);
                                    return DSL.falseCondition();
                                }
                                var leftField = JOOQUtils.prefixSearchNormalize(left);
                                var rightField = JOOQUtils.prefixSearchNormalize(right);
                                return leftField.startsWith(rightField);
                            });
                    case StringComparison.ContentGridFullTextSearch contentGridFullTextSearch ->
                            resolveBinary(contentGridFullTextSearch, context, (left, right) -> {
                                if (!left.getDataType().isString() || !right.getDataType().isString()) {
                                    logWarning("cg_fulltext_search", left, right);
                                    return DSL.falseCondition();
                                }

                                var leftField = JOOQUtils.prefixSearchNormalize(left);
                                var rightField = JOOQUtils.prefixSearchNormalize(right);

                                var locale = contentGridFullTextSearch.getLocale();
                                var language = locale.getDisplayLanguage(ENGLISH);

                                return generateFTSCondition(leftField, rightField, language);
                            });
                    default -> throw new InvalidThunkExpressionException(
                            "Function expression with type %s is not supported.".formatted(
                                    functionExpression.getClass().getSimpleName()));
                };
            };
        }

        private ResolvedExpression resolveBinary(FunctionExpression<?> expression, JOOQContext context,
                BiFunction<Field<?>, Field<?>, Field<?>> operator) {
            assertTwoTerms(expression.getTerms());
            var left = expression.getTerms().getFirst().accept(this, context);
            var right = expression.getTerms().getLast().accept(this, context);
            return left.combine(right, operator);
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
        public ResolvedExpression visit(SymbolicReference symbolicReference, JOOQContext context)
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
        public ResolvedExpression visit(Variable variable, JOOQContext context) throws InvalidThunkExpressionException {
            throw new InvalidThunkExpressionException("Variable %s is not supported".formatted(variable.getName()));
        }

        @Override
        public ResolvedExpression visit(SetValue setValue, JOOQContext context) {
            return ResolvedExpression.rootScoped(getArray(context, setValue.getValue().stream()));
        }

        private Field<Object[]> getArray(JOOQContext context, Stream<? extends ThunkExpression<?>> stream) {
            var values = stream.map(thunkExpression -> {
                if (Objects.requireNonNull(thunkExpression) instanceof Scalar<?> scalar) {
                    Field<?> field = visit(scalar, context).field();
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
        public ResolvedExpression visit(ListValue listValue, JOOQContext context) {
            return ResolvedExpression.rootScoped(getArray(context, listValue.getValue().stream()));
        }

    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JOOQContext {

        @NonNull
        private final JOOQSymbolicReferenceResolver symbolicReferenceResolver;

        public JOOQContext(@NonNull Application application, @NonNull Entity entity) {
            this(new JOOQSymbolicReferenceResolver(application, entity.getName()));
        }

        public TableName getRootTable() {
            return symbolicReferenceResolver.getRootEntity().getTable();
        }

        public TableName getRootAlias() {
            return symbolicReferenceResolver.getRootAlias();
        }

        private ResolvedExpression resolvePath(List<PathElement> path) {
            var field = symbolicReferenceResolver.resolvePath(path);
            return new ResolvedExpression(field,
                    !field.getQualifiedName().qualifier().equals(DSL.name(getRootAlias().getValue())));
        }

        private Condition wrapConjuncts(Function<JOOQContext, ScopedConditions> conditionFunction) {
            return symbolicReferenceResolver.wrapConjuncts(resolver ->
                    conditionFunction.apply(new JOOQContext(resolver))
            );
        }
    }
}

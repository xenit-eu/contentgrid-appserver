package com.contentgrid.appserver.rest.automations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.domain.automations.AutomationsModel.AutomationModel;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.ListValue;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SetValue;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AutomationModelPermissionEvaluatorTest {
    private static final AutomationModel AUTOMATION_1 = AutomationModel.builder()
            .id(UUID.randomUUID().toString())
            .system("my-system")
            .name("test1")
            .data(Map.of("text", "hello world!"))
            .annotations(List.of())
            .build();

    private static final AutomationModel AUTOMATION_2 = AutomationModel.builder()
            .id(UUID.randomUUID().toString())
            .system("other-system")
            .name("test2")
            .data(Map.of("color", Map.of("red", 255, "green", 0, "blue", 0)))
            .annotations(List.of())
            .build();

    private static final SymbolicReference SYSTEM_REFERENCE = SymbolicReference.of("entity", path -> path.string("system"));

    private static final AutomationModelPermissionEvaluator PERMISSION_EVALUATOR = new AutomationModelPermissionEvaluator();

    public static Stream<Arguments> alwaysTruePolicy() {
        return Stream.of(
                Arguments.of(Scalar.of(true)), // true
                Arguments.of(Comparison.areEqual(Scalar.of(true), Scalar.of(true))), // true = true
                Arguments.of(Comparison.areEqual(Scalar.of(5), Scalar.of(5))), // 5 = 5
                Arguments.of(Comparison.notEqual(Scalar.of(true), Scalar.of(false))), // true != false
                Arguments.of(LogicalOperation.conjunction(Scalar.of(true), Scalar.of(true))), // true AND true
                Arguments.of(LogicalOperation.disjunction(Scalar.of(false), Scalar.of(true))), // false OR true
                Arguments.of(LogicalOperation.negation(Scalar.of(false))), // NOT false
                Arguments.of(Comparison.in(SYSTEM_REFERENCE, new SetValue(Set.of(
                        Scalar.of("my-system"), Scalar.of("other-system")
                )))), // entity.system in set(my-system, other-system)
                Arguments.of(Comparison.in(SYSTEM_REFERENCE, new ListValue(List.of(
                        Scalar.of("my-system"), Scalar.of("other-system")
                )))) // entity.system in [my-system, other-system]
        );
    }

    public static Stream<Arguments> alwaysFalsePolicy() {
        return Stream.of(
                Arguments.of(Scalar.of(false)), // false
                Arguments.of(Comparison.areEqual(Scalar.of(true), Scalar.of(false))), // true = false
                Arguments.of(Comparison.notEqual(Scalar.of(true), Scalar.of(true))), // true != true
                Arguments.of(Comparison.notEqual(Scalar.of(5), Scalar.of(5))), // 5 != 5
                Arguments.of(LogicalOperation.conjunction(Scalar.of(false), Scalar.of(true))), // false AND true
                Arguments.of(LogicalOperation.disjunction(Scalar.of(false), Scalar.of(false))), // false OR false
                Arguments.of(LogicalOperation.negation(Scalar.of(true))), // NOT true
                Arguments.of(Comparison.in(SYSTEM_REFERENCE, new SetValue(Set.of(
                        Scalar.of("a"), Scalar.of("b"), Scalar.of("c")
                )))), // entity.system in set(a, b, c)
                Arguments.of(Comparison.in(SYSTEM_REFERENCE, new ListValue(List.of(
                        Scalar.of("a"), Scalar.of("b"), Scalar.of("c")
                )))) // entity.system in [a, b, c]
        );
    }

    @ParameterizedTest
    @MethodSource
    void alwaysTruePolicy(ThunkExpression<Boolean> expression) {
        assertThat(PERMISSION_EVALUATOR.isAllowed(expression, AUTOMATION_1)).isTrue();
        assertThat(PERMISSION_EVALUATOR.isAllowed(expression, AUTOMATION_2)).isTrue();
    }

    @ParameterizedTest
    @MethodSource
    void alwaysFalsePolicy(ThunkExpression<Boolean> expression) {
        assertThat(PERMISSION_EVALUATOR.isAllowed(expression, AUTOMATION_1)).isFalse();
        assertThat(PERMISSION_EVALUATOR.isAllowed(expression, AUTOMATION_2)).isFalse();
    }

    @Test
    void conditionalOnSystemPolicy() {
        var policy = Comparison.areEqual(SYSTEM_REFERENCE, Scalar.of("my-system"));

        assertThat(PERMISSION_EVALUATOR.isAllowed(policy, AUTOMATION_1)).isTrue();
        assertThat(PERMISSION_EVALUATOR.isAllowed(policy, AUTOMATION_2)).isFalse();
    }

    @Test
    void conditionalOnSystemPolicy_wrongSubject_shouldThrow() {
        var policy = Comparison.areEqual(
                SymbolicReference.of("automation", path -> path.string("system")),
                Scalar.of("my-system")
        );

        assertThatThrownBy(() -> PERMISSION_EVALUATOR.isAllowed(policy, AUTOMATION_1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected symbolic-ref subject named 'entity', but got 'automation'");
    }

    @Test
    void conditionalOnSystemPolicy_wrongPath_shouldThrow() {
        var policy = Comparison.areEqual(
                SymbolicReference.of("entity", path -> path.string("sys")),
                Scalar.of("my-system")
        );

        assertThatThrownBy(() -> PERMISSION_EVALUATOR.isAllowed(policy, AUTOMATION_1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field 'entity.sys' does not exist");
    }

    @Test
    void conditionalWithIn_noCollection_shouldThrow() {
        var policy = Comparison.in(SYSTEM_REFERENCE, Scalar.of("my-system"));

        assertThatThrownBy(() -> PERMISSION_EVALUATOR.isAllowed(policy, AUTOMATION_1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Operation 'in' requires a collection as right-hand side expression");
    }

}
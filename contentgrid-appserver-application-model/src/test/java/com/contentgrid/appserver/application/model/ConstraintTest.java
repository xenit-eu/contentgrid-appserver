package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.exceptions.InvalidConstraintException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConstraintTest {

    @Test
    void allowedValuesConstraint() {
        var constraint = Constraint.allowedValues(List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), constraint.getValues());
    }

    @Test
    void allowedValuesConstraint_singleValue() {
        var constraint = Constraint.allowedValues(List.of("This is a single value, even though it contains spaces."));
        assertEquals(List.of("This is a single value, even though it contains spaces."), constraint.getValues());
    }

    @Test
    void allowedValuesConstraint_emptyList() {
        assertThrows(InvalidConstraintException.class, () -> Constraint.allowedValues(List.of()));
    }

    @Test
    void allowedValuesConstraint_emptyValue() {
        assertThrows(InvalidConstraintException.class, () -> Constraint.allowedValues(List.of("a", "", "c")));
    }

    @Test
    void allowedValuesConstraint_duplicateValue() {
        assertThrows(InvalidConstraintException.class, () -> Constraint.allowedValues(List.of("a", "b", "a")));
    }

}
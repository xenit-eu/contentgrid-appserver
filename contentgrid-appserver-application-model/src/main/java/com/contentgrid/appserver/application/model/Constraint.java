package com.contentgrid.appserver.application.model;

import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

public sealed interface Constraint {

    static RequiredConstraint required() {
        return new RequiredConstraint();
    }

    static UniqueConstraint unique() {
        return new UniqueConstraint();
    }

    static AllowedValuesConstraint allowedValues(List<String> values) {
        return new AllowedValuesConstraint(values);
    }

    @Value
    class AllowedValuesConstraint implements Constraint {

        @NonNull
        List<String> values;

        private AllowedValuesConstraint(@NonNull List<String> values) {
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Values can not be empty");
            }
            if (values.stream().anyMatch(value -> value == null || value.isEmpty())) {
                throw new IllegalArgumentException("All values should be non-null and non-empty");
            }
            if (Set.copyOf(values).size() < values.size()) {
                throw new IllegalArgumentException("All values should be unique");
            }
            this.values = values;
        }

    }

    @EqualsAndHashCode
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    final class RequiredConstraint implements Constraint {

    }

    @EqualsAndHashCode
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    final class UniqueConstraint implements Constraint {

    }
}

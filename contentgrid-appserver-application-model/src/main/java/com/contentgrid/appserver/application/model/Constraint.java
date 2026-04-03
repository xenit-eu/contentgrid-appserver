package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.exceptions.InvalidConstraintException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.With;

/**
 * Represents a constraint that can be applied to an attribute.
 * 
 * Constraints define rules and validations for attribute values.
 * This is a sealed interface with specific implementations for different constraint types.
 */
public sealed interface Constraint {

    /**
     * Creates a new required constraint.
     * 
     * @return a new RequiredConstraint instance
     */
    static RequiredConstraint required() {
        return new RequiredConstraint();
    }

    /**
     * Creates a new unique constraint.
     * 
     * @return a new UniqueConstraint instance
     */
    static UniqueConstraint unique() {
        return new UniqueConstraint();
    }

    /**
     * Creates a new allowed values constraint with the specified values.
     * 
     * @param values the list of allowed values
     * @return a new AllowedValuesConstraint instance
     * @throws InvalidConstraintException if the values list is empty, contains null/empty values, or contains duplicates
     */
    static AllowedValuesConstraint allowedValues(List<String> values) {
        return new AllowedValuesConstraint(values);
    }

    /**
     * Creates a new regex constraint
     * @param pattern the pattern that the attribute value must match
     * @return a new RegexPatternConstraint instance
     * @throws InvalidConstraintException if the pattern is not valid
     */
    static RegexPatternConstraint pattern(String pattern) {
        return new RegexPatternConstraint(pattern);
    }

    /**
     * A constraint that restricts an attribute's value to a predefined list of values.
     */
    @Value
    class AllowedValuesConstraint implements Constraint {

        /**
         * The list of allowed values for the attribute.
         */
        @NonNull
        List<String> values;

        private AllowedValuesConstraint(@NonNull List<String> values) {
            if (values.isEmpty()) {
                throw new InvalidConstraintException("Values can not be empty");
            }
            if (values.stream().anyMatch(value -> value == null || value.isEmpty())) {
                throw new InvalidConstraintException("All values should be non-null and non-empty");
            }
            if (Set.copyOf(values).size() < values.size()) {
                throw new InvalidConstraintException("All values should be unique");
            }
            this.values = List.copyOf(values);
        }

    }

    /**
     * A constraint that marks an attribute as required (non-null).
     */
    @EqualsAndHashCode
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    final class RequiredConstraint implements Constraint {

    }

    /**
     * A constraint that requires an attribute's value to be unique within the entity.
     */
    @EqualsAndHashCode
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    final class UniqueConstraint implements Constraint {

    }

    /**
     * A constraint that restricts an attribute's value to match a regex.
     */
    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    class RegexPatternConstraint implements Constraint {
        @NonNull
        Pattern pattern;

        @With
        String htmlPattern;

        private RegexPatternConstraint(@NonNull String pattern) {
            this(tryCompile(pattern), pattern);
        }

        private static @NonNull Pattern tryCompile(@NonNull String pattern) {
            try{
                return Pattern.compile(pattern);
            } catch(IllegalArgumentException e) {
                var exception = new InvalidConstraintException("Regex is invalid: "+e.getMessage());
                exception.initCause(e);
                throw exception;
            }
        }

    }
}

package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.domain.values.EntityId;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jooq.Field;
import org.jooq.impl.DSL;

/**
 * Strategy-interface for handling the expected value of a database field before it is written.
 * <p>
 * It is used for conditional updated when a field should only be updated when it matches the specified condition
 * <p>
 * Note: this is actually an abstract class instead of an interface to hide the implementation methods from consumers outside this package.
 */
public abstract sealed class ExpectedId {

    /**
     * Requires the current value to exactly match the specified entity ID.
     * @param entityId The entity ID that must be matches (must be null when the entity ID must be null)
     */
    public static ExpectedId exactly(EntityId entityId) {
        if (entityId == null) {
            return NullExpectedId.INSTANCE;
        }
        return new ExactlyExpectedId(entityId);
    }

    /**
     * Does not place any requirements  on the current value.
     */
    public static ExpectedId unspecified() {
        return UnspecifiedExpectedId.INSTANCE;
    }

    /**
     * Create a conditional expression to return newTargetValue or the current targetField depending on whether conditionField matches the expected value or not.
     * <p>
     * To be used in a JOOQ UPDATE statement: {@code .set(field, expectedId.mapToNewValue(otherField, field, myNewValue)) }
     *
     * @param conditionField Field against which the expected value is checked
     * @param targetField Field that will be used for reading the existing value of the field to be assigned in case the condition does not match
     * @param newTargetValue Value that will used in case the condition does match
     * @return An expression to be assigned to the target field
     */
    abstract Field<UUID> mapToNewValue(Field<UUID> conditionField, Field<UUID> targetField, UUID newTargetValue);

    /**
     * Used to retrieve the specified ID (or the absence of it) for strategies that explicitly specify an id
     */
    public abstract static sealed class IdSpecified extends ExpectedId {

        abstract Optional<EntityId> getEntityId();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    static final class ExactlyExpectedId extends IdSpecified {

        @NonNull
        private final EntityId entityId;

        @Override
        public Field<UUID> mapToNewValue(Field<UUID> conditionField, Field<UUID> targetField, UUID newTargetValue) {
            return DSL.case_(conditionField)
                    .when(entityId.getValue(), newTargetValue)
                    .else_(targetField);
        }

        @Override
        public String toString() {
            return "exactly %s".formatted(entityId.getValue());
        }

        @Override
        public Optional<EntityId> getEntityId() {
            return Optional.of(entityId);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    static final class NullExpectedId extends IdSpecified {

        private static final ExpectedId INSTANCE = new NullExpectedId();

        @Override
        public Field<UUID> mapToNewValue(Field<UUID> conditionField, Field<UUID> targetField, UUID newTargetValue) {
            return DSL.case_()
                    .when(conditionField.isNull(), newTargetValue)
                    .else_(targetField);
        }

        @Override
        public String toString() {
            return "is null";
        }

        @Override
        public Optional<EntityId> getEntityId() {
            return Optional.empty();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    static final class UnspecifiedExpectedId extends ExpectedId {

        private static final ExpectedId INSTANCE = new UnspecifiedExpectedId();

        @Override
        public Field<UUID> mapToNewValue(Field<UUID> conditionField, Field<UUID> targetField, UUID newTargetValue) {
            return DSL.value(newTargetValue);
        }

        @Override
        public String toString() {
            return "unspecified";
        }
    }
}

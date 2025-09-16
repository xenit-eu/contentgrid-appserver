package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.domain.values.EntityId;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jooq.Field;
import org.jooq.impl.DSL;

public sealed interface ExpectedId {

    static ExpectedId exactly(EntityId entityId) {
        if (entityId == null) {
            return NullExpectedId.INSTANCE;
        }
        return new ExactlyExpectedId(entityId);
    }

    static ExpectedId unspecified() {
        return UnspecifiedExpectedId.INSTANCE;
    }

    Field<UUID> mapToNewValue(Field<UUID> conditionField, Field<UUID> targetField, UUID newTargetValue);

    sealed interface IdSpecified extends ExpectedId {

        Optional<EntityId> getEntityId();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    final class ExactlyExpectedId implements IdSpecified {

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
    final class NullExpectedId implements IdSpecified {

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
    final class UnspecifiedExpectedId implements ExpectedId {

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

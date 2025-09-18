package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.domain.values.EntityId;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ExpectedIdMismatchException extends Exception {

    @NonNull
    private final ExpectedId.IdSpecified expectedId;

    private final UUID actualId;

    @Override
    public String getMessage() {
        return "Expected ID mismatch: expected %s, got %s".formatted(expectedId, actualId);
    }

    public Optional<EntityId> getActualEntityId() {
        return Optional.ofNullable(actualId)
                .map(EntityId::of);
    }
}


package com.contentgrid.appserver.events;

import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@Builder
class EntityChangeEvent {

    @NonNull
    ChangeKind trigger;
    @NonNull
    Class<?> domainType;

    Object oldEntity;
    Object newEntity;

    public Optional<Object> getOldEntity() {
        return Optional.ofNullable(oldEntity);
    }

    public Optional<Object> getNewEntity() {
        return Optional.ofNullable(newEntity);
    }

    @RequiredArgsConstructor
    public enum ChangeKind {
        CREATE("create"),
        UPDATE("update"),
        DELETE("delete"),
        ;
        @Getter
        final String value;
    }
}

package com.contentgrid.appserver.events;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import java.io.Serializable;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@Builder
public class EntityChangeEvent {

    @NonNull
    ChangeKind trigger;

    @NonNull
    Application application;

    @NonNull
    EntityName entity;

    EntityData oldData;
    EntityData newData;

    public Optional<EntityData> getOldData() {
        return Optional.ofNullable(oldData);
    }

    public Optional<EntityData> getNewData() {
        return Optional.ofNullable(newData);
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

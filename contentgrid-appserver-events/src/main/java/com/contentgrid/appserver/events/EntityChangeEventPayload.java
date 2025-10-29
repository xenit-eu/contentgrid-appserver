package com.contentgrid.appserver.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EntityChangeEventPayload {

    @Getter
    @NonNull
    private final String trigger;

    @Getter
    private final JsonNode old;

    @JsonProperty("new")
    private final JsonNode _new;

    public JsonNode getNew() {
        return _new;
    }

    @RequiredArgsConstructor
    public enum ChangeKind {
        CREATE("create"),
        UPDATE("update"),
        DELETE("delete");

        @Getter
        private final String value;
    }

    public static EntityChangeEventPayload forCreate(@NonNull JsonNode newData) {
        return new EntityChangeEventPayload(ChangeKind.CREATE.getValue(), null, newData);
    }

    public static EntityChangeEventPayload forUpdate(@NonNull JsonNode oldData, @NonNull JsonNode newData) {
        return new EntityChangeEventPayload(ChangeKind.UPDATE.getValue(), oldData, newData);
    }

    public static EntityChangeEventPayload forDelete(@NonNull JsonNode oldData) {
        return new EntityChangeEventPayload(ChangeKind.DELETE.getValue(), oldData, null);
    }
}

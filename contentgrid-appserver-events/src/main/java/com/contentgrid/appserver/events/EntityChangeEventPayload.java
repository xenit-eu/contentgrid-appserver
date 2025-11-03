package com.contentgrid.appserver.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EntityChangeEventPayload {

    private static final String CREATE = "create";
    private static final String UPDATE = "update";
    private static final String DELETE = "delete";

    @Getter
    @NonNull
    private final String trigger;

    @Getter
    private final JsonNode old;

    private final JsonNode _new;

    public JsonNode getNew() {
        return _new;
    }

    public static EntityChangeEventPayload forCreate(@NonNull JsonNode newData) {
        return new EntityChangeEventPayload(CREATE, null, newData);
    }

    public static EntityChangeEventPayload forUpdate(@NonNull JsonNode oldData, @NonNull JsonNode newData) {
        return new EntityChangeEventPayload(UPDATE, oldData, newData);
    }

    public static EntityChangeEventPayload forDelete(@NonNull JsonNode oldData) {
        return new EntityChangeEventPayload(DELETE, oldData, null);
    }
}

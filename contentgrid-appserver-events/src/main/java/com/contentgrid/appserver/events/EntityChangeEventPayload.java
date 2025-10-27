package com.contentgrid.appserver.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;
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
}

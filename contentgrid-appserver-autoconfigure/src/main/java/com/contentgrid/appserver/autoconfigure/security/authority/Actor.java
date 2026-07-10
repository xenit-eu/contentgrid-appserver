package com.contentgrid.appserver.autoconfigure.security.authority;

import java.util.Map;
import lombok.NonNull;

public record Actor(@NonNull ActorType type, @NonNull Map<String, Object> claims, Actor parent) {

    public Actor(@NonNull ActorType type, @NonNull Map<String, Object> claims) {
        this(type, claims, null);
    }

    public enum ActorType {
        USER,
        EXTENSION
    }
}

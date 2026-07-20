package com.contentgrid.appserver.security.authority;

import java.io.Serializable;
import lombok.NonNull;
import org.springframework.security.oauth2.core.ClaimAccessor;

public record Actor(@NonNull ActorType type, @NonNull ClaimAccessor claims, Actor parent) implements Serializable {

    public enum ActorType {
        USER,
        EXTENSION
    }
}

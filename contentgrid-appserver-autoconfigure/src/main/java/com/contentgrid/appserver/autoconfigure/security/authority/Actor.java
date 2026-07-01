package com.contentgrid.appserver.autoconfigure.security.authority;

import java.util.Map;
import lombok.NonNull;
import lombok.Value;

@Value
public class Actor {

    @NonNull
    ActorType type;
    @NonNull
    Map<String, Object> claims;

    public enum ActorType {
        USER,
        EXTENSION
    }
}

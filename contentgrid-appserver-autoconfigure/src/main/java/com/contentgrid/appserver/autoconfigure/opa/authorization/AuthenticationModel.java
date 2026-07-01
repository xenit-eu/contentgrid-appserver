package com.contentgrid.appserver.autoconfigure.opa.authorization;

import com.contentgrid.appserver.autoconfigure.security.authority.Actor;
import com.contentgrid.appserver.autoconfigure.security.authority.Actor.ActorType;
import com.contentgrid.appserver.autoconfigure.security.authority.AuthenticationDetails;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.security.core.Authentication;

@Value
@Builder
public class AuthenticationModel {

    AuthenticationKind kind;

    PrincipalModel principal;

    public enum AuthenticationKind {
        @JsonProperty("anonymous")
        ANONYMOUS,
        @JsonProperty("user")
        USER,
        @JsonProperty("system")
        SYSTEM
    }

    @JsonProperty("authenticated")
    public boolean isAuthenticated() {
        return kind != AuthenticationKind.ANONYMOUS;
    }

    @Value
    public static class PrincipalModel {

        ActorKind kind;

        @Getter(onMethod_ = {@JsonAnyGetter, @JsonIgnore})
        Map<String, Object> claims;
    }

    @RequiredArgsConstructor
    public enum ActorKind {
        @JsonProperty("user")
        USER(ActorType.USER),
        @JsonProperty("extension")
        EXTENSION(ActorType.EXTENSION);
        private final ActorType actorType;

        public static ActorKind fromType(ActorType actorType) {
            for (ActorKind actorKind : values()) {
                if (actorKind.actorType == actorType) {
                    return actorKind;
                }
            }
            throw new IllegalArgumentException("ActorType '%s' is not mapped to any ActorKind.".formatted(actorType));
        }
    }

    public static AuthenticationModel from(Authentication authentication) {
        var maybeDetails = authentication.getAuthorities()
                .stream()
                .filter(AuthenticationDetails.class::isInstance)
                .map(AuthenticationDetails.class::cast)
                .findFirst();
        if (maybeDetails.isEmpty()) {
            return AuthenticationModel.builder()
                    .kind(AuthenticationKind.ANONYMOUS)
                    .build();
        }

        var principal = maybeDetails.get().getPrincipal();
        return AuthenticationModel.builder()
                .kind(toAuthenticationKind(principal))
                .principal(new PrincipalModel(ActorKind.fromType(principal.getType()), principal.getClaims()))
                .build();
    }

    private static AuthenticationKind toAuthenticationKind(Actor principal) {
        return switch (principal.getType()) {
            case USER -> AuthenticationKind.USER;
            case EXTENSION -> AuthenticationKind.SYSTEM;
        };
    }
}

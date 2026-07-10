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
import org.springframework.security.oauth2.jwt.JwtClaimNames;

@Value
@Builder
public class AuthenticationModel {

    AuthenticationKind kind;

    PrincipalModel principal;

    ActorModel actor;

    public enum AuthenticationKind {
        @JsonProperty("anonymous")
        ANONYMOUS,
        @JsonProperty("user")
        USER,
        @JsonProperty("delegated")
        DELEGATED,
        @JsonProperty("system")
        SYSTEM
    }

    @JsonProperty("authenticated")
    public boolean isAuthenticated() {
        return kind != AuthenticationKind.ANONYMOUS;
    }

    @Value
    public static class ActorModel {

        ActorKind kind;
        String sub;
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

        var details = maybeDetails.get();
        return AuthenticationModel.builder()
                .kind(toAuthenticationKind(details))
                .principal(toPrincipal(details.principal()))
                .actor(toActor(details.actor()))
                .build();
    }

    private static AuthenticationKind toAuthenticationKind(AuthenticationDetails details) {
        if (details.actor() != null) {
            return AuthenticationKind.DELEGATED;
        }
        return switch (details.principal().type()) {
            case USER -> AuthenticationKind.USER;
            case EXTENSION -> AuthenticationKind.SYSTEM;
        };
    }

    private static PrincipalModel toPrincipal(Actor principal) {
        return new PrincipalModel(ActorKind.fromType(principal.type()), principal.claims());
    }

    private static ActorModel toActor(Actor actor) {
        if (actor == null) {
            return null;
        }
        return new ActorModel(ActorKind.fromType(actor.type()), (String) actor.claims().get(JwtClaimNames.SUB));
    }
}

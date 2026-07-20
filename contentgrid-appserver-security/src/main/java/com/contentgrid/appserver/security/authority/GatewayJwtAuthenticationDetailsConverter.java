package com.contentgrid.appserver.security.authority;

import com.contentgrid.appserver.security.authority.Actor.ActorType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

public class GatewayJwtAuthenticationDetailsConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        try {
            return convertClaims(source);
        } catch (IllegalArgumentException e) {
            // Malformed claim shapes (e.g. a non-object contentgrid:auth:principal) surface as
            // IllegalArgumentException from ClaimAccessor conversion; reject the token instead of erroring.
            throw new InvalidBearerTokenException(e.getMessage(), e);
        }
    }

    private Collection<GrantedAuthority> convertClaims(Jwt source) {
        var principalClaims = source.getClaimAsMap(GatewayAuthClaimNames.AUTH_PRINCIPAL);
        if (principalClaims == null) {
            throw new InvalidBearerTokenException(
                    "JWT is missing the '%s' claim".formatted(GatewayAuthClaimNames.AUTH_PRINCIPAL));
        }

        var principal = actorFromClaims(principalClaims, null);
        var actorClaims = source.getClaimAsMap(GatewayAuthClaimNames.ACT);
        var isDelegated = GatewayAuthClaimNames.AUTH_KIND_DELEGATED
                .equals(source.getClaimAsString(GatewayAuthClaimNames.AUTH_KIND));
        if (isDelegated == (actorClaims == null)) {
            throw new InvalidBearerTokenException(
                    "JWT '%s' claim and presence of the '%s' claim are inconsistent".formatted(
                            GatewayAuthClaimNames.AUTH_KIND, GatewayAuthClaimNames.ACT));
        }

        if (actorClaims != null) {
            var actor = actorChainFromClaims(actorClaims);
            return List.of(new DelegatedAuthenticationDetailsGrantedAuthority(principal, actor));
        }
        return List.of(new PrincipalAuthenticationDetailsGrantedAuthority(principal));
    }

    /**
     * Recursively resolves the {@code act} chain: a nested {@code act} member is the parent (prior) actor of the
     * actor it's nested in, so the outermost object here ends up as the returned {@link Actor}, chained to its
     * parents via {@link Actor#parent()}.
     */
    private static Actor actorChainFromClaims(@Nullable Map<String, Object> claims) {
        if (claims == null) {
            return null;
        }
        var parent = actorChainFromClaims(asMap(claims.get(GatewayAuthClaimNames.ACT)));
        return actorFromClaims(claims, parent);
    }

    private static Actor actorFromClaims(@Nullable Map<String, Object> claims, Actor parent) {
        if (claims == null) {
            return null;
        }
        return new Actor(actorTypeOf(claims), () -> withoutKeys(claims, GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.ACT), parent);
    }

    private static ActorType actorTypeOf(@NonNull Map<String, Object> claims) {
        var kind = claims.get(GatewayAuthClaimNames.KIND);
        if (kind instanceof String kindString) {
            return switch (kindString) {
                case GatewayAuthClaimNames.KIND_USER -> ActorType.USER;
                case GatewayAuthClaimNames.KIND_EXTENSION -> ActorType.EXTENSION;
                default -> throw new InvalidBearerTokenException("Unknown actor '%s' value '%s'".formatted(
                        GatewayAuthClaimNames.KIND, kindString));
            };
        }
        throw new InvalidBearerTokenException(
                "Actor object is missing its '%s' member".formatted(GatewayAuthClaimNames.KIND));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> withoutKeys(Map<String, Object> source, String... keys) {
        var excluded = Set.of(keys);
        return source.entrySet()
                .stream()
                .filter(entry -> !excluded.contains(entry.getKey()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }
}

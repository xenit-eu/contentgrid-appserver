package com.contentgrid.appserver.autoconfigure.security.authority;

import com.contentgrid.appserver.autoconfigure.security.authority.Actor.ActorType;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

@RequiredArgsConstructor
public class ActorConverter implements Converter<ClaimAccessor, Actor> {

    private final Predicate<String> issuerMatcher;
    private final ActorType actorType;
    private final Function<ClaimAccessor, Map<String, Object>> claimsFilter;

    @Override
    public Actor convert(ClaimAccessor claimAccessor) {
        var issuer = claimAccessor.getClaimAsString(JwtClaimNames.ISS);
        if (issuer == null) {
            throw new IllegalArgumentException("The 'iss' claim is required for actors");
        }
        if (!issuerMatcher.test(issuer)) {
            return null;
        }
        return new Actor(actorType, claimsFilter.apply(claimAccessor));
    }
}

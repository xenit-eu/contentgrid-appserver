package com.contentgrid.appserver.autoconfigure.security.authority;

import com.contentgrid.appserver.autoconfigure.security.authority.Actor.ActorType;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

@RequiredArgsConstructor
public class ActorConverter implements Converter<ClaimAccessor, Actor> {

    /**
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8693.html#name-act-actor-claim">RFC8693</a>
     */
    private static final String ACT_CLAIM = "act";

    private final Predicate<String> issuerMatcher;
    private final ActorType actorType;
    private final Function<ClaimAccessor, Map<String, Object>> claimsFilter;

    /**
     * Converter used to resolve the actor delegated to by the {@code act} claim, if present.
     * When left unset, an {@code act} claim on an incoming token is ignored.
     */
    @Setter
    private Converter<ClaimAccessor, Actor> parentActorConverter;

    @Override
    public Actor convert(ClaimAccessor claimAccessor) {
        var issuer = claimAccessor.getClaimAsString(JwtClaimNames.ISS);
        if (issuer == null) {
            throw new IllegalArgumentException("The 'iss' claim is required for actors");
        }
        if (!issuerMatcher.test(issuer)) {
            return null;
        }

        Actor parent = null;
        var actClaims = claimAccessor.getClaimAsMap(ACT_CLAIM);
        if (actClaims != null && parentActorConverter != null) {
            parent = parentActorConverter.convert(() -> actClaims);
        }

        return new Actor(actorType, claimsFilter.apply(claimAccessor), parent);
    }
}

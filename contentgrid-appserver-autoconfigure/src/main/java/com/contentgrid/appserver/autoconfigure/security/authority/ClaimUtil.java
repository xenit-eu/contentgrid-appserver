package com.contentgrid.appserver.autoconfigure.security.authority;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

@UtilityClass
public class ClaimUtil {

    public Map<String, Object> userClaims(ClaimAccessor claims) {
        return limitToKeys(claims, ClaimUtil::isUserClaim);
    }

    public Map<String, Object> extensionSystemClaims(ClaimAccessor claims) {
        return limitToKeys(claims, Set.of(JwtClaimNames.SUB, JwtClaimNames.ISS)::contains);
    }

    private Map<String, Object> limitToKeys(ClaimAccessor claims, Predicate<String> keyFilter) {
        return claims.getClaims()
                .entrySet()
                .stream()
                .filter(entry -> keyFilter.test(entry.getKey()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    private boolean isUserClaim(String claimName) {
        return switch (claimName) {
            case JwtClaimNames.SUB, JwtClaimNames.ISS, StandardClaimNames.NAME, StandardClaimNames.EMAIL -> true;
            default -> claimName.startsWith("contentgrid:");
        };
    }
}

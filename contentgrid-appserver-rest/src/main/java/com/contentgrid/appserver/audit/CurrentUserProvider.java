package com.contentgrid.appserver.audit;

import com.contentgrid.appserver.domain.values.User;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class CurrentUserProvider {
    public static Optional<User> getCurrentUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(authentication -> new User(authentication.getName(), namespace(authentication), authentication.getName()));

    }

    private static String namespace(Authentication authentication) {
        if (authentication instanceof Jwt jwt) {
            return jwt.getClaimAsString("iss");
        }
        return null;
    }
}

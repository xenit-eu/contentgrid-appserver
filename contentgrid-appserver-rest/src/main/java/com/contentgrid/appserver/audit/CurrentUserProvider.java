package com.contentgrid.appserver.audit;

import com.contentgrid.appserver.domain.values.User;
import java.util.Optional;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.context.SecurityContext;
//import org.springframework.security.core.context.SecurityContextHolder;

public class CurrentUserProvider {
    public static Optional<User> getCurrentUser() {
//        return Optional.ofNullable(SecurityContextHolder.getContext())
//                .map(SecurityContext::getAuthentication)
//                .filter(Authentication::isAuthenticated)
//                .map(authentication -> new User(authentication.getName(), null, authentication.getName()));
        return Optional.of(new User("00000000-0000-0000-0000-000000000000", "keycloak", "alice@example.com"));

    }
}

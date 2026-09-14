package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.values.User;
import com.contentgrid.appserver.security.authority.AuthenticationDetails;
import com.contentgrid.thunx.spring.security.AbacContextSupplier;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@RequiredArgsConstructor
@Component
public class AuthorizationContextArgumentResolver implements HandlerMethodArgumentResolver {
    private final AbacContextSupplier abacContextSupplier;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().isAssignableFrom(AuthorizationContext.class);
    }

    @Override
    public AuthorizationContext resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var context = abacContextSupplier.getAbacContext();
        var user = getCurrentUser().orElse(null);

        if(context == null) {
            return AuthorizationContext.allowAll(user);
        }
        return new AuthorizationContext(context, user);
    }

    private static Optional<User> getCurrentUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(AuthorizationContextArgumentResolver::toUser);
    }

    private static User toUser(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .filter(AuthenticationDetails.class::isInstance)
                .map(AuthenticationDetails.class::cast)
                .findFirst()
                .map(authenticationDetails -> userFromClaims(authenticationDetails.getPrincipal().claims()))
                .orElseGet(() -> authentication.getPrincipal() instanceof JwtClaimAccessor jwt
                        ? userFromClaims(jwt)
                        : new User(authentication.getName(), null, authentication.getName()));
    }

    private static User userFromClaims(ClaimAccessor claims) {
        var subject = Objects.requireNonNull(claims.getClaimAsString(JwtClaimNames.SUB),
                "Authenticated principal has no sub claim");
        var name = claims.getClaimAsString(StandardClaimNames.NAME);
        return new User(subject, claims.getClaimAsString(JwtClaimNames.ISS), name != null ? name : subject);
    }
}

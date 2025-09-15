package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.values.User;
import com.contentgrid.thunx.spring.data.context.AbacContextSupplier;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@RequiredArgsConstructor
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
                .map(authentication -> new User(authentication.getName(), namespace(authentication), authentication.getName()));

    }

    private static String namespace(Authentication authentication) {
        if (authentication instanceof Jwt jwt) {
            return jwt.getClaimAsString("iss");
        }
        return null;
    }
}

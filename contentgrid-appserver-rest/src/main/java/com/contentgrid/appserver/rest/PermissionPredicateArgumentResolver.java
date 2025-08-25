package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.domain.authorization.PermissionPredicate;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.spring.data.context.AbacContextSupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@RequiredArgsConstructor
public class PermissionPredicateArgumentResolver implements HandlerMethodArgumentResolver {
    private final AbacContextSupplier abacContextSupplier;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().isAssignableFrom(PermissionPredicate.class);
    }

    @Override
    public PermissionPredicate resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var context = abacContextSupplier.getAbacContext();
        if(context == null) {
            return PermissionPredicate.allowAll();
        }
        return new PermissionPredicate(context);
    }
}

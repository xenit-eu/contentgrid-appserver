package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.hateoas.server.MethodLinkBuilderFactory;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@RequiredArgsConstructor
public class LinkProviderArgumentResolver implements HandlerMethodArgumentResolver {
    @NonNull
    private final MethodLinkBuilderFactory<?> linkBuilderFactory;
    @NonNull
    private final ApplicationArgumentResolver applicationArgumentResolver;
    @NonNull
    private final UserLocalesArgumentResolver userLocalesArgumentResolver;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().isAssignableFrom(LinkFactoryProvider.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        var application = applicationArgumentResolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
        var userLocales = userLocalesArgumentResolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory);

        return new LinkFactoryProvider(
                application,
                userLocales,
                linkBuilderFactory
        );
    }
}

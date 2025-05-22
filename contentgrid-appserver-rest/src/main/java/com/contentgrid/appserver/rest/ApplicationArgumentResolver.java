package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.registry.ApplicationNameExtractor;
import com.contentgrid.appserver.registry.ApplicationResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@RequiredArgsConstructor
public class ApplicationArgumentResolver implements HandlerMethodArgumentResolver {

    private final ApplicationResolver resolver;
    private final ApplicationNameExtractor extractor;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameter().getType() == Application.class;
    }

    @Override
    public Application resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);

        return resolver.resolve(extractor.extract(servletRequest));
    }
}

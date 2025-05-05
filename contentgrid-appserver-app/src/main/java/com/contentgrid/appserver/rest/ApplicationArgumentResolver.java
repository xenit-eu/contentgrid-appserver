package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.registry.ApplicationRegistry;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@RequiredArgsConstructor
public class ApplicationArgumentResolver implements HandlerMethodArgumentResolver {

    private final ApplicationRegistry registry;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameter().getType() == Application.class;
    }

    @Override
    public Application resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);

        var applicationName = Optional.ofNullable(servletRequest)
                .map(ServletRequest::getServerName)
                // TODO figure out a real strategy for this
                .map(serverName -> serverName.contains(".") ? serverName.substring(0, serverName.indexOf('.')) : null)
                .map(ApplicationName::of);

        if (applicationName.isPresent()) {
            return registry.get(applicationName.get());
        }
        return registry.getDefault();
    }
}

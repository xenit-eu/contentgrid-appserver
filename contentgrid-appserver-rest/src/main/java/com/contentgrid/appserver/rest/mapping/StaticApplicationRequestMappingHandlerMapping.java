package com.contentgrid.appserver.rest.mapping;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.rest.mapping.specialized.SpecializationHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Creates specialized request mappings from class-level and method-level {@link org.springframework.web.bind.annotation.RequestMapping}s that are handled by a {@link SpecializationHandler}
 * <p>
 * The specialization done based on the {@link Application} configuration
 */
@RequiredArgsConstructor
class StaticApplicationRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    @NonNull
    private final Application application;

    @NonNull
    private final SpecializationHandler<Method> specializationHandler;

    @Override
    protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
        specializationHandler.getSpecializerFor(method)
                .map(specializer -> specializer.specialize(application, mapping))
                .orElse(Stream.of(mapping))
                .forEach(rmi -> super.registerHandlerMethod(handler, method, rmi));
    }

    @Override
    protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
        return super.getHandlerInternal(request);
    }
}

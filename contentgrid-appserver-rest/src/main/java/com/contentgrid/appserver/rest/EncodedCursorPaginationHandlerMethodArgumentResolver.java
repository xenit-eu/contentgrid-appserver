package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPaginationSystem;
import com.contentgrid.hateoas.pagination.api.PaginationParameters;
import com.contentgrid.hateoas.spring.pagination.PaginationHandlerMethodArgumentResolver;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

public class EncodedCursorPaginationHandlerMethodArgumentResolver extends PaginationHandlerMethodArgumentResolver {

    private final EncodedCursorPaginationSystem paginationSystem = new EncodedCursorPaginationSystem();

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return EncodedCursorPagination.class.equals(parameter.getParameterType());
    }

    @Override
    public EncodedCursorPagination resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {

        var parameterMap = webRequest.getParameterMap().entrySet()
            .stream()
            .collect(Collectors.toMap(
                    Entry::getKey,
                    entry -> List.of(entry.getValue())
            ));
        var parameters = new PaginationParameters(parameterMap);

        return paginationSystem.create(parameters);
    }
}

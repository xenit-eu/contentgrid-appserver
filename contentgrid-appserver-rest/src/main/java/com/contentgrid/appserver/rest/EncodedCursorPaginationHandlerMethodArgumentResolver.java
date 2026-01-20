package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.domain.data.validation.ValidationExceptionCollector;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.exception.InvalidPaginationParameterException;
import com.contentgrid.appserver.exception.InvalidSortParameterException;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.Direction;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import com.contentgrid.hateoas.pagination.api.PaginationParameters;
import com.contentgrid.hateoas.pagination.api.PaginationSystem;
import com.contentgrid.hateoas.spring.pagination.PaginationHandlerMethodArgumentResolver;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.MethodParameter;
import org.springframework.hateoas.server.mvc.UriComponentsContributor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class EncodedCursorPaginationHandlerMethodArgumentResolver extends PaginationHandlerMethodArgumentResolver
        implements PaginationSystem, UriComponentsContributor {

    public static final String CURSOR_NAME = "_cursor";
    public static final String SIZE_NAME = "_size";
    public static final String SORT_NAME = "_sort";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 1000;

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

        return this.create(parameters);
    }

    @Override
    public void enhance(UriComponentsBuilder builder, @Nullable MethodParameter parameter, Object value) {

        Assert.notNull(builder, "UriComponentsBuilder must not be null");

        if (value == null) {
            return;
        }

        if (!(value instanceof EncodedCursorPagination pagination)) {
            return;
        }

        // Overload replaceQueryParam(String name, Object... values)
        builder.replaceQueryParam(SIZE_NAME, pagination.getSize());

        // Overload replaceQueryParam(String name, Collection<?> values)
        // Empty collections remove query parameter
        builder.replaceQueryParam(CURSOR_NAME, Optional.ofNullable(pagination.getCursor()).stream().toList());
        builder.replaceQueryParam(SORT_NAME, pagination.getSort().toList());
    }

    @Override
    public boolean matches(PaginationParameters parameters) {
        return true;
    }

    @Override
    public EncodedCursorPagination create(PaginationParameters parameters) {
        var cursor = parameters.getValue(CURSOR_NAME, Function.identity(), null);
        var size = parseSize(parameters);
        var sort = parseSortData(parameters);
        return new EncodedCursorPagination(cursor, size, sort);
    }

    private int parseSize(PaginationParameters parameters) {
        // Use a collector to prevent parameter.getValue() returning the default value if it
        // encounters a RuntimeException. The Exception is rethrown outside the lambda expression.
        var collector = new ValidationExceptionCollector<>(InvalidPaginationParameterException.class);
        var parsedSize = parameters.getValue(SIZE_NAME, size -> collector.use(() -> {
            try {
                var result = Integer.parseUnsignedInt(size);
                if (result < 1 || result > MAX_PAGE_SIZE) {
                    throw new InvalidPaginationParameterException(SIZE_NAME, size,
                            "Value must be between 1 and %s".formatted(MAX_PAGE_SIZE));
                }
                return result;
            } catch (NumberFormatException e) {
                throw new InvalidPaginationParameterException(SIZE_NAME, size,
                        "Value must be between 1 and %s".formatted(MAX_PAGE_SIZE));
            }
        }), DEFAULT_PAGE_SIZE);
        // Rethrow outside lambda expression if exception was thrown.
        collector.rethrow();
        return parsedSize;
    }

    private SortData parseSortData(PaginationParameters parameters) {
        // Use a collector to catch multiple InvalidSortParameterExceptions
        var collector = new ValidationExceptionCollector<>(InvalidSortParameterException.class);
        var fields = parameters.getValues(SORT_NAME, sort -> collector.use(() -> {
            var split = sort.split(",", 2);
            if (split.length == 2) {
                try {
                    var direction = Direction.valueOf(split[1].toUpperCase());
                    return new FieldSort(direction, SortableName.of(split[0]));
                } catch (IllegalArgumentException e) {
                    throw InvalidSortParameterException.invalidDirection(split[1]);
                }
            } else {
                return new FieldSort(Direction.ASC, SortableName.of(split[0]));
            }
        }));
        collector.rethrow();
        return new SortData(fields);
    }
}

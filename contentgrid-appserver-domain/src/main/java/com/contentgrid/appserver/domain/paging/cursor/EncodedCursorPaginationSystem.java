package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.domain.data.validation.ValidationExceptionCollector;
import com.contentgrid.appserver.exception.InvalidSortParameterException;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.Direction;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import com.contentgrid.hateoas.pagination.api.PaginationParameters;
import com.contentgrid.hateoas.pagination.api.PaginationSystem;
import java.util.function.Function;

public class EncodedCursorPaginationSystem implements PaginationSystem {

    private final EncodedCursorPaginationNamingStrategy namingStrategy = new EncodedCursorPaginationNamingStrategy();

    @Override
    public boolean matches(PaginationParameters parameters) {
        return namingStrategy.getParameters().stream().anyMatch(parameters::containsKey);
    }

    @Override
    public EncodedCursorPagination create(PaginationParameters parameters) {
        var cursor = parameters.getValue(namingStrategy.getPageName(), Function.identity(), null);
        var size = parameters.getInteger(namingStrategy.getSizeName(), EncodedCursorPagination.PAGE_SIZE);
        var sort = parseSortData(parameters);
        return new EncodedCursorPagination(cursor, size, sort);
    }

    private SortData parseSortData(PaginationParameters parameters) {
        // Use a collector to catch multiple InvalidSortParameterExceptions
        var collector = new ValidationExceptionCollector<>(InvalidSortParameterException.class);
        var fields = parameters.getValues(namingStrategy.getSortName(), sort -> collector.use(() -> {
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

package com.contentgrid.appserver.exception;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.domain.data.type.DataType;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class InvalidFilterParameterException extends IllegalArgumentException {

    @NonNull
    private final EntityName entityName;

    @NonNull
    private final FilterName filterName;
    @NonNull
    private final DataType type;

    public InvalidFilterParameterException(
            @NonNull EntityName entityName,
            @NonNull FilterName filterName,
            @NonNull DataType type,
            @NonNull Throwable cause
    ) {
        super(cause);
        this.entityName = entityName;
        this.filterName = filterName;
        this.type = type;
    }

    @Override
    public String getMessage() {
        return "Filter %s in entity %s: can not convert value to %s: %s".formatted(filterName.getValue(),
                entityName.getValue(), type.getTechnicalName(), getCause().getMessage());
    }
}

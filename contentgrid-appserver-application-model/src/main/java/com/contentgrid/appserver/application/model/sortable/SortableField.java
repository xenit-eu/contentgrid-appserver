package com.contentgrid.appserver.application.model.sortable;

import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.application.model.values.SortableName;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
public class SortableField {

    @NonNull
    SortableName name;

    @NonNull
    AttributePath propertyPath;

    @Builder
    public SortableField(
            @NonNull SortableName name,
            @NonNull AttributePath propertyPath
    ) {
        this.name = name;
        this.propertyPath = propertyPath;
    }

}

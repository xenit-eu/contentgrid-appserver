package com.contentgrid.appserver.application.model.sortable;

import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.SortableName;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class SortableField {

    @NonNull
    SortableName name;

    @NonNull
    PropertyPath propertyPath;

}

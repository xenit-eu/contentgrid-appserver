package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.AttributeName;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class SimpleAttributeData<T> implements AttributeData {

    @NonNull
    AttributeName name;

    T value;
}

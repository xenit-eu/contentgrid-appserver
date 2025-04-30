package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.AttributeName;
import lombok.NonNull;

public sealed interface AttributeData permits SimpleAttributeData, CompositeAttributeData {

    @NonNull
    AttributeName getName();

}

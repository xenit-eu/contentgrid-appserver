package com.contentgrid.appserver.application.model.openapi.type;

import lombok.NonNull;
import lombok.Value;

@Value
public class RelationType implements SemanticType {
    @NonNull
    SemanticType target;
}

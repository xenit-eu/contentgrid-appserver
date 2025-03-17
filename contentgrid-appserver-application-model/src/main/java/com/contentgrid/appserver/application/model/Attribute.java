package com.contentgrid.appserver.application.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class Attribute {

    @NonNull
    String name;

    @NonNull
    Type type;

    public enum Type {
        LONG,
        DOUBLE,
        BOOLEAN,
        TEXT,
        DATETIME,
        CONTENT,
        AUDIT_METADATA
    }

}

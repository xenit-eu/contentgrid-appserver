package com.contentgrid.appserver.rest.assembler.profile.hal;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import org.springframework.lang.Nullable;

public enum ProfileAttributeType {
    STRING,
    LONG,
    DOUBLE,
    BOOLEAN,
    DATETIME,
    OBJECT;

    @Nullable
    public static ProfileAttributeType from(SimpleAttribute.Type type) {
        return switch (type) {
            case TEXT, UUID -> STRING;
            case LONG -> LONG;
            case DOUBLE -> DOUBLE;
            case BOOLEAN -> BOOLEAN;
            case DATETIME -> DATETIME;
        };
    }
}

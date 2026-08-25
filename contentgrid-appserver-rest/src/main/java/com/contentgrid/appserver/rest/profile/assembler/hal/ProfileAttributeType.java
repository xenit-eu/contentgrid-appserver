package com.contentgrid.appserver.rest.profile.assembler.hal;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import org.springframework.lang.Nullable;

public enum ProfileAttributeType {
    STRING,
    STRING_SET,
    LONG,
    DOUBLE,
    BOOLEAN,
    DATE,
    DATETIME,
    OBJECT;

    @Nullable
    public static ProfileAttributeType from(SimpleAttribute.Type type) {
        return switch (type) {
            case TEXT, UUID -> STRING;
            case TEXT_SET -> STRING_SET;
            case LONG -> LONG;
            case DOUBLE -> DOUBLE;
            case BOOLEAN -> BOOLEAN;
            case DATE -> DATE;
            case DATETIME -> DATETIME;
        };
    }
}

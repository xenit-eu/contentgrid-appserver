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

    public static ProfileAttributeType setOf(SimpleAttribute.Type itemType) {
        return switch (itemType) {
            case TEXT -> STRING_SET;
            default -> throw new IllegalArgumentException(
                    "No profile type for a set of %s".formatted(itemType));
        };
    }

    @Nullable
    public static ProfileAttributeType from(SimpleAttribute.Type type) {
        return switch (type) {
            case TEXT, UUID -> STRING;
            case LONG -> LONG;
            case DOUBLE -> DOUBLE;
            case BOOLEAN -> BOOLEAN;
            case DATE -> DATE;
            case DATETIME -> DATETIME;
        };
    }
}

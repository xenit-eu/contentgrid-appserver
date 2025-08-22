package com.contentgrid.appserver.rest.assembler.profile;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
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

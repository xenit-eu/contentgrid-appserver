package com.contentgrid.appserver.domain.data.type;

import java.util.Locale;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TechnicalDataType implements DataType {
    STRING,
    LONG,
    DECIMAL,
    BOOLEAN,
    DATE,
    DATETIME,
    NULL,
    LIST,
    ENTITY,
    ENTITY_COLLECTION,
    OBJECT,
    CONTENT,
    MISSING;

    @Override
    public String getTechnicalName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getHumanDescription() {
        return getTechnicalName();
    }
}

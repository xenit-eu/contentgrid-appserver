package com.contentgrid.appserver.domain.data.type;

import com.contentgrid.appserver.application.model.values.EntityName;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class RelationDataType implements DataType {

    @NonNull
    private final EntityName entityName;

    @Override
    public String getTechnicalName() {
        return TechnicalDataType.RELATION.getTechnicalName();
    }

    @Override
    public String getHumanDescription() {
        return "relation to entity '%s'".formatted(entityName.getValue());
    }

    public static DataType simple() {
        return TechnicalDataType.RELATION;
    }

    public static DataType to(@NonNull EntityName entityName) {
        return new RelationDataType(entityName);
    }

}

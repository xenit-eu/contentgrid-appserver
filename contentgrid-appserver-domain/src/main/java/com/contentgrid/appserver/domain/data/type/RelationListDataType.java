package com.contentgrid.appserver.domain.data.type;

import com.contentgrid.appserver.application.model.values.EntityName;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class RelationListDataType implements DataType {

    @NonNull
    private final EntityName entityName;

    @Override
    public String getTechnicalName() {
        return TechnicalDataType.RELATION_LIST.getTechnicalName();
    }

    @Override
    public String getHumanDescription() {
        return "relations to entity '%s'".formatted(entityName.getValue());
    }

    public static DataType simple() {
        return TechnicalDataType.RELATION_LIST;
    }

    public static DataType to(@NonNull EntityName entityName) {
        return new RelationListDataType(entityName);
    }

}

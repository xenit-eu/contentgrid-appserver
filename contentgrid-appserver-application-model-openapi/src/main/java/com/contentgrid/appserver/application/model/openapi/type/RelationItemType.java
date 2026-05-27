package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.values.EntityName;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public final class RelationItemType extends EntityType {

    public RelationItemType(@NonNull EntityName entityName) {
        super(entityName);
    }
}

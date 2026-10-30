package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.EntityName;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
public class EntityCreateData {
    @NonNull
    EntityName entityName;

    @NonNull
    Collection<AttributeData> attributes;

    @NonNull
    Collection<RelationData> relations;

    @Builder
    public EntityCreateData(
            @NonNull EntityName entityName,
            @Singular @NonNull Collection<AttributeData> attributes,
            @Singular @NonNull Collection<RelationData> relations
    ) {
        this.entityName = entityName;
        this.attributes = checkDuplicate(attributes, AttributeData::getName);
        this.relations = checkDuplicate(relations, RelationData::getName);
    }

    private <T, U> Collection<T> checkDuplicate(Collection<T> collection, Function<T, U> uniqueFn) {
        var set = HashSet.newHashSet(collection.size());

        for (T item : collection) {
            var uniqueVal = uniqueFn.apply(item);
            if (!set.add(uniqueVal)) {
                throw new IllegalArgumentException("Duplicate item '%s'".formatted(uniqueVal));
            }
        }
        return List.copyOf(collection);
    }
}

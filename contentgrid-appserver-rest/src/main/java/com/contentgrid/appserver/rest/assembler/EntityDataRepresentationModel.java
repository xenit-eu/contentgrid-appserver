package com.contentgrid.appserver.rest.assembler;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.server.core.Relation;

@EqualsAndHashCode(callSuper = true)
@Relation(collectionRelation = IanaLinkRelations.ITEM_VALUE)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EntityDataRepresentationModel extends RepresentationModelWithTemplates<EntityDataRepresentationModel> {

    private final Map<String, Object> data;

    @JsonAnyGetter
    public Map<String, Object> getAttributes() {
        return Map.copyOf(this.data);
    }

    public static EntityDataRepresentationModel from(Entity entity, EntityData entityData) {
        Map<String, Object> data = new HashMap<>();
        for (Attribute attribute : entity.getAllAttributes()) {
            var value = entityData.getAttributeByName(attribute.getName()).map(v -> attributeValue(v));
            value.ifPresent(v -> data.put(attribute.getName().getValue(), v));
        }
        data.put(entity.getPrimaryKey().getName().getValue(), entityData.getId().getValue());
        return new EntityDataRepresentationModel(data);
    }

    private static Object attributeValue(AttributeData data) {
        return switch (data) {
            case SimpleAttributeData simple -> simple.getValue();
            case CompositeAttributeData composite -> composite.getAttributes().stream()
                    .collect(HashMap::new, (map, sub) -> map.put(sub.getName(), attributeValue(sub)), HashMap::putAll);
        };
    }
}

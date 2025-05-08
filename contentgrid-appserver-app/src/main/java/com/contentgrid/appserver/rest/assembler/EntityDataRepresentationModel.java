package com.contentgrid.appserver.rest.assembler;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.query.EntityInstance;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

@EqualsAndHashCode(callSuper = true)
@Relation(collectionRelation = IanaLinkRelations.ITEM_VALUE)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EntityDataRepresentationModel extends RepresentationModel<EntityDataRepresentationModel> {

    private final Map<String, Object> data;

    @JsonAnyGetter
    public Map<String, Object> getAttributes() {
        return Map.copyOf(this.data);
    }

    public static EntityDataRepresentationModel from(Entity entity, EntityInstance inst) {
        Map<String, Object> data = new HashMap<>();
        for (Attribute attribute : entity.getAllAttributes()) {
            var value = inst.getAttributeByName(attribute.getName());
            value.ifPresent(v -> data.put(attribute.getName().getValue(), v));
        }
        return new EntityDataRepresentationModel(data);
    }
}

package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.query.EntityInstance;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DummyEntityInstance extends HashMap<String, Object> implements EntityInstance {
    private EntityName entityName;

    static EntityInstance fromMap(Map<String, ?> map, EntityName entityName) {
        var res = new DummyEntityInstance(map, entityName);
        if (map.get("id") == null) {
            res.put("id", UUID.randomUUID().toString());
        }
        return res;
    }

    static EntityData fromMap(Map<String, ?> map, Entity entity) {
        var builder =
                EntityData.builder()
                        .name(entity.getName());

        for (Attribute attr : entity.getAllAttributes()) {

            if (map.containsKey(attr.getName().getValue())) {
                if (attr instanceof SimpleAttribute) {
                    builder.attribute(SimpleAttributeData.builder()
                            .name(attr.getName())
                            .value(map.get(attr.getName().getValue()))
                            .build());
                }
            }
        }

        return builder.build();
    }

    private DummyEntityInstance(Map<String, ?> map, EntityName entityName) {
        super(map);
        this.entityName = entityName;
    }

    @Override
    public List<Object> getAttributes() {
        return List.copyOf(this.values());
    }

    @Override
    public Optional<Object> getAttributeByName(AttributeName name) {
        return Optional.ofNullable(this.get(name.getValue()));
    }

    @Override
    public EntityName getEntityName() {
        return this.entityName;
    }

    @Override
    public String getId() {
        return (String) this.get("id");
    }
}

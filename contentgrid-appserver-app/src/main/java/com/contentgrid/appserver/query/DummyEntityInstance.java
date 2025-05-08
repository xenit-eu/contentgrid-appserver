package com.contentgrid.appserver.query;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
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

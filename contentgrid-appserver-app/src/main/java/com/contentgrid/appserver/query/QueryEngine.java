package com.contentgrid.appserver.query;

import com.contentgrid.appserver.application.model.Entity;
import java.util.Map;
import java.util.Optional;

public interface QueryEngine {
    ItemCountPage<EntityInstance> query(Entity entity, Map<String, String> filters, PageRequest pageRequest);
    Optional<EntityInstance> findById(Entity entity, String id);
    EntityInstance createInstance(Entity entity, Map<String, Object> data);
}

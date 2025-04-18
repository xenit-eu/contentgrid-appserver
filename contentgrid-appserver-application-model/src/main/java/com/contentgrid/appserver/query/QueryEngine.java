package com.contentgrid.appserver.query;

import com.contentgrid.appserver.application.model.Entity;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface QueryEngine {
    List<EntityInstance> query(Entity entity, Map<String, String> filters);
    Optional<EntityInstance> findById(Entity entity, String id);
}

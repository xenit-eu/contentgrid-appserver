package com.contentgrid.appserver.query;

import com.contentgrid.appserver.application.model.Entity;
import java.util.List;
import java.util.Map;

public interface QueryEngine {
    List<EntityInstance> query(Entity entity, Map<String, String> filters);
}

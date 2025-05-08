package com.contentgrid.appserver.query;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import java.util.List;
import java.util.Optional;

public interface EntityInstance {
    EntityName getEntityName();
    String getId();
    List<Object> getAttributes();
    Optional<Object> getAttributeByName(AttributeName name);
}

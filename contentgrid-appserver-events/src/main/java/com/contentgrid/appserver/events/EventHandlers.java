package com.contentgrid.appserver.events;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.query.engine.api.data.EntityData;

public interface EventHandlers {
    void dispatchCreate(Application application, EntityName entity, EntityData data);
    void dispatchUpdate(Application application, EntityName entity, EntityData oldData, EntityData newData);
    void dispatchDelete(Application application, EntityName entity, EntityData oldData);
}

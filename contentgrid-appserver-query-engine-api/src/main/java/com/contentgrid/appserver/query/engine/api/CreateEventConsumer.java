package com.contentgrid.appserver.query.engine.api;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.query.engine.api.data.EntityData;

public interface CreateEventConsumer {
    void dispatchCreate(Application application, EntityData data);
}


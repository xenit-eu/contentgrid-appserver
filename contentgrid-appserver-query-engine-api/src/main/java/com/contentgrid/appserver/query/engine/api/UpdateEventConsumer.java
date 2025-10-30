package com.contentgrid.appserver.query.engine.api;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.query.engine.api.data.EntityData;

public interface UpdateEventConsumer {
    void dispatchUpdate(Application application, EntityData oldData, EntityData newData);
}


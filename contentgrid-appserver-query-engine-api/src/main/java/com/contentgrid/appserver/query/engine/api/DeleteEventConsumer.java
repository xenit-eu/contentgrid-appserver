package com.contentgrid.appserver.query.engine.api;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.query.engine.api.data.EntityData;

public interface DeleteEventConsumer {
    void onEntityDelete(Application application, EntityData data);
}


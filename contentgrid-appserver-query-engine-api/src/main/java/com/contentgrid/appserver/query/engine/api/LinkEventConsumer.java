package com.contentgrid.appserver.query.engine.api;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.query.engine.api.data.EntityData;

public interface LinkEventConsumer {
    void onLink(Application application, EntityData oldData, EntityData newData);
}

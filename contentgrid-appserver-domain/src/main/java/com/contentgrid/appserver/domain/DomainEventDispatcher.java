package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.domain.data.EntityInstance;

public interface DomainEventDispatcher {
    void dispatchCreate(Application application, EntityInstance instance);

    void dispatchUpdate(Application application, EntityInstance oldInstance,
            EntityInstance newInstance);

    void dispatchDelete(Application application, EntityInstance instance);
}

package com.contentgrid.appserver.query.engine.jooq.test;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.query.engine.api.CreateEventConsumer;
import com.contentgrid.appserver.query.engine.api.DeleteEventConsumer;
import com.contentgrid.appserver.query.engine.api.LinkEventConsumer;
import com.contentgrid.appserver.query.engine.api.UnlinkEventConsumer;
import com.contentgrid.appserver.query.engine.api.UpdateEventConsumer;
import com.contentgrid.appserver.query.engine.api.data.EntityData;

public class NoneEvents implements CreateEventConsumer, DeleteEventConsumer, LinkEventConsumer,
        UnlinkEventConsumer, UpdateEventConsumer {

    @Override
    public void onEntityCreate(Application application, EntityData data) {
    }

    @Override
    public void onEntityDelete(Application application, EntityData data) {

    }

    @Override
    public void onLink(Application application, EntityData oldData, EntityData newData) {

    }

    @Override
    public void onUnlink(Application application, EntityData oldData, EntityData newData) {

    }

    @Override
    public void onEntityUpdate(Application application, EntityData oldData, EntityData newData) {

    }
}

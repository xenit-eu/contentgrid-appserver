package com.contentgrid.appserver.query.engine.api;

import com.contentgrid.appserver.application.model.Application;

public interface IndexCreator {

    void createIndex(Application application);

    void dropIndex(Application application);

}

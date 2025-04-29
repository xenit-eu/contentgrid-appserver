package com.contentgrid.appserver.query.engine;

import com.contentgrid.appserver.application.model.Application;
import org.jooq.DSLContext;

public interface DSLContextResolver {

    DSLContext resolve(Application application);
}

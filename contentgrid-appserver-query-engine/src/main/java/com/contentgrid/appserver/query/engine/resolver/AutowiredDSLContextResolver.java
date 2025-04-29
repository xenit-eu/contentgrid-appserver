package com.contentgrid.appserver.query.engine.resolver;

import com.contentgrid.appserver.application.model.Application;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;

@RequiredArgsConstructor
public class AutowiredDSLContextResolver implements DSLContextResolver {

    private final DSLContext dslContext;

    @Override
    public DSLContext resolve(Application application) {
        return dslContext;
    }
}

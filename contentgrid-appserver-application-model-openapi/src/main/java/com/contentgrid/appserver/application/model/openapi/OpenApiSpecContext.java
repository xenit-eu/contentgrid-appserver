package com.contentgrid.appserver.application.model.openapi;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiSpec;

public record OpenApiSpecContext(
        Application application,
        OpenApiSpec spec
) {

}

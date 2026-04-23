package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiHttpHeaders.OpenApiHeaderDescription;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import java.util.Map.Entry;
import java.util.stream.Stream;

public interface ResponseHeaderResolver {
    Stream<Entry<String, OpenApiPotentialReference<OpenApiHeaderDescription>>> resolveResponseHeaders(HttpResponseType responseType, OpenApiSpecContext context);

}

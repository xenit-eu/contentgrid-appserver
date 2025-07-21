package com.contentgrid.appserver.rest.mapping;

import com.contentgrid.appserver.application.model.Application;
import java.util.stream.Stream;
import lombok.NonNull;

class ContentAttributeReplacementPathVariablesGenerator implements ReplacementPathVariablesGenerator {

    @Override
    public Stream<ReplacementPathVariableValues> generateForApplication(@NonNull Application application) {
        return application.getEntities().stream()
                .flatMap(entity -> entity.getContentAttributes().stream()
                        .map(contentAttribute -> new ReplacementPathVariableValues(entity.getPathSegment(),
                                contentAttribute.getPathSegment()))
                );
    }
}

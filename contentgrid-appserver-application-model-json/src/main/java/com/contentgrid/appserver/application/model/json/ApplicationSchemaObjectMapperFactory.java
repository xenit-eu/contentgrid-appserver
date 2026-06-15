package com.contentgrid.appserver.application.model.json;

import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class ApplicationSchemaObjectMapperFactory {
    public static ObjectMapper createObjectMapper() {
        return JsonMapper.builder()
                // Jackson 3 sorts keys alphabetically, but our yaml documents look nicer in declaration order
                // The alternative is putting @JsonPropertyOrder({...}) everywhere but that's kind of a pain
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();
    }
}

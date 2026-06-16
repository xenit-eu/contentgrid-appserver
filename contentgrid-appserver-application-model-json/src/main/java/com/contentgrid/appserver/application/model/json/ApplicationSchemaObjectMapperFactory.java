package com.contentgrid.appserver.application.model.json;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class ApplicationSchemaObjectMapperFactory {
    public static ObjectMapper createObjectMapper() {
        return JsonMapper.builder().build();
    }
}

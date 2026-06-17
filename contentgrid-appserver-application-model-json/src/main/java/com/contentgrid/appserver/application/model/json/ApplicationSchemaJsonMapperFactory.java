package com.contentgrid.appserver.application.model.json;

import tools.jackson.databind.json.JsonMapper;

public class ApplicationSchemaJsonMapperFactory {
    public static JsonMapper createJsonMapper() {
        return JsonMapper.builder().build();
    }
}

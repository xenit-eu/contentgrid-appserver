package com.contentgrid.appserver.json;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ApplicationSchemaObjectMapperFactory {
    public static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        return mapper;
    }
}

package com.contentgrid.appserver.json;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ApplicationSchemaObjectMapperFactory {
    public static ObjectMapper createObjectMapper() {
        return new ObjectMapper();
    }
}

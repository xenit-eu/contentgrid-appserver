package com.contentgrid.appserver.json;

import com.contentgrid.appserver.json.model.CompositeAttribute;
import com.contentgrid.appserver.json.model.ContentAttribute;
import com.contentgrid.appserver.json.model.ManyToManyRelation;
import com.contentgrid.appserver.json.model.OneToManyRelation;
import com.contentgrid.appserver.json.model.OneToOneRelation;
import com.contentgrid.appserver.json.model.SimpleAttribute;
import com.contentgrid.appserver.json.model.UserAttribute;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;

public class ApplicationSchemaObjectMapperFactory {
    public static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register subtypes for polymorphic deserialization
        mapper.registerSubtypes(
            new NamedType(SimpleAttribute.class, "simple"),
            new NamedType(CompositeAttribute.class, "composite"),
            new NamedType(ContentAttribute.class, "content"),
            new NamedType(UserAttribute.class, "user"),
            new NamedType(OneToOneRelation.class, "one-to-one"),
            new NamedType(OneToManyRelation.class, "one-to-many"),
            new NamedType(ManyToManyRelation.class, "many-to-many")
        );

        return mapper;
    }
}

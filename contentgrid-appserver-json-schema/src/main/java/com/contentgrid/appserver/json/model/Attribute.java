package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimpleAttribute.class, name = "simple"),
        @JsonSubTypes.Type(value = CompositeAttribute.class, name = "composite"),
        @JsonSubTypes.Type(value = ContentAttribute.class, name = "content"),
        @JsonSubTypes.Type(value = UserAttribute.class, name = "user")
})
public interface Attribute {
}

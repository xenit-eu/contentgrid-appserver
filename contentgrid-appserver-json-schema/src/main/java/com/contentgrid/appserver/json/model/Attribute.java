package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimpleAttribute.class, name = "simple"),
        @JsonSubTypes.Type(value = CompositeAttribute.class, name = "composite"),
        @JsonSubTypes.Type(value = ContentAttribute.class, name = "content"),
        @JsonSubTypes.Type(value = UserAttribute.class, name = "user")
})
public sealed abstract class Attribute permits SimpleAttribute, CompositeAttribute, ContentAttribute, UserAttribute {
    protected String name;
    protected String description;
    protected List<String> flags;
}

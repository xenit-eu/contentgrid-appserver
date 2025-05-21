package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OneToOneRelation.class, name = "one-to-one"),
        @JsonSubTypes.Type(value = OneToManyRelation.class, name = "one-to-many"),
        @JsonSubTypes.Type(value = ManyToManyRelation.class, name = "many-to-many")
})
@Getter
@Setter
public abstract class Relation {
    private RelationEndPoint sourceEndpoint;
    private RelationEndPoint targetEndpoint;
}

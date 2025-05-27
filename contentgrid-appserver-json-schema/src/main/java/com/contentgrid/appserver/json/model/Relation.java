package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OneToOneRelation.class, name = "one-to-one"),
        @JsonSubTypes.Type(value = OneToManyRelation.class, name = "one-to-many"),
        @JsonSubTypes.Type(value = ManyToManyRelation.class, name = "many-to-many")
})
@Getter
@Setter
public abstract sealed class Relation permits OneToOneRelation, OneToManyRelation, ManyToManyRelation {

    @NonNull
    private RelationEndPoint sourceEndpoint;

    @NonNull
    private RelationEndPoint targetEndpoint;
}

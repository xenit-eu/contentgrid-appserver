package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AllowedValuesConstraint.class, name = "allowedValues"),
        @JsonSubTypes.Type(value = UniqueConstraint.class, name = "unique"),
        @JsonSubTypes.Type(value = RequiredConstraint.class, name = "required")
})
public sealed interface AttributeConstraint permits AllowedValuesConstraint, UniqueConstraint, RequiredConstraint {
}

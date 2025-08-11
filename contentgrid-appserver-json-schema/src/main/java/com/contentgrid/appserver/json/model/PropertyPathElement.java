package com.contentgrid.appserver.json.model;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.PropertyName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.function.Function;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyPathElement {

    @NonNull
    private String name;

    @NonNull
    private PropertyPathElementType type;

    @RequiredArgsConstructor
    public enum PropertyPathElementType {
        @JsonProperty("attr")
        ATTRIBUTE(AttributeName::of),
        @JsonProperty("rel")
        RELATION(RelationName::of)
        ;

        private final Function<String, PropertyName> propertyConstructor;
    }

    public PropertyName toPropertyName() {
        return type.propertyConstructor.apply(name);
    }
}

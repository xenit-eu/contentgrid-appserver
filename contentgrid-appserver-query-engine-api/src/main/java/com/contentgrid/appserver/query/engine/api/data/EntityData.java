package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class EntityData {

    @NonNull
    EntityName name;

    @Singular
    @Getter(AccessLevel.NONE)
    Map<AttributeName, AttributeData> attributes;

    public List<AttributeData> getAttributes() {
        return List.copyOf(attributes.values());
    }

    public Optional<AttributeData> getAttributeByName(AttributeName name) {
        return Optional.ofNullable(attributes.get(name));
    }

}

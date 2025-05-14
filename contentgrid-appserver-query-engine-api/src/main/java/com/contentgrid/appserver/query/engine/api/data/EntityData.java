package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import java.util.HashMap;
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
public class EntityData {

    @NonNull
    EntityName name;

    EntityId id;

    @Getter(AccessLevel.NONE)
    Map<AttributeName, AttributeData> attributes = new HashMap<>();

    @Builder
    EntityData(@NonNull EntityName name, EntityId id, @Singular List<AttributeData> attributes) {
        this.name = name;
        this.id = id;
        for (var attribute : attributes) {
            var old = this.attributes.put(attribute.getName(), attribute);
            if (old != null) {
                throw new DuplicateElementException("Duplicate attribute with name '%s'".formatted(attribute.getName()));
            }
        }
    }

    public List<AttributeData> getAttributes() {
        return List.copyOf(attributes.values());
    }

    public Optional<AttributeData> getAttributeByName(AttributeName name) {
        return Optional.ofNullable(attributes.get(name));
    }

}

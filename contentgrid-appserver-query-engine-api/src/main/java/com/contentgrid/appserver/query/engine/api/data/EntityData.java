package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.version.Version;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    EntityIdentity identity;

    @Getter(AccessLevel.NONE)
    Map<AttributeName, AttributeData> attributes = new HashMap<>();

    @Builder
    EntityData(
            @NonNull EntityName name,
            @NonNull EntityId id,
            /* nullable */ Version version,
            @Singular List<AttributeData> attributes
    ) {
        this(
                EntityIdentity.forEntity(name, id)
                        .withVersion(Objects.requireNonNullElse(version, Version.unspecified())),
                attributes);
    }

    public EntityData(@NonNull EntityIdentity identity, Iterable<AttributeData> attributes) {
        this.identity = identity;
        for (var attribute : attributes) {
            var old = this.attributes.put(attribute.getName(), attribute);
            if (old != null) {
                throw new DuplicateElementException("Duplicate attribute with name '%s'".formatted(attribute.getName()));
            }
        }
    }

    public EntityName getName() {
        return identity.getEntityName();
    }

    public EntityId getId() {
        return identity.getEntityId();
    }

    public List<AttributeData> getAttributes() {
        return List.copyOf(attributes.values());
    }

    public Optional<AttributeData> getAttributeByName(AttributeName name) {
        return Optional.ofNullable(attributes.get(name));
    }

}
